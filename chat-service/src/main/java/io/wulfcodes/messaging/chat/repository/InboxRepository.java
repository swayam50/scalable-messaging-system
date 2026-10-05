package io.wulfcodes.messaging.chat.repository;

import io.wulfcodes.messaging.chat.model.po.InboxEntry;
import io.wulfcodes.messaging.chat.model.po.eo.InboxKey;
import io.wulfcodes.messaging.chat.repository.custom.InboxCustomRepository;
import org.springframework.data.cassandra.repository.CassandraRepository;

import java.util.List;

public interface InboxRepository extends CassandraRepository<InboxEntry, InboxKey>, InboxCustomRepository {

    /** Whole inbox partition of one user (bounded: one row per conversation). */
    List<InboxEntry> findByKeyUserId(String userId);
}
