-- V2: read receipts + presence.

-- Read pointers stored on each inbox row:
--   last_read_message_id      = how far THIS user has read (drives unread badges)
--   peer_last_read_message_id = how far the OTHER user has read (drives "seen" ticks)
-- Written USING TIMESTAMP = the message's time, so a pointer can only move forward even if
-- READ events arrive out of order.
ALTER TABLE chat.conversations_by_user ADD last_read_message_id bigint;
ALTER TABLE chat.conversations_by_user ADD peer_last_read_message_id bigint;

-- When a user was last connected (written when their last session closes).
CREATE TABLE IF NOT EXISTS chat.user_presence (
  user_id   text PRIMARY KEY,
  last_seen timestamp
);
