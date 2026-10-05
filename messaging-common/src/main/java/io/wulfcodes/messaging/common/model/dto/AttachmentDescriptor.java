package io.wulfcodes.messaging.common.model.dto;

import io.wulfcodes.messaging.common.model.vo.ContentType;

/**
 * An uploaded, verified attachment as issued by media-service, signed so chat-service can trust it
 * without calling media-service: any change to a field invalidates {@code signature}.
 *
 * @param signature HMAC-SHA256 over all other fields (see AttachmentSigner)
 */
public record AttachmentDescriptor(
        String attachmentId,
        String conversationId,
        String uploaderId,
        ContentType contentType,
        String fileName,
        String mimeType,
        long size,
        String signature
) {
}
