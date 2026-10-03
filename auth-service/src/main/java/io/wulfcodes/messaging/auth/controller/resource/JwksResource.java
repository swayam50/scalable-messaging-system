package io.wulfcodes.messaging.auth.controller.resource;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Publishes the PUBLIC signing key (JSON Web Key Set) so other services can verify our JWTs.
 * Standard well-known path, therefore not versioned.
 */
@RestController
public class JwksResource {

    private final Map<String, Object> publicJwks;

    public JwksResource(RSAKey rsaKey) {
        // toPublicJWKSet() strips the private parts: only n/e (modulus/exponent) are exposed
        this.publicJwks = new JWKSet(rsaKey).toPublicJWKSet().toJSONObject();
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return publicJwks;
    }
}
