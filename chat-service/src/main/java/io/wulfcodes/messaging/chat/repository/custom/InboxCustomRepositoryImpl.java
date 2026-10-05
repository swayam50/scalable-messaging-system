package io.wulfcodes.messaging.chat.repository.custom;

import io.wulfcodes.messaging.chat.model.po.InboxEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.data.cassandra.core.CassandraOperations;
import org.springframework.data.cassandra.core.InsertOptions;
import org.springframework.data.cassandra.core.UpdateOptions;
import org.springframework.data.cassandra.core.query.Query;
import org.springframework.data.cassandra.core.query.Update;

import static org.springframework.data.cassandra.core.query.Criteria.where;

@RequiredArgsConstructor
public class InboxCustomRepositoryImpl implements InboxCustomRepository {

    private final CassandraOperations cassandra;

    @Override
    public void upsertWithTimestamp(InboxEntry entry, long writeTimeMicros) {
        cassandra.insert(entry, InsertOptions.builder().timestamp(writeTimeMicros).build());
    }

    @Override
    public void updateReadPointer(String userId, String conversationId, ReadPointer pointer,
                                  long messageId, long writeTimeMicros) {
        Query row = Query.query(where("user_id").is(userId), where("conversation_id").is(conversationId))
                .queryOptions(UpdateOptions.builder().timestamp(writeTimeMicros).build());
        cassandra.update(row, Update.empty().set(pointer.column, messageId), InboxEntry.class);
    }
}
