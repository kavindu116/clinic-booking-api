package lk.kavindu.clinic.doctor.dto;

import java.time.LocalDate;
import java.util.List;

public record DaySlotsResponse(
        Long doctorId,
        String doctorName,
        LocalDate date,
        String dayOfWeek,
        String timezone,
        int totalSlots,
        int availableSlots,
        List<SlotResponse> slots
) {
}
