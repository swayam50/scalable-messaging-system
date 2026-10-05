package io.wulfcodes.messaging.common.model.dto.response;

/**
 * Public view of another user: no email or account details.
 */
public record UserSummaryResponse(
        String id,
        String username,
        String displayName
) {
}
