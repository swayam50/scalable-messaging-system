package io.wulfcodes.messaging.chat.model.po;

import io.wulfcodes.messaging.chat.model.po.eo.AttachmentUdt;
import io.wulfcodes.messaging.chat.model.po.eo.MessageKey;
import io.wulfcodes.messaging.common.model.vo.ContentType;
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

    /** null for rows written before media support = TEXT */
    @Column("content_type")
    private ContentType contentType;

    @Column("attachment")
    private AttachmentUdt attachment;
}
