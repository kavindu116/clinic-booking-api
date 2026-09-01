package lk.kavindu.clinic.booking;

import lk.kavindu.clinic.booking.dto.CreateBookingRequest;
import lk.kavindu.clinic.common.exception.ApiException;
import lk.kavindu.clinic.common.exception.ErrorCode;
import lk.kavindu.clinic.doctor.Doctor;
import lk.kavindu.clinic.security.AppUserPrincipal;
import lk.kavindu.clinic.support.AbstractIntegrationTest;
import lk.kavindu.clinic.support.TestDataFactory;
import lk.kavindu.clinic.user.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;


class BookingConcurrencyIT extends AbstractIntegrationTest {

    private static final ZoneId COLOMBO = ZoneId.of("Asia/Colombo");
    private static final int CONTENDERS = 10;

    @Autowired private BookingService bookingService;
    @Autowired private TestDataFactory data;

    @Test
    @DisplayName("ten patients race for one slot — exactly one wins")
    void exactlyOneBookingSucceedsUnderContention() throws Exception {
        Doctor doctor = data.doctorWithWeekdayHours("race.doctor@clinic.lk");
        Instant contestedSlot = nextMondayAt(10, 0);

        List<AppUserPrincipal> patients = new ArrayList<>();
        for (int i = 0; i < CONTENDERS; i++) {
            User patient = data.patient("racer" + i + "@example.com");
            patients.add(new AppUserPrincipal(patient));
        }

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(CONTENDERS);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger slotTaken = new AtomicInteger();
        List<Throwable> unexpected = new CopyOnWriteArrayList<>();

        ExecutorService pool = Executors.newFixedThreadPool(CONTENDERS);
        try {
            for (AppUserPrincipal patient : patients) {
                pool.submit(() -> {
                    try {
                        startGate.await();          // okkoma ekapaarama patan gannawa
                        bookingService.create(patient,
                                new CreateBookingRequest(doctor.getId(), contestedSlot, null));
                        successes.incrementAndGet();
                    } catch (ApiException ex) {
                        if (ex.getCode() == ErrorCode.SLOT_ALREADY_BOOKED) {
                            slotTaken.incrementAndGet();
                        } else {
                            unexpected.add(ex);
                        }
                    } catch (Throwable t) {

                        unexpected.add(t);
                    } finally {
                        doneGate.countDown();
                    }
                });
            }

            startGate.countDown();
            assertThat(doneGate.await(30, TimeUnit.SECONDS))
                    .as("all %d threads finished within the timeout", CONTENDERS)
                    .isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(unexpected)
                .as("no unexpected exceptions — the advisory lock should reject cleanly, "
                        + "not fall through to a database constraint violation")
                .isEmpty();

        assertThat(successes.get())
                .as("exactly one booking is confirmed")
                .isEqualTo(1);

        assertThat(slotTaken.get())
                .as("everyone else is told the slot is taken")
                .isEqualTo(CONTENDERS - 1);

        assertThat(bookingRepository.countActiveAtSlot(doctor.getId(), contestedSlot))
                .as("the database holds exactly one active booking for that slot")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("contention on different slots does not serialise — all succeed")
    void differentSlotsDoNotBlockEachOther() throws Exception {
        Doctor doctor = data.doctorWithWeekdayHours("parallel.doctor@clinic.lk");

        // Slots 8ak: 09:00, 09:30, ... 12:30
        List<Instant> slots = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            slots.add(nextMondayAt(9, 0).plus(Duration.ofMinutes(30L * i)));
        }

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneGate = new CountDownLatch(slots.size());
        AtomicInteger successes = new AtomicInteger();
        List<Throwable> failures = new CopyOnWriteArrayList<>();

        ExecutorService pool = Executors.newFixedThreadPool(slots.size());
        try {
            for (int i = 0; i < slots.size(); i++) {
                User patient = data.patient("parallel" + i + "@example.com");
                AppUserPrincipal principal = new AppUserPrincipal(patient);
                Instant slot = slots.get(i);

                pool.submit(() -> {
                    try {
                        startGate.await();
                        bookingService.create(principal,
                                new CreateBookingRequest(doctor.getId(), slot, null));
                        successes.incrementAndGet();
                    } catch (Throwable t) {
                        failures.add(t);
                    } finally {
                        doneGate.countDown();
                    }
                });
            }

            startGate.countDown();
            assertThat(doneGate.await(30, TimeUnit.SECONDS)).isTrue();
        } finally {
            pool.shutdownNow();
        }

        assertThat(failures).isEmpty();
        assertThat(successes.get())
                .as("the lock is per-slot, not per-doctor — all eight go through")
                .isEqualTo(slots.size());
    }

    @Test
    @DisplayName("a cancelled slot can be booked again")
    void cancelledSlotBecomesBookableAgain() {
        Doctor doctor = data.doctorWithWeekdayHours("reuse.doctor@clinic.lk");
        Instant slot = nextMondayAt(11, 0);

        AppUserPrincipal first = new AppUserPrincipal(data.patient("first@example.com"));
        AppUserPrincipal second = new AppUserPrincipal(data.patient("second@example.com"));

        var booking = bookingService.create(first,
                new CreateBookingRequest(doctor.getId(), slot, null));


        assertThat(bookingRepository.countActiveAtSlot(doctor.getId(), slot)).isEqualTo(1);

        bookingService.cancel(first, booking.id());

        var rebooked = bookingService.create(second,
                new CreateBookingRequest(doctor.getId(), slot, null));

        assertThat(rebooked.id()).isNotEqualTo(booking.id());
        assertThat(bookingRepository.countActiveAtSlot(doctor.getId(), slot)).isEqualTo(1);
    }

    private Instant nextMondayAt(int hour, int minute) {
        LocalDate monday = LocalDate.now(COLOMBO)
                .with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.MONDAY));
        if (Duration.between(Instant.now(),
                monday.atTime(hour, minute).atZone(COLOMBO).toInstant()).toHours() < 2) {
            monday = monday.plusWeeks(1);
        }
        return monday.atTime(hour, minute).atZone(COLOMBO).toInstant();
    }
}
