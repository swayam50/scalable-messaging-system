package io.wulfcodes.messaging.chat.repository.custom;

import com.datastax.oss.driver.api.core.cql.Row;
import io.wulfcodes.messaging.chat.model.po.SentMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.data.cassandra.core.CassandraOperations;
import org.springframework.data.cassandra.core.EntityWriteResult;
import org.springframework.data.cassandra.core.InsertOptions;

@RequiredArgsConstructor
public class SentMessageCustomRepositoryImpl implements SentMessageCustomRepository {

    private final CassandraOperations cassandra;

    @Override
    public SentMessage claim(SentMessage claim) {
        InsertOptions ifNotExists = InsertOptions.builder().withIfNotExists().build();
        EntityWriteResult<SentMessage> result = cassandra.insert(claim, ifNotExists);
        if (result.wasApplied()) {
            return claim;
        }
        // A lost LWT returns the existing row in the same response: no second (possibly stale) read.
        Row existing = result.getRows().getFirst();
        return SentMessage.builder()
                .key(claim.getKey())
                .conversationId(existing.getString("conversation_id"))
                .messageId(existing.getLong("message_id"))
                .build();
    }
}
