package io.wulfcodes.messaging.chat.repository;

import io.wulfcodes.messaging.chat.model.po.SentMessage;
import io.wulfcodes.messaging.chat.model.po.eo.SentMessageKey;
import io.wulfcodes.messaging.chat.repository.custom.SentMessageCustomRepository;
import org.springframework.data.cassandra.repository.CassandraRepository;

public interface SentMessageRepository
        extends CassandraRepository<SentMessage, SentMessageKey>, SentMessageCustomRepository {
}
