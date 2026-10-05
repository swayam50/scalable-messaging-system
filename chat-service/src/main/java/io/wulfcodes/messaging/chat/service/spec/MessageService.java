package io.wulfcodes.messaging.chat.service.spec;

import io.wulfcodes.messaging.chat.model.dto.request.ClientFrame;
import io.wulfcodes.messaging.chat.model.dto.response.MessagePageResponse;
import io.wulfcodes.messaging.chat.model.dto.response.MessageResponse;

public interface MessageService {

    /**
     * Stores a message, updates both inboxes and hands it to delivery.
     *
     * @param originSessionId the sender's WebSocket session (it receives an ACK, not a MESSAGE)
     */
    MessageResponse send(String senderId, ClientFrame frame, String originSessionId);

    /**
     * History newest-first, paging backwards with {@code before} (exclusive Snowflake id, null = now).
     */
    MessagePageResponse history(String userId, String conversationId, String before, int limit);
}
