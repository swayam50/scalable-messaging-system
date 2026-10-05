package io.wulfcodes.messaging.chat.model.dto.response;

import io.wulfcodes.messaging.common.model.vo.ContentType;

import java.time.Instant;

/**
 * @param messageId  Snowflake id as a STRING: it is 64-bit, and JavaScript numbers lose
 *                   precision above 2^53, so a numeric id would be silently corrupted in the browser
 * @param body       text, or the optional caption of a media message
 * @param attachment null for TEXT; clients fetch the file via media-service with attachmentId
 */
public record MessageResponse(
        String messageId,
        String conversationId,
        String senderId,
        String body,
        Instant sentAt,
        String clientMessageId,
        ContentType contentType,
        AttachmentResponse attachment
) {
}
