package io.wulfcodes.messaging.ui.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param authServiceUrl base URL of auth-service as seen from THIS server (server-to-server)
 * @param chatApiUrl     base URL of chat-service as seen from the BROWSER
 * @param chatWsUrl      WebSocket URL of chat-service as seen from the BROWSER
 */
@ConfigurationProperties(prefix = "ui")
public record UiProperties(
        String authServiceUrl,
        String chatApiUrl,
        String chatWsUrl
) {
}
