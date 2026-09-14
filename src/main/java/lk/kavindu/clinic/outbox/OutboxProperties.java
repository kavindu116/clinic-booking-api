package lk.kavindu.clinic.outbox;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.outbox")
public record OutboxProperties(
        boolean enabled,

        @Min(1) @Max(500)
        int batchSize,

        @Min(1) @Max(20)
        int maxAttempts,

        @Min(1) @Max(365)
        int retainPublishedDays
) {
}
