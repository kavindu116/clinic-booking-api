package lk.kavindu.clinic.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxWriter {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public void write(String aggregateType, Long aggregateId, String eventType, Object payload) {
        String json;

        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Could not serialise outbox payload for " + eventType, e
            );
        }

        outboxRepository.save(OutboxEvent.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .payload(json)
                .status(OutboxStatus.PENDING)
                .build());

        log.debug("Outbox roe written: {} for {} {}",eventType,aggregateType,aggregateId);
    }

}
