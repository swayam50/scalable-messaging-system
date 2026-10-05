package io.wulfcodes.messaging.media.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.StringUtils;

import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/** Verifies auth-service access tokens with its public key (JWKS endpoint or configured PEM). */
@Configuration
public class JwtConfig {

    @Bean
    public JwtDecoder jwtDecoder(MediaProperties properties) throws Exception {
        MediaProperties.Auth auth = properties.auth();
        NimbusJwtDecoder decoder = StringUtils.hasText(auth.publicKey())
                ? NimbusJwtDecoder.withPublicKey(parsePublicKey(auth.publicKey())).build()
                : NimbusJwtDecoder.withJwkSetUri(auth.jwksUri()).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(auth.issuer()));
        return decoder;
    }

    private static RSAPublicKey parsePublicKey(String pem) throws Exception {
        String base64 = pem.replaceAll("-----(BEGIN|END) [A-Z ]+-----", "").replaceAll("\\s", "");
        return (RSAPublicKey) KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));
    }
}
