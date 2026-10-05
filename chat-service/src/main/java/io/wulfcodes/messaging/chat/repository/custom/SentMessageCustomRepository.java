package io.wulfcodes.messaging.chat.repository.custom;

import io.wulfcodes.messaging.chat.model.po.SentMessage;

public interface SentMessageCustomRepository {

    /**
     * INSERT ... IF NOT EXISTS (lightweight transaction).
     *
     * @return {@code claim} if this call created the row, otherwise the row that already existed
     */
    SentMessage claim(SentMessage claim);
}
