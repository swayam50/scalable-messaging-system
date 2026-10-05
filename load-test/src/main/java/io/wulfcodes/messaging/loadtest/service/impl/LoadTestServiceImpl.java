package io.wulfcodes.messaging.loadtest.service.impl;

import io.wulfcodes.messaging.common.util.UlidGenerator;
import io.wulfcodes.messaging.loadtest.config.LoadTestConfig;
import io.wulfcodes.messaging.loadtest.model.vo.StageResult;
import io.wulfcodes.messaging.loadtest.model.vo.VirtualUserSpec;
import io.wulfcodes.messaging.loadtest.service.spec.ChatApiClient;
import io.wulfcodes.messaging.loadtest.service.spec.LoadTestService;
import io.wulfcodes.messaging.loadtest.service.spec.TokenService;
import io.wulfcodes.messaging.loadtest.util.LatencyRecorder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Setup: users -> conversations (pairs) -> /connect -> one WebSocket per user on its owner node.
 * Then, per stage: send at a target total rate (open-loop, fixed schedule) and measure
 *   ACK latency      = send -> ACK on the sender's socket (message durably stored)
 *   delivery latency = send -> MESSAGE on the recipient's socket, split same-node / cross-node (gRPC).
 * Sender and recipient live in this JVM, so both timestamps come from the same monotonic clock.
 */
public class LoadTestServiceImpl implements LoadTestService {

    private static final String BODY = "load-test message 0123456789 abcdefghijklmnopqrstuvwxyz";
    private static final long TICK_MS = 5;

    private final LoadTestConfig config;
    private final TokenService tokens;
    private final ChatApiClient chatApi;
    private final HttpClient http;
    private final JsonMapper json = JsonMapper.builder().build();

    private final List<VirtualUserConnection> connections = new ArrayList<>();
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();
    private volatile Stage stage;

    /** A sent message waiting for its ACK and its delivery. */
    private record Pending(long sentNanos, boolean crossNode, boolean measured, Stage stage, AtomicInteger seen) {
    }

    /** Counters and recorders of the stage currently running. */
    private static final class Stage {
        final LatencyRecorder ack;
        final LatencyRecorder sameNode;
        final LatencyRecorder crossNode;
        final AtomicLong sent = new AtomicLong();
        final AtomicLong acked = new AtomicLong();
        final AtomicLong delivered = new AtomicLong();
        final AtomicLong errors = new AtomicLong();
        volatile boolean measuring;

        Stage(int capacity) {
            ack = new LatencyRecorder(capacity);
            sameNode = new LatencyRecorder(capacity);
            crossNode = new LatencyRecorder(capacity);
        }
    }

    public LoadTestServiceImpl(LoadTestConfig config, TokenService tokens, ChatApiClient chatApi, HttpClient http) {
        this.config = config;
        this.tokens = tokens;
        this.chatApi = chatApi;
        this.http = http;
    }

    @Override
    public List<StageResult> run() throws Exception {
        setUp();
        List<StageResult> results = new ArrayList<>();
        for (int rate : config.stageRates()) {
            StageResult result = runStage(rate);
            results.add(result);
            System.out.printf("  stage %5d msg/s -> sent %.0f/s, acked %.0f/s, ack p99 %.1f ms, cross-node delivery p99 %.1f ms%n",
                    rate, result.achievedSendRate(), result.ackRate(), result.ack().p99(), result.deliveryCrossNode().p99());
        }
        connections.forEach(VirtualUserConnection::close);
        return results;
    }

    // ------------------------------------------------------------------ setup

    private void setUp() throws Exception {
        int n = config.users() - (config.users() % 2);
        UlidGenerator ids = new UlidGenerator();
        List<String> userIds = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            userIds.add(ids.nextString());
        }
        List<String> userTokens = userIds.stream().map(tokens::tokenFor).toList();

        System.out.printf("Setting up %d users / %d conversations ...%n", n, n / 2);
        List<String> conversations = parallel(n / 2, i -> chatApi.createConversation(userTokens.get(2 * i), userIds.get(2 * i + 1)));
        List<String[]> owners = parallel(n, i -> chatApi.connect(userTokens.get(i)));

        List<VirtualUserSpec> specs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int peer = i % 2 == 0 ? i + 1 : i - 1;
            specs.add(new VirtualUserSpec(userIds.get(i), userIds.get(peer), conversations.get(i / 2), owners.get(i)[0], owners.get(i)[1]));
        }
        List<VirtualUserConnection> opened = parallel(n, i ->
                VirtualUserConnection.open(http, specs.get(i), userTokens.get(i), config.origin(), this::onFrame).join());
        connections.addAll(opened);

        long crossPairs = 0;
        Map<String, Integer> perNode = new ConcurrentHashMap<>();
        for (int i = 0; i < n; i++) {
            perNode.merge(specs.get(i).nodeId(), 1, Integer::sum);
            if (i % 2 == 0 && !specs.get(i).nodeId().equals(specs.get(i + 1).nodeId())) {
                crossPairs++;
            }
        }
        System.out.printf("Connected %d WebSockets %s; %d of %d conversations span two nodes%n",
                connections.size(), perNode, crossPairs, n / 2);
    }

    /** Runs {@code count} setup calls concurrently (virtual threads, bounded concurrency), keeping order. */
    private <T> List<T> parallel(int count, IndexedTask<T> task) throws Exception {
        Semaphore limit = new Semaphore(64);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int index = i;
                Callable<T> call = () -> {
                    limit.acquire();
                    try {
                        return task.run(index);
                    } finally {
                        limit.release();
                    }
                };
                futures.add(executor.submit(call));
            }
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        }
    }

    @FunctionalInterface
    private interface IndexedTask<T> {
        T run(int index) throws Exception;
    }

    // ------------------------------------------------------------------ one stage

    private StageResult runStage(int rate) throws InterruptedException {
        long warmupNanos = config.warmup().toNanos();
        long measureNanos = config.measure().toNanos();
        Stage current = new Stage((int) (rate * config.measure().toSeconds() * 1.5) + 10_000);
        stage = current;
        pending.clear();

        AtomicLong scheduled = new AtomicLong();
        AtomicInteger cursor = new AtomicInteger();
        long start = System.nanoTime();
        ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor();
        // Open-loop schedule: how many messages SHOULD have been sent by now, regardless of how
        // fast the server answers. A slow server therefore shows up as latency, not as a lower rate.
        ticker.scheduleAtFixedRate(() -> {
            long elapsed = System.nanoTime() - start;
            current.measuring = elapsed >= warmupNanos && elapsed < warmupNanos + measureNanos;
            long due = elapsed * rate / 1_000_000_000L;
            while (scheduled.get() < due) {
                scheduled.incrementAndGet();
                sendOne(connections.get(Math.floorMod(cursor.getAndIncrement(), connections.size())), current);
            }
        }, 0, TICK_MS, TimeUnit.MILLISECONDS);

        TimeUnit.NANOSECONDS.sleep(warmupNanos + measureNanos);
        ticker.shutdownNow();
        current.measuring = false;
        TimeUnit.SECONDS.sleep(3);   // drain: let in-flight ACKs/deliveries arrive

        long timeouts = pending.values().stream().filter(p -> p.measured() && p.stage() == current).count();
        double seconds = config.measure().toNanos() / 1e9;
        return new StageResult(rate,
                current.sent.get() / seconds,
                current.acked.get() / seconds,
                current.delivered.get() / seconds,
                current.errors.get(),
                timeouts,
                current.ack.percentiles(),
                current.sameNode.percentiles(),
                current.crossNode.percentiles());
    }

    private void sendOne(VirtualUserConnection sender, Stage current) {
        VirtualUserSpec spec = sender.spec();
        String clientMessageId = spec.userId().substring(16) + "-" + System.nanoTime();
        boolean measured = current.measuring;
        boolean crossNode = !spec.nodeId().equals(peerNode(spec));
        pending.put(clientMessageId, new Pending(System.nanoTime(), crossNode, measured, current, new AtomicInteger()));
        if (measured) {
            current.sent.incrementAndGet();
        }
        sender.send("{\"type\":\"SEND\",\"conversationId\":\"" + spec.conversationId()
                + "\",\"clientMessageId\":\"" + clientMessageId + "\",\"body\":\"" + BODY + "\"}",
                () -> current.errors.incrementAndGet());
    }

    private final Map<String, String> nodeByUser = new ConcurrentHashMap<>();

    private String peerNode(VirtualUserSpec spec) {
        if (nodeByUser.isEmpty()) {
            connections.forEach(c -> nodeByUser.put(c.spec().userId(), c.spec().nodeId()));
        }
        return nodeByUser.get(spec.peerId());
    }

    // ------------------------------------------------------------------ incoming frames

    private void onFrame(String frame) {
        long now = System.nanoTime();
        Stage current = stage;
        JsonNode node = json.readTree(frame);
        String type = node.path("type").asString();
        switch (type) {
            case "ACK" -> {
                Pending p = pending.get(node.path("clientMessageId").asString());
                if (p != null && p.measured() && p.stage() == current) {
                    current.ack.record(now - p.sentNanos());
                    current.acked.incrementAndGet();
                }
                complete(node.path("clientMessageId").asString(), p);
            }
            case "MESSAGE" -> {
                String clientMessageId = node.path("message").path("clientMessageId").asString();
                Pending p = pending.get(clientMessageId);
                if (p != null && p.measured() && p.stage() == current) {
                    (p.crossNode() ? current.crossNode : current.sameNode).record(now - p.sentNanos());
                    current.delivered.incrementAndGet();
                }
                complete(clientMessageId, p);
            }
            case "ERROR" -> {
                if (current != null) {
                    current.errors.incrementAndGet();
                }
            }
            default -> {
                // RECEIPT / PRESENCE / TYPING are not part of this measurement
            }
        }
    }

    /** Forget a message once both its ACK and its delivery have been seen. */
    private void complete(String clientMessageId, Pending p) {
        if (p != null && p.seen().incrementAndGet() >= 2) {
            pending.remove(clientMessageId);
        }
    }
}
