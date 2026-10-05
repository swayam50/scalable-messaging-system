package io.wulfcodes.messaging.chat.repository;

import io.wulfcodes.messaging.chat.model.po.DirectConversation;
import io.wulfcodes.messaging.chat.model.po.eo.DirectConversationKey;
import io.wulfcodes.messaging.chat.repository.custom.DirectConversationCustomRepository;
import org.springframework.data.cassandra.repository.CassandraRepository;

public interface DirectConversationRepository
        extends CassandraRepository<DirectConversation, DirectConversationKey>, DirectConversationCustomRepository {
}
