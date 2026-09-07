package lk.kavindu.clinic.outbox;

import lk.kavindu.clinic.booking.BookingService;
import lk.kavindu.clinic.booking.dto.CreateBookingRequest;
import lk.kavindu.clinic.doctor.Doctor;
import lk.kavindu.clinic.notification.dto.BookingEventPayload;
import lk.kavindu.clinic.security.AppUserPrincipal;
import lk.kavindu.clinic.support.AbstractIntegrationTest;
import lk.kavindu.clinic.support.TestDataFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.fasterxml.jackson.databind.ObjectMapper;


import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class OutboxIT extends AbstractIntegrationTest {

    private static final ZoneId COLOMBO = ZoneId.of("Asia/Colombo");

    @Autowired
    private BookingService bookingService;
    @Autowired
    private OutboxRepository outboxRepository;
    @Autowired
    private OutboxPublisher outboxPublisher;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TestDataFactory data;
    @MockitoBean
    private RabbitTemplate rabbitTemplate;

    @Test
    @DisplayName("a confirmed writes exatly one pending outbox event")
    void bookingWritesOutboxEvent() throws Exception{
        Doctor doctor = data.doctorWithWeekdayHours("outbox.doctor@clinic.lk");
        var patient = new AppUserPrincipal(data.patient("outbox.patient@example.com"));
        Instant slot = nextMondayAt(10,0);

        var booking = bookingService.create(patient,
                new CreateBookingRequest(doctor.getId(),slot,"Check-Up"));

        List<OutboxEvent> events = outboxRepository
                .findByAggregateTypeAndAggregateIdOrderByCreatedAtAsc("BOOKING",booking.id());

        assertThat(events).hasSize(1);

        OutboxEvent event = events.getFirst();
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getEventType()).isEqualTo("CONFIRMED");
        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getAttempts()).isZero();

        BookingEventPayload payload =
                objectMapper.readValue(event.getPayload(), BookingEventPayload.class);
        assertThat(payload.bookingId()).isEqualTo(booking.id());
        assertThat(payload.patientEmail()).isEqualTo("outbox.patient@example.com");
        assertThat(payload.doctorName()).isEqualTo(doctor.getUser().getFullName());
        assertThat(payload.slotStart()).isEqualTo(slot);
        assertThat(payload.clinicTimeZone()).isEqualTo("Asia/Colombo");

    }

    @Test
    @DisplayName("a rejected booking leaves no outbox event behind")
    void rejectedBookingWritesNothing() {
        Doctor doctor = data.doctorWithWeekdayHours("rollback.doctor@clinic.lk");
        var patient = new AppUserPrincipal(data.patient("rollback.patient@example.com"));

        // 10:15 -- slot grid eke naa, ee nisa reject wenawa
        assertThatThrownBy(() -> bookingService.create(patient,
                new CreateBookingRequest(doctor.getId(), nextMondayAt(10, 15), null)))
                .isInstanceOf(RuntimeException.class);

        assertThat(outboxRepository.count())
                .as("a failed booking must not leave an orphan event")
                .isZero();
    }

    @Test
    @DisplayName("cancelling writes a second event, so the outbox is an audit trail")
    void cancellationWritesItsOwnEvent() {
        Doctor doctor = data.doctorWithWeekdayHours("cancel.doctor@clinic.lk");
        var patient = new AppUserPrincipal(data.patient("cancel.patient@example.com"));

        var booking = bookingService.create(patient,
                new CreateBookingRequest(doctor.getId(), nextMondayAt(11, 0), null));
        bookingService.cancel(patient, booking.id());

        List<OutboxEvent> events = outboxRepository
                .findByAggregateTypeAndAggregateIdOrderByCreatedAtAsc("BOOKING", booking.id());

        assertThat(events).hasSize(2);
        assertThat(events).extracting(OutboxEvent::getEventType)
                .containsExactly("CONFIRMED", "CANCELLED");
    }

    @Test
    @DisplayName("the publisher marks events published once the broker accepts them")
    void publisherMarksEventsPublished() {
        Doctor doctor = data.doctorWithWeekdayHours("publish.doctor@clinic.lk");
        var patient = new AppUserPrincipal(data.patient("publish.patient@example.com"));

        bookingService.create(patient,
                new CreateBookingRequest(doctor.getId(), nextMondayAt(9, 0), null));

        assertThat(outboxRepository.countByStatus(OutboxStatus.PENDING)).isEqualTo(1);

        outboxPublisher.publishPending();

        assertThat(outboxRepository.countByStatus(OutboxStatus.PENDING)).isZero();
        assertThat(outboxRepository.countByStatus(OutboxStatus.PUBLISHED)).isEqualTo(1);

        verify(rabbitTemplate).convertAndSend(
                eq("booking.events"), eq("booking.confirmed"),
                anyString(), any(MessagePostProcessor.class));
    }

    @Test
    @DisplayName("⭐ when the broker is down the booking still succeeds and the event waits")
    void brokerOutageDoesNotLoseTheBooking() {
        doThrow(new AmqpConnectException(new RuntimeException("connection refused")))
                .when(rabbitTemplate).convertAndSend(
                        anyString(), anyString(), anyString(), any(MessagePostProcessor.class));

        Doctor doctor = data.doctorWithWeekdayHours("outage.doctor@clinic.lk");
        var patient = new AppUserPrincipal(data.patient("outage.patient@example.com"));

        // Booking eka HARIYATA wenawa -- broker eka down unath
        var booking = bookingService.create(patient,
                new CreateBookingRequest(doctor.getId(), nextMondayAt(12, 0), null));
        assertThat(booking.id()).isNotNull();

        outboxPublisher.publishPending();

        // Event eka naethi wela naa -- retry ekakata balan innawa
        List<OutboxEvent> events = outboxRepository
                .findByAggregateTypeAndAggregateIdOrderByCreatedAtAsc("BOOKING", booking.id());
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(events.getFirst().getAttempts()).isEqualTo((short) 1);
        assertThat(events.getFirst().getLastError()).contains("connection refused");

        // Broker eka aayemath awa -- dæn yanawa
        reset(rabbitTemplate);
        outboxPublisher.publishPending();

        assertThat(outboxRepository.countByStatus(OutboxStatus.PUBLISHED)).isEqualTo(1);
    }

    @Test
    @DisplayName("an event gives up after the configured number of attempts")
    void eventStopsRetryingEventually() {
        doThrow(new AmqpConnectException(new RuntimeException("still down")))
                .when(rabbitTemplate).convertAndSend(
                        anyString(), anyString(), anyString(), any(MessagePostProcessor.class));

        Doctor doctor = data.doctorWithWeekdayHours("giveup.doctor@clinic.lk");
        var patient = new AppUserPrincipal(data.patient("giveup.patient@example.com"));

        bookingService.create(patient,
                new CreateBookingRequest(doctor.getId(), nextMondayAt(12, 30), null));

        // max-attempts eka application-test.yml eke 3ak
        for (int i = 0; i < 3; i++) {
            outboxPublisher.publishPending();
        }

        assertThat(outboxRepository.countByStatus(OutboxStatus.FAILED))
                .as("a permanently broken event must not be retried forever")
                .isEqualTo(1);
        assertThat(outboxRepository.countByStatus(OutboxStatus.PENDING)).isZero();
    }


    private Instant nextMondayAt(int hour, int minute){
        LocalDate monday = LocalDate.now(COLOMBO).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        if (Duration.between(Instant.now(),
                monday.atTime(hour,minute).atZone(COLOMBO).toInstant()).toHours() < 2){
            monday = monday.plusWeeks(1);
        }

        return monday.atTime(hour,minute).atZone(COLOMBO).toInstant();
    }
}
