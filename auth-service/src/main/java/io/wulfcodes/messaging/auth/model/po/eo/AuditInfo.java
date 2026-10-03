package io.wulfcodes.messaging.auth.model.po.eo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Embedded object: persisted as columns of the owning table (no table of its own).
 * Hibernate fills the timestamps automatically on insert/update.
 */
@Embeddable
@Getter
@NoArgsConstructor
public class AuditInfo {

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
