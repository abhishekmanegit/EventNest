package com.eventnest.security;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Revoke every live token for a user.
     *
     * <p>The {@code AND revokedAt is null} predicate is not just an optimisation,
     * it is a concurrency guard: two simultaneous refresh requests cannot both
     * observe "not revoked" and both rotate successfully.
     */
    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now "
            + "where t.userId = :userId and t.revokedAt is null")
    int revokeAllForUser(@Param("userId") Long userId, @Param("now") Instant now);

    /** Housekeeping: rows that can never be valid again. */
    @Modifying
    @Query("delete from RefreshToken t where t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}