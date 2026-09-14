package lk.kavindu.clinic.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class TokenCleanupJob {
    private static final Duration REVOKE_GRACE_PERIOD = Duration.ofDays(7);
    private final RefreshTokenRepository refreshTokenRepository;

    @Scheduled(cron = "${app.cleanup.token-cron:0 30 3 * * *}")
    @Transactional
    public void purgeStaleTokens(){
        Instant cutoff = Instant.now().minus(REVOKE_GRACE_PERIOD);
        int removes = refreshTokenRepository.deleteExpiredAndRevoked(cutoff);
        if(removes > 0){
            log.info("Token cleanup: removed {} stale refresh tokens", removes);
        }
    }
}
