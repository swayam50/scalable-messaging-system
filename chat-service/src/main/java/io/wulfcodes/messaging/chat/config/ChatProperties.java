package io.wulfcodes.messaging.chat.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
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
        Node node,
        Cluster cluster,
        Cassandra cassandra,
        History history,
        Media media,
        Auth auth,
        Cors cors
) {

    /**
     * Identity of this node.
     *
     * @param grpcAddress host:port at which OTHER nodes reach this node's gRPC server
     * @param wsUrl       WebSocket URL at which CLIENTS reach this node
     */
    public record Node(String id, String grpcAddress, String wsUrl) {
    }

    /**
     * @param heartbeatInterval how often this node refreshes its membership row
     * @param memberTtl         row TTL: a node missing this many seconds of heartbeats is considered dead
     * @param refreshInterval   how often the ring is rebuilt from the membership table
     * @param virtualNodes      points per node on the hash ring (more = more even spread)
     * @param secret            shared secret nodes present to each other on gRPC calls
     */
    public record Cluster(String name, Duration heartbeatInterval, Duration memberTtl,
                          Duration refreshInterval, int virtualNodes, String secret) {
    }

    public record Cassandra(String keyspace, boolean initSchema, List<String> schemaLocations) {
    }

    public record History(int maxPageSize) {
    }

    /** @param signingSecret HMAC secret shared with media-service (attachment descriptors) */
    public record Media(String signingSecret) {
    }

    /**
     * Either {@code jwksUri} (fetch auth-service's keys) or {@code publicKey} (PEM, e.g. for offline/tests).
     */
    public record Auth(String jwksUri, String publicKey, String issuer) {
    }

    public record Cors(List<String> allowedOrigins) {
    }
}
