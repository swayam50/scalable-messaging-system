package io.wulfcodes.messaging.chat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Verifies access tokens issued by auth-service.
 * Only auth-service holds the private key; this node downloads the PUBLIC keys from its JWKS
 * endpoint (lazily, then cached) and checks signature, expiry and issuer locally, so no call
 * to auth-service is needed per request.
 */
@Configuration
public class JwtConfig {

    @Bean
    public JwtDecoder jwtDecoder(ChatProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.auth().jwksUri()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(properties.auth().issuer()));
        return decoder;
    }
}
