package io.wulfcodes.messaging.chat.model.po;

import io.wulfcodes.messaging.chat.model.po.eo.InboxKey;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

/**
 * One row of a user's inbox (conversations_by_user).
 * {@code lastMessageId} is null until the first message is sent.
 */
@Table("conversations_by_user")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InboxEntry {

    @PrimaryKey
    private InboxKey key;

    @Column("peer_id")
    private String peerId;

    @Column("last_message_id")
    private Long lastMessageId;

    @Column("last_sender_id")
    private String lastSenderId;

    @Column("preview")
    private String preview;
}
