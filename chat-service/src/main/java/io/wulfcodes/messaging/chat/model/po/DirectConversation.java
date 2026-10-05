package io.wulfcodes.messaging.chat.model.po;

import io.wulfcodes.messaging.chat.model.po.eo.DirectConversationKey;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

@Table("direct_conversations")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DirectConversation {

    @PrimaryKey
    private DirectConversationKey key;

    @Column("conversation_id")
    private String conversationId;
}
