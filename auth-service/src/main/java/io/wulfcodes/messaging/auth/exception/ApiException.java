package io.wulfcodes.messaging.auth.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base class for business errors. Each subclass decides its HTTP status, and
 * {@link GlobalExceptionHandler} turns it into an RFC 9457 problem response.
 */
@Getter
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String title;

    protected ApiException(HttpStatus status, String title, String detail) {
        super(detail);
        this.status = status;
        this.title = title;
    }
}
