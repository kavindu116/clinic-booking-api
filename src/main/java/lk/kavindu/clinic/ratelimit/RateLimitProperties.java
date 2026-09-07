package lk.kavindu.clinic.ratelimit;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties (
        boolean enabled,

        @Min(1)
        int loginAttempts,

        @Min(1)
        int windowSeconds
){
}
