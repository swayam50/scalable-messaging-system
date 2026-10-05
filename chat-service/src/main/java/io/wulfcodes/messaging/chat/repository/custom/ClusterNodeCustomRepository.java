package io.wulfcodes.messaging.chat.repository.custom;

import io.wulfcodes.messaging.chat.model.po.ClusterNode;

import java.time.Duration;

public interface ClusterNodeCustomRepository {

    /** INSERT ... USING TTL: the row disappears by itself unless refreshed in time. */
    void upsertWithTtl(ClusterNode node, Duration ttl);
}
