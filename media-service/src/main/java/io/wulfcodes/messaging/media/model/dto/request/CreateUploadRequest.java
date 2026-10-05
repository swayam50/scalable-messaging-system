package io.wulfcodes.messaging.media.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * @param size declared size in bytes; the storage policy enforces the real limit on upload
 */
public record CreateUploadRequest(
        @NotBlank String conversationId,
        @NotBlank @Size(max = 255) String fileName,
        @NotBlank @Size(max = 127) String mimeType,
        @Positive long size
) {
}
