package io.wulfcodes.messaging.ui.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * @param authServiceUrl base URL of auth-service as seen from THIS server (server-to-server)
 * @param chatApiUrls    base URLs of the chat nodes as seen from the BROWSER. Any node can serve the
 *                       REST API (and /api/v1/connect); the client fails over to the next one if a node
 *                       is down. In production a load balancer would usually sit in front instead.
 * @param mediaApiUrl    base URL of media-service as seen from the BROWSER (uploads/downloads)
 */
@ConfigurationProperties(prefix = "ui")
public record UiProperties(
        String authServiceUrl,
        List<String> chatApiUrls,
        String mediaApiUrl
) {
}
