package lk.kavindu.clinic.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import lk.kavindu.clinic.notification.dto.BookingEventPayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.notifications.eanabled",havingValue = "true", matchIfMissing = true)
public class NotificationConsumer {

    private static final int SEEN_CACHE_SIZE = 10_000;
    private final ObjectMapper objectMapper;

    private final Set<String> processed = Collections.synchronizedSet(
            Collections.newSetFromMap(new java.util.LinkedHashMap<>() {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, Boolean> eldest) {
                    return size() > SEEN_CACHE_SIZE;
                }
            }));

    @RabbitListener(queues = NotificationMessaging.QUEUE)
    public void onBookingEvent(String payload,
                               @Header(AmqpHeaders.MESSAGE_ID) String messageId,
                               @Header(name = "eventType",required = false) String eventType){
        if (!processed.add(messageId)){
            log.debug("Duplicate Message ignored: Id={}",messageId);
            return;
        }

        try {
            BookingEventPayload event = objectMapper.readValue(payload,BookingEventPayload.class);
            deliver(eventType,event);
        }catch (Exception e){
            processed.remove(messageId);
            log.error("Could not process notification : id={} type={}",messageId,eventType,e);
            throw new AmqpRejectAndDontRequeueException(
                    "Unprocessable notification"+ messageId,e
            );
        }

    }

    private void deliver(String eventType,BookingEventPayload event){
        ZoneId zone = ZoneId.of(event.clinicTimeZone());
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("EEEE,d MMMM yyyy 'at' h:mm a")
                .withZone(zone);

        String subject = switch (eventType == null ? "" : eventType){
            case "CONFIRMED" ->"Your appointment is confirmed";
            case "CANCELLED" ->"Your appointment is cancelled";
            case "RESCHEDULED" -> "Your appointment is moved";
            default -> "Update your appointment";
        };

        log.info("""

                --------------- NOTIFICATION ---------------
                To      : {} <{}>
                Subject : {}
                Doctor  : {} ({})
                When    : {}
                Fee     : LKR {}
                Booking : #{}
                --------------------------------------------""",
                event.patientName(), event.patientEmail(),
                subject,
                event.doctorName(), event.specialization(),
                fmt.format(event.slotStart()),
                event.consultationFee(),
                event.bookingId());
    }
    }


