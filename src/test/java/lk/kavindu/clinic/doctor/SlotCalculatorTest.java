package lk.kavindu.clinic.doctor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.*;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SlotCalculatorTest {

    private static final ZoneId COLOMBO = ZoneId.of("Asia/Colombo");
    private static final LocalDate MONDAY = LocalDate.of(2026, 8, 17);
    private static final LocalDate TUESDAY = LocalDate.of(2026, 8, 18);

    private Availability block(DayOfWeek day, String start, String end, int minutes) {
        return Availability.builder()
                .dayOfWeek((short) day.getValue())
                .startTime(LocalTime.parse(start))
                .endTime(LocalTime.parse(end))
                .slotDurationMinutes((short) minutes)
                .build();
    }

    @Test
    @DisplayName("divides a block evenly into slots of the configured length")
    void dividesBlockIntoSlots() {
        var slots = SlotCalculator.candidateSlots(
                List.of(block(DayOfWeek.MONDAY, "09:00", "13:00", 30)), MONDAY, COLOMBO);

        assertThat(slots).hasSize(8);
        assertThat(slots.getFirst().localStart()).isEqualTo(LocalTime.of(9, 0));
        assertThat(slots.getFirst().localEnd()).isEqualTo(LocalTime.of(9, 30));
        assertThat(slots.getLast().localStart()).isEqualTo(LocalTime.of(12, 30));
        assertThat(slots.getLast().localEnd()).isEqualTo(LocalTime.of(13, 0));
    }

    @Test
    @DisplayName("drops a trailing slot that would overrun the block")
    void dropsPartialTrailingSlot() {
        // 09:00-10:00 with 45-minute slots: 09:00-09:45 fits, 09:45-10:30 does not
        var slots = SlotCalculator.candidateSlots(
                List.of(block(DayOfWeek.MONDAY, "09:00", "10:00", 45)), MONDAY, COLOMBO);

        assertThat(slots).hasSize(1);
        assertThat(slots.getFirst().localEnd()).isEqualTo(LocalTime.of(9, 45));
    }

    @Test
    @DisplayName("returns nothing when the rule is for a different weekday")
    void ignoresOtherWeekdays() {
        var slots = SlotCalculator.candidateSlots(
                List.of(block(DayOfWeek.MONDAY, "09:00", "13:00", 30)), TUESDAY, COLOMBO);

        assertThat(slots).isEmpty();
    }

    @Test
    @DisplayName("merges multiple blocks on the same day and returns them in order")
    void mergesMorningAndAfternoonBlocks() {
        var slots = SlotCalculator.candidateSlots(List.of(
                block(DayOfWeek.MONDAY, "16:00", "18:00", 60),   // deliberately out of order
                block(DayOfWeek.MONDAY, "09:00", "11:00", 60)
        ), MONDAY, COLOMBO);

        assertThat(slots).hasSize(4);
        assertThat(slots.stream().map(SlotCalculator.Slot::localStart))
                .containsExactly(
                        LocalTime.of(9, 0), LocalTime.of(10, 0),
                        LocalTime.of(16, 0), LocalTime.of(17, 0));
    }

    @Test
    @DisplayName("returns nothing when the block is shorter than one slot")
    void handlesBlockShorterThanSlot() {
        var slots = SlotCalculator.candidateSlots(
                List.of(block(DayOfWeek.MONDAY, "09:00", "09:20", 30)), MONDAY, COLOMBO);

        assertThat(slots).isEmpty();
    }

    @Test
    @DisplayName("converts clinic wall-clock time to the correct UTC instant")
    void convertsToCorrectInstant() {
        var slots = SlotCalculator.candidateSlots(
                List.of(block(DayOfWeek.MONDAY, "09:00", "09:30", 30)), MONDAY, COLOMBO);

        // Asia/Colombo is UTC+5:30, so 09:00 local is 03:30 UTC
        assertThat(slots.getFirst().start())
                .isEqualTo(ZonedDateTime.of(MONDAY, LocalTime.of(9, 0), COLOMBO).toInstant())
                .isEqualTo(Instant.parse("2026-08-17T03:30:00Z"));
    }

    @Test
    @DisplayName("does not loop forever on a block that runs up to midnight")
    void terminatesAtMidnight() {
        var slots = SlotCalculator.candidateSlots(
                List.of(block(DayOfWeek.MONDAY, "23:00", "23:59", 30)), MONDAY, COLOMBO);

        assertThat(slots).hasSize(1);
        assertThat(slots.getFirst().localEnd()).isEqualTo(LocalTime.of(23, 30));
    }

    @Test
    @DisplayName("every slot is exactly one slot-length long")
    void slotsHaveConsistentDuration() {
        var slots = SlotCalculator.candidateSlots(
                List.of(block(DayOfWeek.MONDAY, "08:00", "17:00", 20)), MONDAY, COLOMBO);

        assertThat(slots).hasSize(27);
        assertThat(slots).allSatisfy(slot ->
                assertThat(Duration.between(slot.start(), slot.end()))
                        .isEqualTo(Duration.ofMinutes(20)));
    }

    @Test
    @DisplayName("slots never overlap")
    void slotsDoNotOverlap() {
        var slots = SlotCalculator.candidateSlots(List.of(
                block(DayOfWeek.MONDAY, "09:00", "12:00", 30),
                block(DayOfWeek.MONDAY, "14:00", "17:00", 30)
        ), MONDAY, COLOMBO);

        for (int i = 1; i < slots.size(); i++) {
            assertThat(slots.get(i).start())
                    .isAfterOrEqualTo(slots.get(i - 1).end());
        }
    }

    @ParameterizedTest(name = "{0}-{1} at {2} minutes yields {3} slots")
    @DisplayName("slot count for various block lengths")
    @CsvSource({
            "09:00, 13:00, 30, 8",
            "09:00, 13:00, 60, 4",
            "09:00, 13:00, 15, 16",
            "09:00, 09:30, 30, 1",
            "09:00, 09:29, 30, 0",
            "08:00, 20:00, 45, 16"
    })
    void slotCountMatrix(String start, String end, int minutes, int expected) {
        var slots = SlotCalculator.candidateSlots(
                List.of(block(DayOfWeek.MONDAY, start, end, minutes)), MONDAY, COLOMBO);

        assertThat(slots).hasSize(expected);
    }
}
