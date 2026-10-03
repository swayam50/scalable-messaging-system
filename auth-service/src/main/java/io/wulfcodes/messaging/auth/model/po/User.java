package io.wulfcodes.messaging.auth.model.po;

import io.wulfcodes.messaging.auth.model.po.eo.AuditInfo;
import io.wulfcodes.messaging.auth.model.vo.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Persisted user account. The primary key is a ULID string: sortable by creation time
 * and safe to expose publicly (not guessable like an auto-increment id).
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @Column(length = 26)
    private String id;

    @Column(nullable = false, length = 32)
    private String username;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 64)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private UserStatus status;

    @Embedded
    @Builder.Default
    private AuditInfo audit = new AuditInfo();

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }
}
