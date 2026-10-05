package io.wulfcodes.messaging.chat.repository;

import io.wulfcodes.messaging.chat.model.po.Message;
import io.wulfcodes.messaging.chat.model.po.eo.MessageKey;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;

import java.util.List;

public interface MessageRepository extends CassandraRepository<Message, MessageKey> {

    /**
     * One partition (conversation + day), newest first, strictly older than {@code before}.
     * Efficient: a single-partition range scan on the clustering key.
     */
    @Query("SELECT * FROM messages_by_conversation WHERE conversation_id = ?0 AND bucket = ?1 AND message_id < ?2 LIMIT ?3")
    List<Message> findPageBefore(String conversationId, int bucket, long before, int limit);
}
