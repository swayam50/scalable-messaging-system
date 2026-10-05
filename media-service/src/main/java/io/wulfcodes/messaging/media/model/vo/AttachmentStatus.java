package io.wulfcodes.messaging.media.model.vo;

/**
 * PENDING: upload ticket issued, object may not exist yet.
 * READY: object verified in storage (size + type); can be sent and downloaded.
 */
public enum AttachmentStatus {
    PENDING,
    READY
}
