package io.wulfcodes.messaging.media.config;

import io.wulfcodes.messaging.common.util.UlidGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IdGeneratorConfig {

    /** Attachment ids: ULIDs are unguessable, which also keeps object keys unguessable. */
    @Bean
    public UlidGenerator ulidGenerator() {
        return new UlidGenerator();
    }
}
