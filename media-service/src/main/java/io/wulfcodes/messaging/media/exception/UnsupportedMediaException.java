package io.wulfcodes.messaging.media.exception;

import org.springframework.http.HttpStatus;

public class UnsupportedMediaException extends ApiException {

    public UnsupportedMediaException(String mimeType) {
        super(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported file type", "Files of type " + mimeType + " are not allowed");
    }
}
