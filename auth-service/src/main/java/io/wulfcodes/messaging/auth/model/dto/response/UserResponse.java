package io.wulfcodes.messaging.auth.model.dto.response;

import io.wulfcodes.messaging.auth.model.vo.UserStatus;

import java.time.Instant;

/**
 * Full profile of the authenticated user (includes email, so only returned for "me").
 */
public record UserResponse(
        String id,
        String username,
        String email,
        String displayName,
        UserStatus status,
        Instant createdAt
) {
}
