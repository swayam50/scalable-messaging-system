package io.wulfcodes.messaging.chat.model.po.eo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;

/** Partition key ((sender_id, client_message_id)) of sent_messages. */
@PrimaryKeyClass
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SentMessageKey {

    @PrimaryKeyColumn(name = "sender_id", ordinal = 0, type = PrimaryKeyType.PARTITIONED)
    private String senderId;

    @PrimaryKeyColumn(name = "client_message_id", ordinal = 1, type = PrimaryKeyType.PARTITIONED)
    private String clientMessageId;
}
