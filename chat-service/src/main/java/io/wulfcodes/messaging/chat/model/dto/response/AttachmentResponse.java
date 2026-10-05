package io.wulfcodes.messaging.chat.model.dto.response;

/** Attachment metadata shown in the chat (the file itself is fetched from media-service). */
public record AttachmentResponse(String attachmentId, String fileName, String mimeType, long size) {
}
