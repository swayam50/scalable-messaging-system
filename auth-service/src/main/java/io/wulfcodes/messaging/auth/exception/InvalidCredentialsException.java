package io.wulfcodes.messaging.auth.exception;

import org.springframework.http.HttpStatus;

/**
 * Same message for "unknown user" and "wrong password", so attackers cannot
 * discover which usernames exist (user enumeration).
 */
public class InvalidCredentialsException extends ApiException {

    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "Invalid credentials", "Username/email or password is incorrect");
    }
}
