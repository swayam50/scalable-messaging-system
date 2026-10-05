package io.wulfcodes.messaging.chat.service.impl;

import io.wulfcodes.messaging.chat.model.dto.response.ServerFrame;
import io.wulfcodes.messaging.chat.model.vo.FrameType;
import io.wulfcodes.messaging.chat.model.vo.NodeInfo;
import io.wulfcodes.messaging.chat.service.spec.DeliveryService;
import io.wulfcodes.messaging.chat.service.spec.LocalDeliveryService;
import io.wulfcodes.messaging.chat.service.spec.PeerNodeClient;
import io.wulfcodes.messaging.chat.service.spec.RingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.function.IntConsumer;

/**
 * Cluster-aware delivery of any event (message, receipt, typing, presence):
 * <pre>
 *   owner = ring.ownerOf(userId)
 *   owner == this node  -> write to local WebSockets
 *   owner == other node -> forward over gRPC; the owner writes to ITS local WebSockets
 * </pre>
 * ACK/ERROR are replies to the requesting socket and are always local.
 */
@Service
@RequiredArgsConstructor
public class RoutingDeliveryServiceImpl implements DeliveryService {

    private final RingService ringService;
    private final LocalDeliveryService localDeliveryService;
    private final PeerNodeClient peerNodeClient;

    @Override
    public void deliver(String userId, ServerFrame frame, String excludeSessionId, IntConsumer onDelivered) {
        NodeInfo owner = ringService.ownerOf(userId);
        boolean local = owner.nodeId().equals(ringService.localNode().nodeId());

        if (local || frame.type() == FrameType.ACK || frame.type() == FrameType.ERROR) {
            onDelivered.accept(localDeliveryService.deliverLocally(userId, frame, excludeSessionId));
        } else {
            peerNodeClient.forward(owner, userId, frame, excludeSessionId, onDelivered);
        }
    }
}
