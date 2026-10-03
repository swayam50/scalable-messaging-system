package io.wulfcodes.messaging.auth.model.dto.response;

/**
 * Public view of another user: no email or account details.
 */
public record UserSummaryResponse(
        String id,
        String username,
        String displayName
) {
}
