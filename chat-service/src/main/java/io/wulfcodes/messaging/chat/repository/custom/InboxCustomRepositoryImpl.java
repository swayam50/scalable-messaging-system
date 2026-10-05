package io.wulfcodes.messaging.chat.repository.custom;

import io.wulfcodes.messaging.chat.model.po.InboxEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.data.cassandra.core.CassandraOperations;
import org.springframework.data.cassandra.core.InsertOptions;

@RequiredArgsConstructor
public class InboxCustomRepositoryImpl implements InboxCustomRepository {

    private final CassandraOperations cassandra;

    @Override
    public void upsertWithTimestamp(InboxEntry entry, long writeTimeMicros) {
        cassandra.insert(entry, InsertOptions.builder().timestamp(writeTimeMicros).build());
    }
}
