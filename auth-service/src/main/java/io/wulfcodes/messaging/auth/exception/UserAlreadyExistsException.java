package io.wulfcodes.messaging.auth.exception;

import org.springframework.http.HttpStatus;

public class UserAlreadyExistsException extends ApiException {

    public UserAlreadyExistsException(String field) {
        super(HttpStatus.CONFLICT, "User already exists", "An account with this " + field + " already exists");
    }
}
