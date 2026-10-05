package io.wulfcodes.messaging.media.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Bound from {@code media.*}.
 *
 * @param signingSecret HMAC secret shared with chat-service for signed attachment descriptors
 * @param chatApiUrls   chat-service nodes used for membership checks (any node works)
 */
@ConfigurationProperties(prefix = "media")
public record MediaProperties(
        Storage storage,
        Duration uploadUrlTtl,
        Duration downloadUrlTtl,
        String signingSecret,
        List<String> chatApiUrls,
        Auth auth,
        Cors cors
) {

    /**
     * @param internalEndpoint how THIS service reaches MinIO (e.g. http://minio:9000 inside compose)
     * @param publicEndpoint   how BROWSERS reach MinIO; presigned URLs are signed for this host,
     *                         because the host is part of the signature
     */
    public record Storage(String internalEndpoint, String publicEndpoint, String region,
                          String accessKey, String secretKey, String bucket) {
    }

    public record Auth(String jwksUri, String publicKey, String issuer) {
    }

    public record Cors(List<String> allowedOrigins) {
    }
}
