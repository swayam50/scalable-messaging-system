-- V4: idempotent SEND.
-- A client that gets no ACK (lost frame, dropped socket) retries with the same client_message_id.
-- The first SEND claims (sender_id, client_message_id) with INSERT ... IF NOT EXISTS; a retry loses
-- the claim, reads back the original message_id and gets the same message instead of a duplicate.
-- Rows only need to outlive the client's retry window, so they expire after a day.
CREATE TABLE IF NOT EXISTS chat.sent_messages (
  sender_id         text,
  client_message_id text,
  conversation_id   text,
  message_id        bigint,
  PRIMARY KEY ((sender_id, client_message_id))
) WITH default_time_to_live = 86400;
