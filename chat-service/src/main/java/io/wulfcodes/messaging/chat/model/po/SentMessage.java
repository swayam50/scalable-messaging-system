package io.wulfcodes.messaging.chat.model.po;

import io.wulfcodes.messaging.chat.model.po.eo.SentMessageKey;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

/** Claim on a client message id: which message a (sender, clientMessageId) pair was stored as. */
@Table("sent_messages")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SentMessage {

    @PrimaryKey
    private SentMessageKey key;

    @Column("conversation_id")
    private String conversationId;

    @Column("message_id")
    private long messageId;
}
