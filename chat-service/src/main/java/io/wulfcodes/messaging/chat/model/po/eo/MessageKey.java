package io.wulfcodes.messaging.chat.model.po.eo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;

/**
 * Composite primary key of messages_by_conversation: ((conversation_id, bucket), message_id DESC).
 * Embedded in {@code Message}; it has no table of its own.
 */
@PrimaryKeyClass
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MessageKey {

    @PrimaryKeyColumn(name = "conversation_id", ordinal = 0, type = PrimaryKeyType.PARTITIONED)
    private String conversationId;

    @PrimaryKeyColumn(name = "bucket", ordinal = 1, type = PrimaryKeyType.PARTITIONED)
    private int bucket;

    @PrimaryKeyColumn(name = "message_id", ordinal = 2, type = PrimaryKeyType.CLUSTERED, ordering = Ordering.DESCENDING)
    private long messageId;
}
