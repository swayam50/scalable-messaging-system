package io.wulfcodes.messaging.chat.repository.custom;

import io.wulfcodes.messaging.chat.model.po.InboxEntry;

public interface InboxCustomRepository {

    /**
     * Upserts an inbox row with an explicit write timestamp (USING TIMESTAMP).
     * Cassandra/Scylla resolves concurrent writes by timestamp ("last write wins"), so using
     * the message's own time means the newest message always wins, whatever the arrival order.
     */
    void upsertWithTimestamp(InboxEntry entry, long writeTimeMicros);
}
