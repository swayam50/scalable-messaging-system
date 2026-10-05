package io.wulfcodes.messaging.chat.repository.custom;

import io.wulfcodes.messaging.chat.model.po.InboxEntry;

public interface InboxCustomRepository {

    /**
     * Upserts an inbox row with an explicit write timestamp (USING TIMESTAMP).
     * Cassandra/Scylla resolves concurrent writes by timestamp ("last write wins"), so using
     * the message's own time means the newest message always wins, whatever the arrival order.
     * Null fields are not written (no tombstones, and existing read pointers are left untouched).
     */
    void upsertWithTimestamp(InboxEntry entry, long writeTimeMicros);

    /**
     * Sets one read-pointer column of a single inbox row, USING TIMESTAMP = the message's time,
     * so the pointer can only ever move forward.
     */
    void updateReadPointer(String userId, String conversationId, ReadPointer pointer, long messageId, long writeTimeMicros);

    enum ReadPointer {
        /** the row owner's own read position */
        OWN("last_read_message_id"),
        /** the other participant's read position, kept on the row owner's inbox for "seen" ticks */
        PEER("peer_last_read_message_id");

        final String column;

        ReadPointer(String column) {
            this.column = column;
        }
    }
}
