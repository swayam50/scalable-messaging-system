package io.wulfcodes.messaging.chat.exception;

import org.springframework.http.HttpStatus;

public class InvalidConversationException extends ApiException {

    public InvalidConversationException(String detail) {
        super(HttpStatus.BAD_REQUEST, "Invalid conversation", detail);
    }
}
