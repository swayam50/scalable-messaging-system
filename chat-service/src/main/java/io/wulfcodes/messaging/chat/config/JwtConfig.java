package io.wulfcodes.messaging.chat.config;

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

/**
 * Verifies access tokens issued by auth-service.
 * Only auth-service holds the private key; this node checks signature, expiry and issuer
 * locally with the PUBLIC key, so no call to auth-service is needed per request.
 * Key source: a configured PEM public key if present, otherwise auth-service's JWKS endpoint
 * (fetched lazily, then cached).
 */
@Configuration
public class JwtConfig {

    @Bean
    public JwtDecoder jwtDecoder(ChatProperties properties) throws Exception {
        ChatProperties.Auth auth = properties.auth();
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
