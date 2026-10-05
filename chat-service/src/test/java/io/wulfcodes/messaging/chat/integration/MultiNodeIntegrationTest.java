package io.wulfcodes.messaging.chat.integration;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import io.wulfcodes.messaging.chat.ChatServiceApplication;
import io.wulfcodes.messaging.chat.service.spec.RingService;
import io.wulfcodes.messaging.common.util.UlidGenerator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.web.client.RestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.scylladb.ScyllaDBContainer;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Two real chat-service nodes (two Spring contexts in this JVM) sharing one ScyllaDB:
 * membership via TTL rows, consistent-hash ownership, and gRPC forwarding between nodes.
 * Tokens are real RS256 JWTs signed with a test key; nodes verify them with the public key.
 */
@Testcontainers
class MultiNodeIntegrationTest {

    private static final String ISSUER = "test-issuer";

    @Container
    static final ScyllaDBContainer SCYLLA = new ScyllaDBContainer("scylladb/scylla:2026.3.2")
            .withCommand("--smp 1 --memory 1G --overprovisioned 1 --developer-mode 1");

    private static NimbusJwtEncoder jwtEncoder;
    private static Node nodeA;
    private static Node nodeB;

    record Node(String id, int httpPort, ConfigurableApplicationContext context) {
        RingService ring() {
            return context.getBean(RingService.class);
        }

        RestClient rest() {
            return RestClient.builder().baseUrl("http://localhost:" + httpPort).build();
        }
    }

    @BeforeAll
    static void startCluster() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keys = generator.generateKeyPair();
        RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) keys.getPublic()).privateKey(keys.getPrivate()).keyID("test").build();
        jwtEncoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(rsaKey)));
        String publicKeyPem = "-----BEGIN PUBLIC KEY-----\n"
                + Base64.getEncoder().encodeToString(keys.getPublic().getEncoded())
                + "\n-----END PUBLIC KEY-----";

        String cluster = "test-" + System.nanoTime();
        nodeA = start("node-a", 1, cluster, publicKeyPem);
        nodeB = start("node-b", 2, cluster, publicKeyPem);

        // both nodes must see each other through the membership table
        await(() -> nodeA.ring().nodes().size() == 2 && nodeB.ring().nodes().size() == 2);
    }

    @AfterAll
    static void stopCluster() {
        if (nodeA != null) nodeA.context().close();
        if (nodeB != null && nodeB.context().isActive()) nodeB.context().close();
    }

    @Test
    void messageCrossesNodesOverGrpcAndRingRebalancesWhenANodeLeaves() throws Exception {
        String alice = userOwnedBy("node-a");
        String bob = userOwnedBy("node-b");

        // both nodes agree on ownership, and /connect points each user at its owner
        assertThat(nodeB.ring().ownerOf(alice).nodeId()).isEqualTo("node-a");
        String bobConnect = nodeA.rest().get().uri("/api/v1/connect")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(bob)).retrieve().body(String.class);
        assertThat((String) JsonPath.read(bobConnect, "$.nodeId")).isEqualTo("node-b");

        // a user connecting to a node that does not own it is refused
        assertThatThrownBy(() -> connect(nodeA, bob, new LinkedBlockingQueue<>()))
                .isInstanceOf(CompletionException.class);

        String conversationId = JsonPath.read(nodeA.rest().post().uri("/api/v1/conversations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(alice))
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"peerId\":\"" + bob + "\"}")
                .retrieve().body(String.class), "$.conversationId");

        BlockingQueue<String> aliceFrames = new LinkedBlockingQueue<>();
        BlockingQueue<String> bobFrames = new LinkedBlockingQueue<>();
        WebSocket aliceSocket = connect(nodeA, alice, aliceFrames);
        WebSocket bobSocket = connect(nodeB, bob, bobFrames);

        // alice (node A) -> bob (node B): stored by A, forwarded over gRPC to B, pushed to bob
        aliceSocket.sendText(send(conversationId, "a-1", "hello across nodes"), true);
        assertThat(type(aliceFrames.poll(10, TimeUnit.SECONDS))).isEqualTo("ACK");
        String atBob = bobFrames.poll(10, TimeUnit.SECONDS);
        assertThat(type(atBob)).isEqualTo("MESSAGE");
        assertThat((String) JsonPath.read(atBob, "$.message.body")).isEqualTo("hello across nodes");

        // and back: bob (node B) -> alice (node A)
        bobSocket.sendText(send(conversationId, "b-1", "got it"), true);
        assertThat(type(bobFrames.poll(10, TimeUnit.SECONDS))).isEqualTo("ACK");
        String atAlice = aliceFrames.poll(10, TimeUnit.SECONDS);
        assertThat((String) JsonPath.read(atAlice, "$.message.body")).isEqualTo("got it");

        // node B leaves cleanly: it deletes its membership row, node A's ring shrinks,
        // and bob is now owned by node A
        nodeB.context().close();
        await(() -> nodeA.ring().nodes().size() == 1);
        assertThat(nodeA.ring().ownerOf(bob).nodeId()).isEqualTo("node-a");
    }

    // ------------------------------------------------------------------ helpers

    private static Node start(String nodeId, int workerId, String cluster, String publicKeyPem) throws Exception {
        int httpPort = freePort();
        int grpcPort = freePort();
        Map<String, Object> properties = new HashMap<>();
        properties.put("server.port", httpPort);
        properties.put("spring.grpc.server.port", grpcPort);
        properties.put("spring.cassandra.contact-points",
                SCYLLA.getContactPoint().getHostString() + ":" + SCYLLA.getContactPoint().getPort());
        properties.put("spring.cassandra.local-datacenter", "datacenter1");
        properties.put("spring.cassandra.request.timeout", "15s");
        properties.put("chat.worker-id", workerId);
        properties.put("chat.node.id", nodeId);
        properties.put("chat.node.grpc-address", "localhost:" + grpcPort);
        properties.put("chat.node.ws-url", "ws://localhost:" + httpPort + "/ws/chat");
        properties.put("chat.cluster.name", cluster);
        properties.put("chat.cluster.heartbeat-interval", "500ms");
        properties.put("chat.cluster.refresh-interval", "300ms");
        properties.put("chat.auth.public-key", publicKeyPem);
        properties.put("chat.auth.issuer", ISSUER);

        // Passed as command-line args: builder.properties() would only set *defaults*,
        // which application.yml overrides.
        String[] args = properties.entrySet().stream()
                .map(e -> "--" + e.getKey() + "=" + e.getValue())
                .toArray(String[]::new);
        ConfigurableApplicationContext context = new SpringApplicationBuilder(ChatServiceApplication.class).run(args);
        return new Node(nodeId, httpPort, context);
    }

    private static String userOwnedBy(String nodeId) {
        UlidGenerator ids = new UlidGenerator();
        while (true) {
            String candidate = ids.nextString();
            if (nodeA.ring().ownerOf(candidate).nodeId().equals(nodeId)) {
                return candidate;
            }
        }
    }

    private static String token(String userId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(ISSUER).subject(userId)
                .issuedAt(now).expiresAt(now.plusSeconds(300)).claim("username", userId).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims))
                .getTokenValue();
    }

    private static WebSocket connect(Node node, String userId, BlockingQueue<String> frames) {
        return HttpClient.newHttpClient().newWebSocketBuilder()
                .header("Origin", "http://localhost:8080")
                .buildAsync(URI.create("ws://localhost:" + node.httpPort() + "/ws/chat?access_token=" + token(userId)),
                        new WebSocket.Listener() {
                            @Override
                            public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                                frames.add(data.toString());
                                ws.request(1);
                                return null;
                            }
                        })
                .join();
    }

    private static String send(String conversationId, String clientMessageId, String body) {
        return """
                {"type":"SEND","conversationId":"%s","clientMessageId":"%s","body":"%s"}"""
                .formatted(conversationId, clientMessageId, body);
    }

    private static String type(String frame) {
        assertThat(frame).as("expected a frame but timed out").isNotNull();
        return JsonPath.read(frame, "$.type");
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("condition not met within 20s");
            }
            Thread.sleep(100);
        }
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
