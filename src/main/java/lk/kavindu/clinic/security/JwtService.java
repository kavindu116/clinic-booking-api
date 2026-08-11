package lk.kavindu.clinic.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lk.kavindu.clinic.user.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

/**
 * Access token: short-lived (15 min), stateless, DB hit ekak naa.
 * Refresh token: long-lived (7 days), DB eke hash ekak thiyenawa, revoke karanna puluwan.
 *
 * Mokada dekak? Access token eka leak unath 15 min ekakin useless.
 * Refresh token eka DB eke nisa logout ekedi instantly kill karanna puluwan.
 */
@Slf4j
@Service
public class JwtService {

    private final SecretKey key;
    private final JwtProperties props;

    public JwtService(JwtProperties props) {
        byte[] secretBytes = props.secret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret must be at least 32 bytes for HS256. " +
                    "Generate one with: openssl rand -base64 48");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.props = props;
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(Duration.ofMinutes(props.accessTtlMinutes()));

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .issuer(props.issuer())
                .claims(Map.of(
                        "email", user.getEmail(),
                        "role", user.getRole().name(),
                        "name", user.getFullName()))
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(props.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long extractUserId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    public long accessTtlSeconds() {
        return Duration.ofMinutes(props.accessTtlMinutes()).toSeconds();
    }

    public Duration refreshTtl() {
        return Duration.ofDays(props.refreshTtlDays());
    }
}
