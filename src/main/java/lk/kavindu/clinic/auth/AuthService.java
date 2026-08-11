package lk.kavindu.clinic.auth;

import lk.kavindu.clinic.auth.dto.*;
import lk.kavindu.clinic.common.exception.ApiException;
import lk.kavindu.clinic.common.exception.ErrorCode;
import lk.kavindu.clinic.security.JwtService;
import lk.kavindu.clinic.user.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TokenRevocationService tokenRevocationService;

    private static final SecureRandom RANDOM = new SecureRandom();

    /** Public registration ekedi PATIENT witharai. DOCTOR/ADMIN admin kenek hadanawa. */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.of(ErrorCode.EMAIL_ALREADY_EXISTS,
                    "An account with this email already exists");
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName().trim())
                .phone(request.phone())
                .role(Role.PATIENT)
                .enabled(true)
                .build();

        user = userRepository.save(user);
        log.info("Registered new patient: id={}", user.getId());

        return issueTokens(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {

        User user = userRepository.findByEmailIgnoreCase(request.email().trim())
                .orElseThrow(() -> ApiException.of(ErrorCode.INVALID_CREDENTIALS,
                        "Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.debug("Failed login attempt for userId={}", user.getId());
            throw ApiException.of(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password");
        }

        if (!user.isEnabled()) {
            throw ApiException.of(ErrorCode.ACCOUNT_DISABLED,
                    "This account has been disabled. Contact the clinic.");
        }

        log.info("Login success: userId={}", user.getId());
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        String hash = sha256(request.refreshToken());

        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> ApiException.of(ErrorCode.TOKEN_INVALID,
                        "Invalid refresh token"));

        if (stored.isRevoked()) {
            log.warn("Revoked refresh token reuse detected for userId={}", stored.getUser().getId());
            tokenRevocationService.revokeAllForUser(stored.getUser().getId());
            throw ApiException.of(ErrorCode.TOKEN_INVALID,
                    "Refresh token has been revoked. Please log in again.");
        }

        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.of(ErrorCode.TOKEN_EXPIRED,
                    "Refresh token has expired. Please log in again.");
        }

        User user = stored.getUser();
        if (!user.isEnabled()) {
            throw ApiException.of(ErrorCode.ACCOUNT_DISABLED, "This account has been disabled");
        }

        stored.setRevoked(true);          // rotation
        refreshTokenRepository.save(stored);

        return issueTokens(user);
    }

    @Transactional
    public void logout(Long userId) {
       tokenRevocationService.revokeAllForUser(userId);
        log.info("Logout: revoked {} refresh token(s) for userId={}", userId);
    }

    // ---------------------------------------------------------------

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = generateRefreshToken();

        refreshTokenRepository.save(RefreshToken.builder()
                .user(user)
                .tokenHash(sha256(refreshToken))
                .expiresAt(Instant.now().plus(jwtService.refreshTtl()))
                .revoked(false)
                .build());

        return AuthResponse.of(accessToken, refreshToken,
                jwtService.accessTtlSeconds(), UserSummary.from(user));
    }


    private String generateRefreshToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
