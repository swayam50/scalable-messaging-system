-- ScyllaDB schema for chat-service (applied manually for now; automated in a later milestone).

CREATE KEYSPACE IF NOT EXISTS chat
  WITH replication = {'class': 'NetworkTopologyStrategy', 'replication_factor': 1};

-- All messages of a conversation, newest first.
-- bucket (epoch day from the Snowflake timestamp) keeps partitions bounded for long-lived chats.
CREATE TABLE IF NOT EXISTS chat.messages_by_conversation (
  conversation_id text,      -- ULID
  bucket          int,       -- days since Unix epoch
  message_id      bigint,    -- Snowflake id (time-ordered)
  sender_id       text,      -- user ULID
  body            text,
  PRIMARY KEY ((conversation_id, bucket), message_id)
) WITH CLUSTERING ORDER BY (message_id DESC);

-- A user's inbox, most recently active conversation first.
-- last_message_id is a clustering column, so it cannot be updated in place:
-- on every new message the old row is deleted and a new one inserted (in one logged batch).
CREATE TABLE IF NOT EXISTS chat.conversations_by_user (
  user_id          text,
  last_message_id  bigint,
  conversation_id  text,
  peer_id          text,
  preview          text,
  PRIMARY KEY (user_id, last_message_id, conversation_id)
) WITH CLUSTERING ORDER BY (last_message_id DESC, conversation_id ASC);

-- Live chat-service nodes for the consistent-hash ring.
-- Rows are written with USING TTL 15 every 5s, so a dead node disappears on its own.
CREATE TABLE IF NOT EXISTS chat.cluster_members (
  cluster      text,
  node_id      text,
  grpc_address text,
  PRIMARY KEY (cluster, node_id)
);
