package io.wulfcodes.messaging.chat.config;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Creates the CQL session. The keyspace must exist before a session can be bound to it, so:
 * 1. open a bootstrap session WITHOUT a keyspace and run the (idempotent) schema script,
 * 2. then build the real session bound to the {@code chat} keyspace.
 * In production a migration tool would own the schema; here it keeps local setup to "docker compose up".
 */
@Slf4j
@Configuration
public class CassandraConfig {

    @Bean
    public CqlSession cassandraSession(CqlSessionBuilder builder, ChatProperties properties, ResourceLoader resources) {
        ChatProperties.Cassandra cassandra = properties.cassandra();
        if (cassandra.initSchema()) {
            try (CqlSession bootstrap = builder.build()) {
                String script = readScript(resources, cassandra.schemaLocation());
                statements(script).forEach(bootstrap::execute);
                log.info("Applied ScyllaDB schema from {}", cassandra.schemaLocation());
            }
        }
        return builder.withKeyspace(cassandra.keyspace()).build();
    }

    private static String readScript(ResourceLoader resources, String location) {
        try {
            return resources.getResource(location).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read schema " + location, e);
        }
    }

    /** Strips "--" comments and splits on ';'. */
    static java.util.List<String> statements(String script) {
        String withoutComments = script.lines()
                .map(line -> line.replaceAll("--.*$", ""))
                .reduce("", (a, b) -> a + "\n" + b);
        return Arrays.stream(withoutComments.split(";"))
                .map(String::trim)
                .filter(statement -> !statement.isEmpty())
                .toList();
    }
}
