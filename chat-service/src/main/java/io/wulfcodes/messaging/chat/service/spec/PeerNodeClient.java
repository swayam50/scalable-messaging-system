package io.wulfcodes.messaging.chat.service.spec;

import io.wulfcodes.messaging.chat.model.dto.response.ServerFrame;
import io.wulfcodes.messaging.chat.model.vo.NodeInfo;

import java.util.Collection;
import java.util.Set;
import java.util.function.IntConsumer;

/**
 * gRPC client for talking to other chat nodes.
 */
public interface PeerNodeClient {

    /**
     * Asynchronously pushes a frame to {@code userId}'s sessions on {@code target}. Never throws.
     *
     * @param onDelivered called with the number of sessions reached (0 if the call failed)
     */
    void forward(NodeInfo target, String userId, ServerFrame frame, String excludeSessionId, IntConsumer onDelivered);

    /** Which of {@code userIds} (owned by {@code target}) are connected there. Empty set on failure. */
    Set<String> onlineAmong(NodeInfo target, Collection<String> userIds);
}
