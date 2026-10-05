package io.wulfcodes.messaging.chat.service.spec;

import io.wulfcodes.messaging.chat.model.dto.request.ClientFrame;
import io.wulfcodes.messaging.chat.model.dto.response.MessagePageResponse;
import io.wulfcodes.messaging.chat.model.dto.response.MessageResponse;

import java.util.function.Consumer;

public interface MessageService {

    /**
     * Stores a message, updates both inboxes, then fans it out.
     * Order is guaranteed: {@code onStored} (the ACK) runs once the message is durable and BEFORE
     * delivery starts, so the sender never sees a DELIVERED receipt for a message it was not yet ACKed.
     *
     * @param originSessionId the sender's WebSocket session (it receives the ACK, not a MESSAGE)
     */
    MessageResponse send(String senderId, ClientFrame frame, String originSessionId, Consumer<MessageResponse> onStored);

    /**
     * History newest-first, paging backwards with {@code before} (exclusive Snowflake id, null = now).
     */
    MessagePageResponse history(String userId, String conversationId, String before, int limit);
}
