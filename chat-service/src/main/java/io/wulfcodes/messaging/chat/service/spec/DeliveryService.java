package io.wulfcodes.messaging.chat.service.spec;

import io.wulfcodes.messaging.chat.model.dto.response.ServerFrame;

/**
 * Delivers frames to a user's connected sessions.
 * Single node: local sessions only. Milestone 4 adds a routing implementation that looks up the
 * user's owner node on the consistent-hash ring and forwards over gRPC when it is another node.
 */
public interface DeliveryService {

    /**
     * @param excludeSessionId session that should not receive the frame (the sender's own tab); may be null
     */
    void deliver(String userId, ServerFrame frame, String excludeSessionId);
}
