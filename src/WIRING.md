# Week 3 — wiring the new files in

Your Week 2 files have local fixes in them, so this folder holds only **new** files
plus the two configs. Copy the new ones in, then apply the four small edits below by hand.

## 1. New files — copy as-is

| From | To |
|---|---|
| `main/outbox/*.java` | `src/main/java/lk/kavindu/clinic/outbox/` |
| `main/notification/*.java` | `src/main/java/lk/kavindu/clinic/notification/` |
| `main/observability/*.java` | `src/main/java/lk/kavindu/clinic/observability/` |
| `main/ratelimit/*.java` | `src/main/java/lk/kavindu/clinic/ratelimit/` |
| `main/config/CacheConfig.java` | `src/main/java/lk/kavindu/clinic/config/` |
| `main/user/TokenCleanupJob.java` | `src/main/java/lk/kavindu/clinic/user/` |
| `main/user/RefreshTokenRepository.java` | `src/main/java/lk/kavindu/clinic/user/` (replaces) |
| `main/resources/db/migration/V2__outbox_and_indexes.sql` | `src/main/resources/db/migration/` |
| `main/resources/application.yml` | `src/main/resources/` (replaces) |
| `test/outbox/OutboxIT.java` | `src/test/java/lk/kavindu/clinic/outbox/` |
| `test/resources/application-test.yml` | `src/test/resources/` (replaces) |

## 2. `ClinicBookingApplication.java` — enable scheduling

The outbox publisher and both cleanup jobs are `@Scheduled`. Without this annotation
they are ordinary beans that nobody ever calls, and the tests would still pass because
they invoke the publisher directly — a silent no-op in production.

```java
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableJpaAuditing
@EnableScheduling          // <-- add
public class ClinicBookingApplication {
```

```java
import org.springframework.scheduling.annotation.EnableScheduling;
```

## 3. `BookingService.java` — write the outbox row

Add two fields alongside the existing ones:

```java
    private final OutboxWriter outboxWriter;
    private final BookingMetrics metrics;
```

Imports:

```java
import lk.kavindu.clinic.notification.BookingEventPayload;
import lk.kavindu.clinic.observability.BookingMetrics;
import lk.kavindu.clinic.outbox.OutboxWriter;
```

### In `create(...)`

Right after `bookingRepository.save(...)` and before the `log.info` line:

```java
        outboxWriter.write("BOOKING", booking.getId(), "CONFIRMED",
                payloadFor(booking, patient, doctor));
        metrics.bookingCreated();
```

And in the slot-taken branch, before the throw:

```java
        if (bookingRepository.countActiveAtSlot(doctor.getId(), slotStart) > 0) {
            metrics.slotConflict();                    // <-- add
            throw ApiException.of(ErrorCode.SLOT_ALREADY_BOOKED, ...);
        }
```

### In `cancel(...)`

After `booking.setStatus(BookingStatus.CANCELLED);`:

```java
        outboxWriter.write("BOOKING", booking.getId(), "CANCELLED",
                payloadFor(booking, booking.getPatient(), booking.getDoctor()));
        metrics.bookingCancelled();
```

### In `reschedule(...)`

After `bookingRepository.save(...)` for the replacement booking:

```java
        outboxWriter.write("BOOKING", moved.getId(), "RESCHEDULED",
                payloadFor(moved, patient, doctor));
```

### Add this helper near `toResponse`

```java
    /**
     * Event eka SNAPSHOT ekak -- IDs withrak nemei, values-uth.
     * Doctor kenekge nama passe wenas unath, email eke thiyenna one
     * booking eka hædena welawe thibba nama.
     */
    private BookingEventPayload payloadFor(Booking booking, User patient, Doctor doctor) {
        return new BookingEventPayload(
                BookingEventPayload.CURRENT_VERSION,
                booking.getId(),
                patient.getFullName(),
                patient.getEmail(),
                doctor.getUser().getFullName(),
                doctor.getSpecialization(),
                doctor.getConsultationFee(),
                booking.getSlotStart(),
                booking.getSlotEnd(),
                clinic.timezone(),
                booking.getNotes());
    }
```

## 4. `DoctorService.java` — cache the two read-heavy queries

```java
import lk.kavindu.clinic.config.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
```

```java
    @Cacheable(cacheNames = CacheConfig.SPECIALIZATIONS)
    @Transactional(readOnly = true)
    public List<String> specializations() { ... }
```

```java
    @Cacheable(cacheNames = CacheConfig.DOCTORS, key = "#doctorId")
    @Transactional(readOnly = true)
    public DoctorResponse get(Long doctorId) { ... }
```

Then evict on every write. Add to `create`, `deactivate` and `replaceAvailability`:

```java
    @CacheEvict(cacheNames = {CacheConfig.DOCTORS, CacheConfig.SPECIALIZATIONS}, allEntries = true)
```

```java
import org.springframework.cache.annotation.CacheEvict;
```

**Do not cache `slotsForDate` or anything about bookings.** Slots change by the second;
a stale slot list means a patient picks a time that is already gone and gets a 409.
Caching is for data that changes on the order of hours, not seconds.

## 5. `docker-compose.yml` — nothing to change

Postgres, Redis and RabbitMQ are already there. The RabbitMQ management UI is at
http://localhost:15672 (clinic / clinic123) — after the first booking you can watch
`booking.notifications` fill up.

## 6. Run it

```
docker compose up -d
mvn verify          # expect 44 tests green
mvn spring-boot:run
```

Then in Swagger: log in as a patient, book a slot, and watch the app log. Within two
seconds the publisher picks up the outbox row and the consumer prints the notification
block. Stop RabbitMQ with `docker compose stop rabbitmq`, book again, and the booking
still succeeds while the row waits in `outbox_events`. Start it back up and the log
shows it drain.
