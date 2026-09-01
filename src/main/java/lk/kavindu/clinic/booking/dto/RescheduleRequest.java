package lk.kavindu.clinic.booking.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record RescheduleRequest (
        @NotNull(message = "slotStart is required")
        Instant slotStart
) {
}
