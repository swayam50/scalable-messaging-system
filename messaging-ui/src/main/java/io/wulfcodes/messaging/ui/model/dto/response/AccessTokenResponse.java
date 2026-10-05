package io.wulfcodes.messaging.ui.model.dto.response;

/**
 * Short-lived access token handed to the page's JavaScript for chat-service calls.
 *
 * @param expiresIn seconds until expiry
 */
public record AccessTokenResponse(String accessToken, long expiresIn) {
}
