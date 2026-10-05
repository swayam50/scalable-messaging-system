package io.wulfcodes.messaging.chat.model.po;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.Instant;

@Table("user_presence")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPresence {

    @Id
    @Column("user_id")
    private String userId;

    @Column("last_seen")
    private Instant lastSeen;
}
