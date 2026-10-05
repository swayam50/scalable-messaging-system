package io.wulfcodes.messaging.chat.repository;

import io.wulfcodes.messaging.chat.model.po.Conversation;
import org.springframework.data.cassandra.repository.CassandraRepository;

public interface ConversationRepository extends CassandraRepository<Conversation, String> {
}
