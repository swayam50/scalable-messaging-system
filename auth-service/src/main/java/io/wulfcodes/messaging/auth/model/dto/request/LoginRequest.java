package io.wulfcodes.messaging.auth.model.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * @param login username or email
 */
public record LoginRequest(
        @NotBlank String login,
        @NotBlank String password
) {
}
