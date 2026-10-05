package io.wulfcodes.messaging.chat.repository.custom;

import io.wulfcodes.messaging.chat.model.po.ClusterNode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.cassandra.core.CassandraOperations;
import org.springframework.data.cassandra.core.InsertOptions;

import java.time.Duration;

@RequiredArgsConstructor
public class ClusterNodeCustomRepositoryImpl implements ClusterNodeCustomRepository {

    private final CassandraOperations cassandra;

    @Override
    public void upsertWithTtl(ClusterNode node, Duration ttl) {
        cassandra.insert(node, InsertOptions.builder().ttl(ttl).build());
    }
}
