package io.wulfcodes.messaging.chat.model.po.eo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;

/**
 * Primary key (user_id, conversation_id) of conversations_by_user.
 */
@PrimaryKeyClass
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class InboxKey {

    @PrimaryKeyColumn(name = "user_id", ordinal = 0, type = PrimaryKeyType.PARTITIONED)
    private String userId;

    @PrimaryKeyColumn(name = "conversation_id", ordinal = 1, type = PrimaryKeyType.CLUSTERED)
    private String conversationId;
}
