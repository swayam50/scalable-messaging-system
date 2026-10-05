package io.wulfcodes.messaging.chat.config;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.servererrors.InvalidQueryException;
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
 * 1. open a bootstrap session WITHOUT a keyspace and run the versioned schema scripts in order
 *    (V1, V2, ...); every statement is idempotent: CREATE ... IF NOT EXISTS, and ALTER ... ADD
 *    statements whose column already exists are skipped,
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
                for (String location : cassandra.schemaLocations()) {
                    statements(readScript(resources, location)).forEach(statement -> execute(bootstrap, statement));
                    log.info("Applied ScyllaDB schema {}", location);
                }
            }
        }
        return builder.withKeyspace(cassandra.keyspace()).build();
    }

    private static void execute(CqlSession session, String statement) {
        try {
            session.execute(statement);
        } catch (InvalidQueryException e) {
            boolean alreadyApplied = statement.toUpperCase().startsWith("ALTER TABLE")
                    && e.getMessage().toLowerCase().contains("conflicts with an existing column");
            if (!alreadyApplied) {
                throw e;
            }
        }
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
