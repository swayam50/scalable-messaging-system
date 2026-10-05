package io.wulfcodes.messaging.media.exception;

import org.springframework.http.HttpStatus;

public class StorageUnavailableException extends ApiException {

    public StorageUnavailableException(String detail) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "Storage unavailable", detail);
    }
}
