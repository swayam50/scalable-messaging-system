package io.wulfcodes.messaging.media.exception;

import org.springframework.http.HttpStatus;

public class MediaTooLargeException extends ApiException {

    public MediaTooLargeException(long maxBytes) {
        super(HttpStatus.PAYLOAD_TOO_LARGE, "File too large", "Maximum size for this type is " + (maxBytes / (1024 * 1024)) + " MB");
    }
}
