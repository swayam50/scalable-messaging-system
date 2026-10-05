package io.wulfcodes.messaging.media.service.spec;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/** Object storage operations (MinIO / any S3-compatible store). */
public interface StorageService {

    /** Form fields for a browser POST upload, constrained to this key, content type and max size. */
    Map<String, String> presignUpload(String objectKey, String mimeType, long maxBytes, Instant expiresAt);

    String uploadUrl();

    /** Size and content type of a stored object, or empty if it does not exist. */
    Optional<StoredObject> stat(String objectKey);

    /** Presigned GET; {@code asDownload} forces "save as" (Content-Disposition: attachment). */
    String presignDownload(String objectKey, String fileName, String mimeType, boolean asDownload, int expirySeconds);

    record StoredObject(long size, String contentType) {
    }
}
