-- V3: media messages (image / video / audio / file).

-- User-defined type: the attachment is embedded in the message row (no table of its own).
CREATE TYPE IF NOT EXISTS chat.attachment (
  attachment_id text,
  file_name     text,
  mime_type     text,
  size          bigint
);

ALTER TABLE chat.messages_by_conversation ADD content_type text;              -- TEXT/IMAGE/VIDEO/AUDIO/FILE (null = TEXT)
ALTER TABLE chat.messages_by_conversation ADD attachment frozen<attachment>;
