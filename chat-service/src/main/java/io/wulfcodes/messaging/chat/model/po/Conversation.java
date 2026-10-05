package io.wulfcodes.messaging.chat.model.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.Instant;
import java.util.Set;

@Table("conversations")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {

    @Id
    @Column("conversation_id")
    private String conversationId;

    @Column("participant_ids")
    private Set<String> participantIds;

    @Column("created_at")
    private Instant createdAt;

    public boolean hasParticipant(String userId) {
        return participantIds != null && participantIds.contains(userId);
    }

    /** For a 1:1 conversation: the participant who is not {@code userId}. */
    public String peerOf(String userId) {
        return participantIds.stream().filter(id -> !id.equals(userId)).findFirst().orElse(userId);
    }
}
