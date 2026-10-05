package io.wulfcodes.messaging.ui.model.vo;

import io.wulfcodes.messaging.common.model.dto.response.UserResponse;

import java.io.Serializable;
import java.time.Instant;

/**
 * Immutable token state kept in the server-side HttpSession (never sent to the browser as a whole:
 * the refresh token stays here). Replaced as a whole on every refresh.
 */
public record SessionTokens(
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        UserResponse user
) implements Serializable {

    public boolean expiresWithin(java.time.Duration margin, Instant now) {
        return accessTokenExpiresAt.minus(margin).isBefore(now);
    }
}
