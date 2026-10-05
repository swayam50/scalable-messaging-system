package io.wulfcodes.messaging.loadtest.service.impl;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import io.wulfcodes.messaging.loadtest.service.spec.TokenService;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;

public class JwtTokenService implements TokenService {

    private final NimbusJwtEncoder encoder;
    private final String issuer;

    public JwtTokenService(RSAPublicKey publicKey, RSAPrivateKey privateKey, String issuer) {
        RSAKey key = new RSAKey.Builder(publicKey).privateKey(privateKey).keyID("load-test").build();
        this.encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
        this.issuer = issuer;
    }

    @Override
    public String tokenFor(String userId) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .subject(userId)
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofHours(1)))   // outlives the whole run
                .claim("username", "lt-" + userId.substring(18))
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).build(), claims))
                .getTokenValue();
    }
}
