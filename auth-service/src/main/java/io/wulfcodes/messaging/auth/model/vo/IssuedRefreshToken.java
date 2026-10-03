package io.wulfcodes.messaging.auth.model.vo;

import java.time.Instant;

/**
 * Immutable result of issuing a refresh token.
 * The raw value is returned to the client exactly once; only its hash is stored.
 */
public record IssuedRefreshToken(String rawValue, Instant expiresAt) {
}
