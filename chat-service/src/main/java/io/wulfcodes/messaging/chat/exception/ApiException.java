package io.wulfcodes.messaging.chat.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base class for business errors, rendered as RFC 9457 problem responses
 * (REST) or ERROR frames (WebSocket).
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
