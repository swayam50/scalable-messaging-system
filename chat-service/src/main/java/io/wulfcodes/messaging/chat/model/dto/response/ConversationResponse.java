package io.wulfcodes.messaging.chat.model.dto.response;

import java.time.Instant;

public record ConversationResponse(
        String conversationId,
        String peerId,
        Instant createdAt
) {
}
