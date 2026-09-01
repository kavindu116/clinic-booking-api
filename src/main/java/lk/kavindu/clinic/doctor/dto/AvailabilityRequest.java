package lk.kavindu.clinic.doctor.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

public record AvailabilityRequest(

        @Schema(example = "1", description = "1 = Monday ... 7 = Sunday (ISO-8601)")
        @NotNull @Min(1) @Max(7)
        Short dayOfWeek,

        @Schema(example = "09:00", type = "string")
        @NotNull
        @JsonFormat(pattern = "HH:mm")
        LocalTime startTime,

        @Schema(example = "13:00", type = "string")
        @NotNull
        @JsonFormat(pattern = "HH:mm")
        LocalTime endTime,

        @Schema(example = "30", description = "Allowed: 10, 15, 20, 30, 45, 60")
        @NotNull @Min(10) @Max(60)
        Short slotDurationMinutes
) {
}
