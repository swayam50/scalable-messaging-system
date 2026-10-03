package io.wulfcodes.messaging.auth.exception;

import org.springframework.http.HttpStatus;

public class InvalidRefreshTokenException extends ApiException {

    public InvalidRefreshTokenException() {
        super(HttpStatus.UNAUTHORIZED, "Invalid refresh token", "Refresh token is invalid, expired or revoked");
    }
}
