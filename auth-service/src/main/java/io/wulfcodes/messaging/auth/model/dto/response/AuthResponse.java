package io.wulfcodes.messaging.auth.model.dto.response;

import io.wulfcodes.messaging.auth.model.vo.TokenType;

/**
 * @param expiresIn access token lifetime in seconds
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        TokenType tokenType,
        long expiresIn,
        UserResponse user
) {
}
