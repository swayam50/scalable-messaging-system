package io.wulfcodes.messaging.chat.model.dto.response;

import io.wulfcodes.messaging.chat.model.vo.ReceiptStatus;

/**
 * @param userId    the participant who received / read
 * @param messageId DELIVERED: that message; READ: everything up to and including it
 */
public record ReceiptResponse(
        String conversationId,
        String userId,
        String messageId,
        ReceiptStatus status
) {
}
