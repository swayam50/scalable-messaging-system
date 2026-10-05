package io.wulfcodes.messaging.chat.service.impl;

import io.wulfcodes.messaging.chat.model.dto.response.ServerFrame;
import io.wulfcodes.messaging.chat.model.vo.FrameType;
import io.wulfcodes.messaging.chat.model.vo.NodeInfo;
import io.wulfcodes.messaging.chat.service.spec.DeliveryService;
import io.wulfcodes.messaging.chat.service.spec.LocalDeliveryService;
import io.wulfcodes.messaging.chat.service.spec.NodeForwarder;
import io.wulfcodes.messaging.chat.service.spec.RingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Cluster-aware delivery:
 * <pre>
 *   owner = ring.ownerOf(userId)
 *   owner == this node  -> write to local WebSockets
 *   owner == other node -> forward over gRPC; the owner writes to ITS local WebSockets
 * </pre>
 * Clients connect to the node that owns them (see /api/v1/connect), so the owner is where
 * the user's sockets live. A forward that fails loses nothing: the message is already stored
 * and the client picks it up from history on reconnect.
 */
@Service
@RequiredArgsConstructor
public class RoutingDeliveryServiceImpl implements DeliveryService {

    private final RingService ringService;
    private final LocalDeliveryService localDeliveryService;
    private final NodeForwarder nodeForwarder;

    @Override
    public void deliver(String userId, ServerFrame frame, String excludeSessionId) {
        NodeInfo owner = ringService.ownerOf(userId);
        boolean local = owner.nodeId().equals(ringService.localNode().nodeId());

        if (local || frame.type() != FrameType.MESSAGE) {
            localDeliveryService.deliverLocally(userId, frame, excludeSessionId);
        } else {
            nodeForwarder.forward(owner, userId, frame.message(), excludeSessionId);
        }
    }
}
