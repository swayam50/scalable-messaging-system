package io.wulfcodes.messaging.chat.repository.custom;

import io.wulfcodes.messaging.chat.model.po.DirectConversation;
import lombok.RequiredArgsConstructor;
import org.springframework.data.cassandra.core.CassandraOperations;
import org.springframework.data.cassandra.core.InsertOptions;

/**
 * Spring Data picks this up as the implementation of {@link DirectConversationCustomRepository}
 * (the "Impl" suffix), and mixes it into DirectConversationRepository.
 */
@RequiredArgsConstructor
public class DirectConversationCustomRepositoryImpl implements DirectConversationCustomRepository {

    private final CassandraOperations cassandra;

    @Override
    public boolean insertIfNotExists(DirectConversation directConversation) {
        InsertOptions ifNotExists = InsertOptions.builder().withIfNotExists().build();
        return cassandra.insert(directConversation, ifNotExists).wasApplied();
    }
}
