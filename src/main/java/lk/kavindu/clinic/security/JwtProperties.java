package lk.kavindu.clinic.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        long accessTtlMinutes,
        long refreshTtlDays,
        String issuer
) {}
