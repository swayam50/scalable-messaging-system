package io.wulfcodes.messaging.chat.model.po.eo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;

/**
 * Partition key ((user_low, user_high)) of direct_conversations.
 * The pair is always stored sorted, so (A,B) and (B,A) map to the same row.
 */
@PrimaryKeyClass
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DirectConversationKey {

    @PrimaryKeyColumn(name = "user_low", ordinal = 0, type = PrimaryKeyType.PARTITIONED)
    private String userLow;

    @PrimaryKeyColumn(name = "user_high", ordinal = 1, type = PrimaryKeyType.PARTITIONED)
    private String userHigh;

    public static DirectConversationKey of(String userA, String userB) {
        return userA.compareTo(userB) <= 0
                ? new DirectConversationKey(userA, userB)
                : new DirectConversationKey(userB, userA);
    }
}
