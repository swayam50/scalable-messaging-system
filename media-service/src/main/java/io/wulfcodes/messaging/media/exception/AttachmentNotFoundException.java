package io.wulfcodes.messaging.media.exception;

import org.springframework.http.HttpStatus;

/** Also used when the caller may not see the attachment (404 rather than 403 reveals nothing). */
public class AttachmentNotFoundException extends ApiException {

    public AttachmentNotFoundException(String id) {
        super(HttpStatus.NOT_FOUND, "Attachment not found", "No attachment " + id);
    }
}
