package io.wulfcodes.messaging.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Bound from {@code chat.*}.
 *
 * @param workerId Snowflake worker id of this node (0-1023); must be unique per running node
 */
@ConfigurationProperties(prefix = "chat")
public record ChatProperties(
        int workerId,
        int maxMessageLength,
        Cassandra cassandra,
        History history,
        Auth auth,
        Cors cors
) {

    public record Cassandra(String keyspace, boolean initSchema, String schemaLocation) {
    }

    public record History(int maxPageSize) {
    }

    /** Where to fetch auth-service's public keys, and the issuer its tokens must carry. */
    public record Auth(String jwksUri, String issuer) {
    }

    public record Cors(List<String> allowedOrigins) {
    }
}
