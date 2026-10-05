package io.wulfcodes.messaging.media.exception;

import org.springframework.http.HttpStatus;

public class UploadNotCompleteException extends ApiException {

    public UploadNotCompleteException(String detail) {
        super(HttpStatus.CONFLICT, "Upload not complete", detail);
    }
}
