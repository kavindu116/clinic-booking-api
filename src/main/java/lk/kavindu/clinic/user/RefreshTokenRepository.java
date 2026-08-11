package lk.kavindu.clinic.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Logout ekedi — ee user ge okkoma tokens revoke karanawa. */
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.user.id = :userId AND rt.revoked = false")
    int revokeAllForUser(@Param("userId") Long userId);

    /** Cleanup job ekata (Week 3). */
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiresAt < :cutoff OR rt.revoked = true")
    int deleteExpiredAndRevoked(@Param("cutoff") Instant cutoff);
}
