package io.wulfcodes.messaging.media.model.po;

import io.wulfcodes.messaging.common.model.vo.ContentType;
import io.wulfcodes.messaging.media.model.po.eo.AuditInfo;
import io.wulfcodes.messaging.media.model.vo.AttachmentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Metadata of an uploaded file. The bytes live in MinIO under {@code objectKey}.
 */
@Entity
@Table(name = "media_attachments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Attachment {

    @Id
    @Column(length = 26)
    private String id;

    @Column(name = "uploader_id", nullable = false, length = 26)
    private String uploaderId;

    @Column(name = "conversation_id", nullable = false, length = 26)
    private String conversationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false, length = 16)
    private ContentType contentType;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "mime_type", nullable = false, length = 127)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "object_key", nullable = false, length = 400)
    private String objectKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AttachmentStatus status;

    @Embedded
    @Builder.Default
    private AuditInfo audit = new AuditInfo();

    public boolean isReady() {
        return status == AttachmentStatus.READY;
    }
}
