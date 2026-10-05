package io.wulfcodes.messaging.chat.service.impl;

import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import io.wulfcodes.messaging.chat.grpc.proto.DeliverRequest;
import io.wulfcodes.messaging.chat.grpc.proto.DeliverResponse;
import io.wulfcodes.messaging.chat.grpc.proto.NodeDeliveryGrpc;
import io.wulfcodes.messaging.chat.grpc.proto.PresenceQuery;
import io.wulfcodes.messaging.chat.mapper.GrpcMessageMapper;
import io.wulfcodes.messaging.chat.model.dto.response.ServerFrame;
import io.wulfcodes.messaging.chat.model.vo.NodeInfo;
import io.wulfcodes.messaging.chat.model.vo.RingChangedEvent;
import io.wulfcodes.messaging.chat.service.spec.PeerNodeClient;
import io.wulfcodes.messaging.chat.service.spec.RingService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

/**
 * gRPC client to peer chat nodes.
 * <ul>
 *   <li>One long-lived HTTP/2 channel per peer, reused for all calls (channels multiplex many RPCs).</li>
 *   <li>Async stub for event delivery: the WebSocket thread is not blocked waiting for the peer.</li>
 *   <li>Deadlines on every call, so a hung peer cannot pile up requests.</li>
 *   <li>Plaintext (Spring gRPC default) inside the cluster network, authenticated by the cluster token;
 *       mutual TLS via spring.grpc.client.channel.*.ssl would be the production upgrade.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GrpcPeerNodeClient implements PeerNodeClient {

    private static final long DELIVER_DEADLINE_MS = 2_000;
    private static final long PRESENCE_DEADLINE_MS = 1_000;

    private final GrpcChannelFactory channelFactory;
    private final GrpcMessageMapper grpcMessageMapper;
    private final RingService ringService;
    private final Map<String, ManagedChannel> channels = new ConcurrentHashMap<>();

    /**
     * Never throws: a failed forward must not fail the sender's request (messages are already
     * stored, so the recipient still gets them from history on reconnect).
     */
    @Override
    public void forward(NodeInfo target, String userId, ServerFrame frame, String excludeSessionId, IntConsumer onDelivered) {
        try {
            DeliverRequest request = grpcMessageMapper.toRequest(userId, frame, excludeSessionId);
            NodeDeliveryGrpc.newStub(channel(target))
                    .withDeadlineAfter(DELIVER_DEADLINE_MS, TimeUnit.MILLISECONDS)
                    .deliver(request, new StreamObserver<>() {
                        @Override
                        public void onNext(DeliverResponse response) {
                            onDelivered.accept(response.getDeliveredSessions());
                        }

                        @Override
                        public void onError(Throwable t) {
                            log.warn("Forward of {} to {} failed: {}", frame.type(), target.nodeId(), t.getMessage());
                            onDelivered.accept(0);
                        }

                        @Override
                        public void onCompleted() {
                        }
                    });
        } catch (RuntimeException e) {
            log.warn("Could not forward {} to {}: {}", frame.type(), target.nodeId(), e.getMessage());
            onDelivered.accept(0);
        }
    }

    @Override
    public Set<String> onlineAmong(NodeInfo target, Collection<String> userIds) {
        try {
            return Set.copyOf(NodeDeliveryGrpc.newBlockingStub(channel(target))
                    .withDeadlineAfter(PRESENCE_DEADLINE_MS, TimeUnit.MILLISECONDS)
                    .getPresence(PresenceQuery.newBuilder().addAllUserIds(userIds).build())
                    .getOnlineUserIdsList());
        } catch (RuntimeException e) {
            log.warn("Presence query to {} failed: {}", target.nodeId(), e.getMessage());
            return Set.of();
        }
    }

    /** Spring gRPC builds plaintext channels unless spring.grpc.client.channel.*.ssl is enabled. */
    private ManagedChannel channel(NodeInfo target) {
        return channels.computeIfAbsent(target.grpcAddress(), channelFactory::createChannel);
    }

    /** Closes channels to nodes that are no longer in the ring. */
    @EventListener
    public void onRingChanged(RingChangedEvent event) {
        Set<String> liveAddresses = ringService.nodes().stream()
                .map(NodeInfo::grpcAddress)
                .collect(Collectors.toSet());
        channels.entrySet().removeIf(entry -> {
            if (liveAddresses.contains(entry.getKey())) {
                return false;
            }
            entry.getValue().shutdown();
            return true;
        });
    }

    @PreDestroy
    void shutdown() {
        channels.values().forEach(ManagedChannel::shutdown);
    }
}
