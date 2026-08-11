package lk.kavindu.clinic.auth;

import lk.kavindu.clinic.user.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh token reuse ekak detect unama, revocation eka commit wenna one —
 * eth request eka reject karanna one. Ekama transaction ekaka meka karanna baa:
 * exception eka throw kalama revocation ekath rollback wenawa.
 *
 * REQUIRES_NEW ekෙන් pita transaction eka suspend karala aluth ekak patan gannawa.
 * Eka commit unaata passe pita eka rollback unath revocation eka ithuruyi.
 *
 * Wenama bean ekak wenna one — ekama class eke method ekak call kalot
 * Spring proxy eka bypass wenawa, propagation eka apply wenne naa.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenRevocationService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void revokeAllForUser(Long userId) {
        int revoked = refreshTokenRepository.revokeAllForUser(userId);
        log.warn("Revoked {} refresh token(s) for userId={}", revoked, userId);
    }
}