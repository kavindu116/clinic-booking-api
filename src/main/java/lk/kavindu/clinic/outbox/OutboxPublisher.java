package lk.kavindu.clinic.outbox;

import lk.kavindu.clinic.notification.NotificationMessaging;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.outbox.enabled",havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {
    private final OutboxRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;
    private final OutboxProperties outboxProperties;

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:2000}")
    @Transactional
    public void publishPending(){
        List<OutboxEvent> batch = outboxRepository.lockPendingBatch(outboxProperties.batchSize());
        if(batch.isEmpty()){
            return;
        }
        log.debug("Outbox : publishing {} event(s)", batch.size());

        for (OutboxEvent event : batch) {
            try {
                rabbitTemplate.convertAndSend(
                        NotificationMessaging.EXCHANGE,
                        routingKeyFor(event),
                        event.getPayload(),
                        message -> {
                            message.getMessageProperties()
                                    .setMessageId(String.valueOf(event.getId()));
                            message.getMessageProperties()
                                    .setContentType("application/json");
                            message.getMessageProperties()
                                    .setHeader("eventType", event.getEventType());
                            return message;
                        }
                );

                event.markPublished();
                log.info("Outbox published : id={} type={}", event.getId(), event.getEventType());
            }catch (AmqpException e){
                event.markAttemptFailed(e.getMessage(), outboxProperties.maxAttempts());

                if (event.getStatus() ==OutboxStatus.FAILED){
                    log.error("Outbox give up after {} attempts: id={} type={}",
                            outboxProperties.maxAttempts(), event.getId(), event.getEventType(),e);
                }else {
                    log.warn("Outbox publish faild (attempt {}), will retry: id={}",
                            outboxProperties.maxAttempts(), event.getId());
                }
            }
        }
    }

    private String routingKeyFor(OutboxEvent event){
        return event.getAggregateType().toLowerCase() +"."+ event.getEventType().toLowerCase();
    }

    @Scheduled(cron = "${app.outbox.cleanup-cron:0 15 3 * * *}")
    @Transactional
    public void purgePublished(){
        int removed = outboxRepository.deletePublishedBefore(
                Instant.now().minus(
                        Duration.ofDays(outboxProperties.retainPublishedDays())
                )
        );

        if (removed > 0){
            log.info("Outbox cleanup : removed {} published event(s)", removed);
        }
    }

}
