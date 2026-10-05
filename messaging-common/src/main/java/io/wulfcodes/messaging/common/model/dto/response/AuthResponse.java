package io.wulfcodes.messaging.common.model.dto.response;

import io.wulfcodes.messaging.common.model.vo.TokenType;

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
