package io.wulfcodes.messaging.auth.exception;

import org.springframework.http.HttpStatus;

public class UserNotFoundException extends ApiException {

    public UserNotFoundException(String id) {
        super(HttpStatus.NOT_FOUND, "User not found", "No user with id " + id);
    }
}
