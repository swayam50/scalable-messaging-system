package io.wulfcodes.messaging.chat.exception;

import org.springframework.http.HttpStatus;

public class InvalidMessageException extends ApiException {

    public InvalidMessageException(String detail) {
        super(HttpStatus.BAD_REQUEST, "Invalid message", detail);
    }
}
