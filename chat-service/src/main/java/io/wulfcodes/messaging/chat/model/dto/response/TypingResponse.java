package io.wulfcodes.messaging.chat.model.dto.response;

public record TypingResponse(String conversationId, String userId, boolean typing) {
}
