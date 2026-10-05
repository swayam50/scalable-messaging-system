package io.wulfcodes.messaging.ui.exception;

import lombok.Getter;
import org.springframework.http.HttpStatusCode;

/**
 * auth-service answered with an error; {@code getMessage()} is its problem "detail", safe to show to users.
 */
@Getter
public class AuthClientException extends RuntimeException {

    private final HttpStatusCode status;

    public AuthClientException(HttpStatusCode status, String detail) {
        super(detail);
        this.status = status;
    }
}
