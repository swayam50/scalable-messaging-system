package io.wulfcodes.messaging.auth.config;

import io.wulfcodes.messaging.common.util.UlidGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class IdGeneratorConfig {

    /** ULIDs for public ids (users, refresh tokens), backed by SecureRandom. */
    @Bean
    public UlidGenerator ulidGenerator() {
        return new UlidGenerator();
    }
}
