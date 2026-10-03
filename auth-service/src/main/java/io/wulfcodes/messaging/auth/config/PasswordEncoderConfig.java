package io.wulfcodes.messaging.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordEncoderConfig {

    /**
     * Delegating encoder: hashes with BCrypt today and stores the algorithm as a prefix
     * ("{bcrypt}$2a$..."), so we can move to a stronger algorithm later without breaking old hashes.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
