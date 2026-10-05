-- ScyllaDB schema for chat-service. Idempotent (IF NOT EXISTS); executed at startup
-- when chat.cassandra.init-schema=true. Tables are designed query-first: one table per access pattern.

CREATE KEYSPACE IF NOT EXISTS chat
  WITH replication = {'class': 'NetworkTopologyStrategy', 'replication_factor': 1};

-- Conversation metadata: who takes part and when it started (lower bound for history paging).
CREATE TABLE IF NOT EXISTS chat.conversations (
  conversation_id text PRIMARY KEY,     -- ULID
  participant_ids set<text>,
  created_at      timestamp
);

-- Finds the 1:1 conversation of a user pair (user_low < user_high).
-- Written with INSERT ... IF NOT EXISTS (lightweight transaction / Paxos) so two users
-- starting a chat at the same moment still end up with exactly one conversation.
CREATE TABLE IF NOT EXISTS chat.direct_conversations (
  user_low        text,
  user_high       text,
  conversation_id text,
  PRIMARY KEY ((user_low, user_high))
);

-- All messages of a conversation, newest first.
-- bucket (days since epoch, from the Snowflake timestamp) bounds partition size for long-lived chats.
CREATE TABLE IF NOT EXISTS chat.messages_by_conversation (
  conversation_id   text,
  bucket            int,
  message_id        bigint,             -- Snowflake id (time-ordered)
  sender_id         text,
  body              text,
  client_message_id text,               -- client-generated id echoed back in the ACK
  PRIMARY KEY ((conversation_id, bucket), message_id)
) WITH CLUSTERING ORDER BY (message_id DESC);

-- A user's inbox: one row per conversation, sorted by the app.
-- Rows are written USING TIMESTAMP = message time, so an older message arriving late
-- can never overwrite a newer "last message" (last-write-wins by message time, not arrival).
CREATE TABLE IF NOT EXISTS chat.conversations_by_user (
  user_id          text,
  conversation_id  text,
  peer_id          text,
  last_message_id  bigint,
  last_sender_id   text,
  preview          text,
  PRIMARY KEY (user_id, conversation_id)
);

-- Live chat-service nodes for the consistent-hash ring (milestone 4).
-- Rows are written with USING TTL 15 every 5s, so a dead node disappears on its own.
CREATE TABLE IF NOT EXISTS chat.cluster_members (
  cluster      text,
  node_id      text,
  grpc_address text,
  PRIMARY KEY (cluster, node_id)
);
