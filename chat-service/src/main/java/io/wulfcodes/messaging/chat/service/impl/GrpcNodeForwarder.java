package io.wulfcodes.messaging.chat.service.impl;

import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import io.wulfcodes.messaging.chat.grpc.proto.DeliverRequest;
import io.wulfcodes.messaging.chat.grpc.proto.DeliverResponse;
import io.wulfcodes.messaging.chat.grpc.proto.NodeDeliveryGrpc;
import io.wulfcodes.messaging.chat.mapper.GrpcMessageMapper;
import io.wulfcodes.messaging.chat.model.dto.response.MessageResponse;
import io.wulfcodes.messaging.chat.model.vo.NodeInfo;
import io.wulfcodes.messaging.chat.model.vo.RingChangedEvent;
import io.wulfcodes.messaging.chat.service.spec.NodeForwarder;
import io.wulfcodes.messaging.chat.service.spec.RingService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Forwards messages to peer nodes over gRPC.
 * <ul>
 *   <li>One long-lived HTTP/2 channel per peer, reused for all calls (channels multiplex many RPCs).</li>
 *   <li>Async stub: the WebSocket thread is not blocked waiting for the peer.</li>
 *   <li>A deadline per call so a hung peer cannot pile up requests.</li>
 *   <li>Plaintext (Spring gRPC default): traffic stays inside the cluster network; mTLS via
 *       spring.grpc.client.channel.*.ssl would be the production upgrade.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GrpcNodeForwarder implements NodeForwarder {

    private static final long DEADLINE_MS = 2_000;

    private final GrpcChannelFactory channelFactory;
    private final GrpcMessageMapper grpcMessageMapper;
    private final RingService ringService;
    private final Map<String, ManagedChannel> channels = new ConcurrentHashMap<>();

    /**
     * Never throws: a failed forward must not fail the sender's request (the message is
     * already stored, so the recipient still gets it from history).
     */
    @Override
    public void forward(NodeInfo target, String userId, MessageResponse message, String excludeSessionId) {
        try {
            send(target, userId, message, excludeSessionId);
        } catch (RuntimeException e) {
            log.warn("Could not forward message {} to {}: {}", message.messageId(), target.nodeId(), e.getMessage());
        }
    }

    private void send(NodeInfo target, String userId, MessageResponse message, String excludeSessionId) {
        DeliverRequest request = DeliverRequest.newBuilder()
                .setUserId(userId)
                .setMessage(grpcMessageMapper.toProto(message))
                .setExcludeSessionId(GrpcMessageMapper.nullToEmpty(excludeSessionId))
                .build();

        NodeDeliveryGrpc.newStub(channel(target))
                .withDeadlineAfter(DEADLINE_MS, TimeUnit.MILLISECONDS)
                .deliver(request, new StreamObserver<>() {
                    @Override
                    public void onNext(DeliverResponse response) {
                        log.debug("Forwarded message {} to {} ({} sessions)",
                                message.messageId(), target.nodeId(), response.getDeliveredSessions());
                    }

                    @Override
                    public void onError(Throwable t) {
                        // The message is already persisted: the recipient gets it from history on reconnect.
                        log.warn("Forward of message {} to {} failed: {}", message.messageId(), target.nodeId(), t.getMessage());
                    }

                    @Override
                    public void onCompleted() {
                    }
                });
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
