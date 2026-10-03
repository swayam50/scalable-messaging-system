package io.wulfcodes.messaging.auth.service.spec;

import io.wulfcodes.messaging.auth.model.po.RefreshToken;
import io.wulfcodes.messaging.auth.model.po.User;
import io.wulfcodes.messaging.auth.model.vo.IssuedRefreshToken;

public interface TokenService {

    /** Short-lived signed JWT (RS256) carrying the user id and username. */
    String issueAccessToken(User user);

    /** Long-lived opaque token; only its hash is stored. */
    IssuedRefreshToken issueRefreshToken(User user);

    /**
     * Validates a raw refresh token and revokes it (rotation: every token is single-use).
     * Presenting an already-revoked token revokes all of the user's tokens (reuse detection).
     */
    RefreshToken consumeRefreshToken(String rawToken);

    /** Revokes the token if it exists; idempotent. */
    void revokeRefreshToken(String rawToken);

    long accessTokenTtlSeconds();
}
