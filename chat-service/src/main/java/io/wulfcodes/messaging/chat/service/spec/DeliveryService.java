package io.wulfcodes.messaging.chat.service.spec;

import io.wulfcodes.messaging.chat.model.dto.response.ServerFrame;

import java.util.function.IntConsumer;

/**
 * Delivers frames to a user's connected sessions, wherever in the cluster they are:
 * looks up the user's owner node on the consistent-hash ring and either writes to local
 * sessions or forwards to the owner over gRPC.
 */
public interface DeliveryService {

    /**
     * @param excludeSessionId session that should not receive the frame (the sender's own tab); may be null
     */
    default void deliver(String userId, ServerFrame frame, String excludeSessionId) {
        deliver(userId, frame, excludeSessionId, delivered -> { });
    }

    /**
     * @param onDelivered called (possibly asynchronously) with how many of the user's sessions received it
     */
    void deliver(String userId, ServerFrame frame, String excludeSessionId, IntConsumer onDelivered);
}
