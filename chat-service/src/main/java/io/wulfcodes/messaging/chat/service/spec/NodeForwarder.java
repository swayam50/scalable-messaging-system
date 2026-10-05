package io.wulfcodes.messaging.chat.service.spec;

import io.wulfcodes.messaging.chat.model.dto.response.MessageResponse;
import io.wulfcodes.messaging.chat.model.vo.NodeInfo;

/**
 * Sends a message to another chat node (gRPC) for delivery to its local sessions.
 */
public interface NodeForwarder {

    void forward(NodeInfo target, String userId, MessageResponse message, String excludeSessionId);
}
