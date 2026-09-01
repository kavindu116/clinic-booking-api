package lk.kavindu.clinic.booking.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateBookingRequest(
        @Schema(example = "1")
        @NotNull(message = "doctor Id is requireed")
        Long doctorId,

        @Schema(description = "slot start as returned by GET / doctors{id}/slots")
        @NotNull(message = "slotStart is required")
        Instant slotStrat,

        @Schema(example = "Follow on last month's blood test")
        @Size(max = 500)
        String notes

) {
}
