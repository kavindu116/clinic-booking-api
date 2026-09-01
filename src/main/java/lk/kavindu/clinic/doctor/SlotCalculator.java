package lk.kavindu.clinic.doctor;

import java.time.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SlotCalculator {
    private SlotCalculator() {}

    public static List<Slot> candidateSlots(List<Availability> availabilities,
                                            LocalDate date,
                                            ZoneId clinicZone){
        List<Slot> slots = new ArrayList<>();
        short dow = (short) date.getDayOfWeek().getValue();
        for (Availability availability : availabilities) {
            if (!availability.getDayOfWeek().equals(dow)) {
                continue;
            }
            slots.addAll(slotsForBlock(availability, date, clinicZone));
        }

        slots.sort(Comparator.comparing(Slot::start));
        return slots;
    }

    private static List<Slot> slotsForBlock(Availability availability,
                                            LocalDate date,
                                            ZoneId clinicZone){
        List<Slot> slots = new ArrayList<>();
        int slotMinutes = availability.getSlotDurationMinutes();
        int blockStart = availability.getStartTime().toSecondOfDay() / 60;
        int blockEnd = availability.getEndTime().toSecondOfDay() / 60;

        for (int minute = blockStart; minute + slotMinutes <= blockEnd; minute += slotMinutes) {
            LocalTime localStart = LocalTime.ofSecondOfDay(minute * 60L);
            LocalTime localEnd = LocalTime.ofSecondOfDay((minute + slotMinutes) * 60L);

            LocalDateTime startLocal = LocalDateTime.of(date, localStart);

            slots.add(new Slot(
                    startLocal.atZone(clinicZone).toInstant(),
                    startLocal.plusMinutes(slotMinutes).atZone(clinicZone).toInstant(),
                    localStart,
                    localEnd));
        }

        return slots;
    }

    public record Slot(Instant start, Instant end, LocalTime localStart, LocalTime localEnd) {}
}
