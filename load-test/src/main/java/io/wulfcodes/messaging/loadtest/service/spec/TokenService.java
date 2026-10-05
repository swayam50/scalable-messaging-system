package io.wulfcodes.messaging.loadtest.service.spec;

/** Mints access tokens for virtual users (RS256, same shape as auth-service's tokens). */
public interface TokenService {

    String tokenFor(String userId);
}
