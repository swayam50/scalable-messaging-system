package io.wulfcodes.messaging.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Bound from {@code auth.jwt.*}. Keys are PEM strings (PKCS#8 private, X.509 public);
 * when blank, {@link JwtConfig} generates an in-memory key pair for local development.
 */
@ConfigurationProperties(prefix = "auth.jwt")
public record JwtProperties(
        String issuer,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        String keyId,
        String privateKey,
        String publicKey
) {
}
