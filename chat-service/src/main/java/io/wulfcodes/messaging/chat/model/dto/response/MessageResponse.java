package io.wulfcodes.messaging.chat.model.dto.response;

import java.time.Instant;

/**
 * @param messageId Snowflake id as a STRING: it is 64-bit, and JavaScript numbers lose
 *                  precision above 2^53, so a numeric id would be silently corrupted in the browser
 */
public record MessageResponse(
        String messageId,
        String conversationId,
        String senderId,
        String body,
        Instant sentAt,
        String clientMessageId
) {
}
