package io.wulfcodes.messaging.chat.model.dto.response;

import java.time.Instant;

/**
 * One conversation in the user's inbox. Message fields are null until the first message.
 */
public record InboxEntryResponse(
        String conversationId,
        String peerId,
        String lastMessageId,
        String lastSenderId,
        String preview,
        Instant lastMessageAt
) {
}
