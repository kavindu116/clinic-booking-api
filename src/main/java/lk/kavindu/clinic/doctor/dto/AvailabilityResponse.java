package lk.kavindu.clinic.doctor.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lk.kavindu.clinic.doctor.Availability;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record AvailabilityResponse(
        Long id,
        Short dayOfWeek,
        String dsyName,
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm") LocalTime endTime,
        Short slotDurationMinutes
) {
    public static AvailabilityResponse from(Availability availability) {
        return new AvailabilityResponse(
                availability.getId(),
                availability.getDayOfWeek(),
                DayOfWeek.of(availability.getDayOfWeek()).name(),
                availability.getStartTime(),
                availability.getEndTime(),
                availability.getSlotDurationMinutes()
        );
    }
}
