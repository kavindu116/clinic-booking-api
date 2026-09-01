package lk.kavindu.clinic.booking;

import lk.kavindu.clinic.booking.dto.CreateBookingRequest;
import lk.kavindu.clinic.booking.dto.RescheduleRequest;
import lk.kavindu.clinic.common.exception.ApiException;
import lk.kavindu.clinic.common.exception.ErrorCode;
import lk.kavindu.clinic.doctor.Doctor;
import lk.kavindu.clinic.security.AppUserPrincipal;
import lk.kavindu.clinic.support.AbstractIntegrationTest;
import lk.kavindu.clinic.support.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.*;
import java.time.temporal.TemporalAdjusters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Booking eke business rules. */
class BookingRulesIT extends AbstractIntegrationTest {

    private static final ZoneId COLOMBO = ZoneId.of("Asia/Colombo");

    @Autowired private BookingService bookingService;
    @Autowired private TestDataFactory data;

    @Test
    @DisplayName("rejects a time that is not on the doctor's slot grid")
    void rejectsOffGridTime() {
        Doctor doctor = data.doctorWithWeekdayHours("grid@clinic.lk");
        var patient = new AppUserPrincipal(data.patient("gridpatient@example.com"));

        // 10:15 — slots thiyenne :00 saha :30 walata
        Instant offGrid = nextMondayAt(10, 15);

        assertThatThrownBy(() -> bookingService.create(patient,
                new CreateBookingRequest(doctor.getId(), offGrid, null)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode())
                .isEqualTo(ErrorCode.SLOT_NOT_AVAILABLE);
    }

    @Test
    @DisplayName("rejects a day the doctor does not consult")
    void rejectsNonWorkingDay() {
        Doctor doctor = data.doctorWithWeekdayHours("weekday@clinic.lk");
        var patient = new AppUserPrincipal(data.patient("weekendpatient@example.com"));

        LocalDate sunday = LocalDate.now(COLOMBO).with(TemporalAdjusters.next(DayOfWeek.SUNDAY));
        Instant onSunday = sunday.atTime(10, 0).atZone(COLOMBO).toInstant();

        assertThatThrownBy(() -> bookingService.create(patient,
                new CreateBookingRequest(doctor.getId(), onSunday, null)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode())
                .isEqualTo(ErrorCode.SLOT_NOT_AVAILABLE);
    }

    @Test
    @DisplayName("rejects a slot in the past")
    void rejectsPastSlot() {
        Doctor doctor = data.doctorWithWeekdayHours("past@clinic.lk");
        var patient = new AppUserPrincipal(data.patient("pastpatient@example.com"));

        LocalDate lastMonday = LocalDate.now(COLOMBO)
                .with(TemporalAdjusters.previous(DayOfWeek.MONDAY));
        Instant past = lastMonday.atTime(10, 0).atZone(COLOMBO).toInstant();

        assertThatThrownBy(() -> bookingService.create(patient,
                new CreateBookingRequest(doctor.getId(), past, null)))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode())
                .isEqualTo(ErrorCode.BOOKING_RULE_VIOLATION);
    }

    @Test
    @DisplayName("enforces the per-patient upcoming booking limit")
    void enforcesUpcomingLimit() {
        Doctor doctor = data.doctorWithWeekdayHours("limit@clinic.lk");
        var patient = new AppUserPrincipal(data.patient("limitpatient@example.com"));

        // Limit eka 3ක් (application-test.yml)
        for (int i = 0; i < 3; i++) {
            bookingService.create(patient, new CreateBookingRequest(
                    doctor.getId(), nextMondayAt(9, 0).plus(Duration.ofMinutes(30L * i)), null));
        }

        assertThatThrownBy(() -> bookingService.create(patient,
                new CreateBookingRequest(doctor.getId(), nextMondayAt(11, 0), null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("limit is 3");
    }

    @Test
    @DisplayName("stops a patient double-booking themselves across two doctors")
    void rejectsPatientDoubleBooking() {
        Doctor first = data.doctorWithWeekdayHours("clash1@clinic.lk");
        Doctor second = data.doctorWithWeekdayHours("clash2@clinic.lk");
        var patient = new AppUserPrincipal(data.patient("clashpatient@example.com"));

        Instant slot = nextMondayAt(10, 0);
        bookingService.create(patient, new CreateBookingRequest(first.getId(), slot, null));

        assertThatThrownBy(() -> bookingService.create(patient,
                new CreateBookingRequest(second.getId(), slot, null)))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("another appointment at that time");
    }

    @Test
    @DisplayName("reschedule frees the old slot and takes the new one")
    void rescheduleMovesTheBooking() {
        Doctor doctor = data.doctorWithWeekdayHours("move@clinic.lk");
        var patient = new AppUserPrincipal(data.patient("movepatient@example.com"));

        Instant original = nextMondayAt(9, 0);
        Instant target = nextMondayAt(11, 30);

        var booking = bookingService.create(patient,
                new CreateBookingRequest(doctor.getId(), original, "Annual check-up"));

        var moved = bookingService.reschedule(patient, booking.id(),
                new RescheduleRequest(target));

        assertThat(moved.slotStart()).isEqualTo(target);
        assertThat(moved.notes()).isEqualTo("Annual check-up");
        assertThat(bookingRepository.countActiveAtSlot(doctor.getId(), original)).isZero();
        assertThat(bookingRepository.countActiveAtSlot(doctor.getId(), target)).isEqualTo(1);
    }

    @Test
    @DisplayName("a patient cannot see another patient's booking")
    void hidesOtherPatientsBookings() {
        Doctor doctor = data.doctorWithWeekdayHours("privacy@clinic.lk");
        var owner = new AppUserPrincipal(data.patient("owner@example.com"));
        var stranger = new AppUserPrincipal(data.patient("stranger@example.com"));

        var booking = bookingService.create(owner,
                new CreateBookingRequest(doctor.getId(), nextMondayAt(12, 0), null));

        // 403 nemei 404 — bookings thiyenawada kiyala enumerate karanna denne naa
        assertThatThrownBy(() -> bookingService.get(stranger, booking.id()))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode())
                .isEqualTo(ErrorCode.BOOKING_NOT_FOUND);
    }

    private Instant nextMondayAt(int hour, int minute) {
        LocalDate monday = LocalDate.now(COLOMBO).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        if (Duration.between(Instant.now(),
                monday.atTime(hour, minute).atZone(COLOMBO).toInstant()).toHours() < 2) {
            monday = monday.plusWeeks(1);
        }
        return monday.atTime(hour, minute).atZone(COLOMBO).toInstant();
    }
}
