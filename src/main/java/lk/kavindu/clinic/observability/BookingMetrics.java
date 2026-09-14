package lk.kavindu.clinic.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;


@Component
public class BookingMetrics {
    private final Counter created;
    private final Counter conflicts;
    private final Counter cancelled;
    private final Counter rejected;
    private final Timer createTimer;

    public BookingMetrics(MeterRegistry registry) {
        this.created = Counter.builder("clinic.bookings")
                .tag("outcome","created")
                .description("Booking Confirmed")
                .register(registry);

        this.conflicts = Counter.builder("clinic.bookings")
                .tag("outcome","conflicts")
                .description("Booking rejected because the slot was taken")
                .register(registry);

        this.cancelled = Counter.builder("clinic.bookings")
                .tag("outcome","cancelled")
                .description("Bookings cancelled ")
                .register(registry);

        this.rejected = Counter.builder("clinic.bookings")
                .tag("outcome","rejected")
                .description("Bookings rejected by a business rule")
                .register(registry);

        this.createTimer = Timer.builder("clinic.booking.duration")
                .description("Time to complete a bookig attempt")
                .publishPercentileHistogram()
                .register(registry);
    }

    public void bookingCreated() {
        created.increment();
    }
    public void slotConflicts() {
        conflicts.increment();
    }
    public void bookingCancelled() {
        cancelled.increment();
    }
    public void ruleRejected() {
        rejected.increment();
    }

    public Timer.Sample startTimer(MeterRegistry registry) {
        return Timer.start(registry);
    }

    public Timer createDuration(){
        return createTimer;
    }
}
