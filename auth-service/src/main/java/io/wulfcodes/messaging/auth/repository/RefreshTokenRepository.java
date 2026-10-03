package io.wulfcodes.messaging.auth.repository;

import io.wulfcodes.messaging.auth.model.po.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, String> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Revokes every active token of a user (used on refresh-token reuse, i.e. suspected theft). */
    @Modifying
    @Query("update RefreshToken t set t.revoked = true where t.user.id = :userId and t.revoked = false")
    int revokeAllForUser(@Param("userId") String userId);
}
