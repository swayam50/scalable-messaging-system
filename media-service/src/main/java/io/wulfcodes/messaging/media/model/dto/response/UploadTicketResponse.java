package io.wulfcodes.messaging.media.model.dto.response;

import io.wulfcodes.messaging.common.model.vo.ContentType;

import java.time.Instant;
import java.util.Map;

/**
 * Everything the browser needs to upload straight to object storage: a multipart POST to
 * {@code uploadUrl} with all {@code formFields} plus the file as the last field ("file").
 * The signed policy inside the form fields makes storage reject a different key, a different
 * Content-Type or a body larger than {@code maxBytes}.
 */
public record UploadTicketResponse(
        String attachmentId,
        ContentType contentType,
        String uploadUrl,
        Map<String, String> formFields,
        long maxBytes,
        Instant expiresAt
) {
}
