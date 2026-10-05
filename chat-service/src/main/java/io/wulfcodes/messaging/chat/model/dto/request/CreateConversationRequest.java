package io.wulfcodes.messaging.chat.model.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * @param peerId user id (ULID) of the other participant
 */
public record CreateConversationRequest(@NotBlank String peerId) {
}
