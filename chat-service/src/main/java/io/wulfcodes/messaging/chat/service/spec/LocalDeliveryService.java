package io.wulfcodes.messaging.chat.service.spec;

import io.wulfcodes.messaging.chat.model.dto.response.ServerFrame;

/**
 * Pushes a frame to the user's WebSocket sessions connected to THIS node.
 */
public interface LocalDeliveryService {

    /** @return number of sessions the frame was written to */
    int deliverLocally(String userId, ServerFrame frame, String excludeSessionId);
}
