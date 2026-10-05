package io.wulfcodes.messaging.chat.config;

import io.wulfcodes.messaging.common.util.IdGenerator;
import io.wulfcodes.messaging.common.util.SnowflakeIdGenerator;
import io.wulfcodes.messaging.common.util.UlidGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class IdGeneratorConfig {

    /** Message ids. The worker id must differ per node, otherwise two nodes could mint the same id. */
    @Bean
    public IdGenerator messageIdGenerator(ChatProperties properties, Clock clock) {
        return new SnowflakeIdGenerator(properties.workerId(), clock);
    }

    /** Public conversation ids. */
    @Bean
    public UlidGenerator ulidGenerator() {
        return new UlidGenerator();
    }
}
