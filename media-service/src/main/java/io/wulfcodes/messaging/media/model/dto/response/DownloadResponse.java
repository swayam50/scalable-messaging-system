package io.wulfcodes.messaging.media.model.dto.response;

import io.wulfcodes.messaging.common.model.vo.ContentType;

import java.time.Instant;

/** Short-lived presigned URL to fetch an attachment directly from storage. */
public record DownloadResponse(
        String attachmentId,
        ContentType contentType,
        String fileName,
        String mimeType,
        long size,
        String url,
        Instant expiresAt
) {
}
