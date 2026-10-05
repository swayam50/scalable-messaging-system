package io.wulfcodes.messaging.chat.service.spec;

import io.wulfcodes.messaging.chat.model.dto.response.MessageResponse;

public interface ReceiptService {

    /** Tells the sender that {@code message} reached at least one of the recipient's live sessions. */
    void notifyDelivered(MessageResponse message, String recipientId);

    /**
     * Records that {@code userId} has read the conversation up to {@code messageId}, and tells the
     * other participant (ticks) and the reader's other tabs (clear the unread badge).
     */
    void markRead(String userId, String conversationId, String messageId, String originSessionId);
}
