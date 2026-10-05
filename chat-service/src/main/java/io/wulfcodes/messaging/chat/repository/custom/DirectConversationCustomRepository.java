package io.wulfcodes.messaging.chat.repository.custom;

import io.wulfcodes.messaging.chat.model.po.DirectConversation;

public interface DirectConversationCustomRepository {

    /**
     * INSERT ... IF NOT EXISTS (lightweight transaction).
     *
     * @return true if this call created the row, false if it already existed
     */
    boolean insertIfNotExists(DirectConversation directConversation);
}
