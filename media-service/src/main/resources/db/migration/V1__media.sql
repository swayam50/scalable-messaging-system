-- Attachment metadata (file bytes live in MinIO). Own Flyway history table: flyway_media_history.
CREATE TABLE media_attachments (
    id              VARCHAR(26)  PRIMARY KEY,       -- ULID
    uploader_id     VARCHAR(26)  NOT NULL,
    conversation_id VARCHAR(26)  NOT NULL,
    content_type    VARCHAR(16)  NOT NULL,          -- IMAGE / VIDEO / AUDIO / FILE
    file_name       VARCHAR(255) NOT NULL,
    mime_type       VARCHAR(127) NOT NULL,
    size_bytes      BIGINT       NOT NULL,
    object_key      VARCHAR(400) NOT NULL,
    status          VARCHAR(16)  NOT NULL,          -- PENDING / READY
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL
);

CREATE INDEX ix_media_attachments_conversation ON media_attachments (conversation_id);
