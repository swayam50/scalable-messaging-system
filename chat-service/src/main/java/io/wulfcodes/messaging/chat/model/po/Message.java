package io.wulfcodes.messaging.chat.model.po;

import io.wulfcodes.messaging.chat.model.po.eo.MessageKey;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

@Table("messages_by_conversation")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    @PrimaryKey
    private MessageKey key;

    @Column("sender_id")
    private String senderId;

    @Column("body")
    private String body;

    @Column("client_message_id")
    private String clientMessageId;
}
