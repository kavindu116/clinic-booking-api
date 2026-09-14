package lk.kavindu.clinic.notification;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NotificationMessaging {
    public static final String EXCHANGE = "booking.events";
    public static final String QUEUE = "booking.notifications";
    public static final String DLO_EXCHANGE = "booking.events.dlx";
    public static final String DLQ = "booking.notifications.dlq";

    @Bean
    TopicExchange bookingExchange() {
        return ExchangeBuilder.topicExchange(EXCHANGE).durable(true).build();
    }

    @Bean
    DirectExchange bookingDirectLetterExchange() {
        return ExchangeBuilder.directExchange(DLO_EXCHANGE).durable(true).build();
    }

    @Bean
    Queue bookingNotificationQueue(){
        return QueueBuilder.durable(QUEUE)
                .deadLetterExchange(DLO_EXCHANGE)
                .deadLetterRoutingKey(DLQ)
                .build();
    }

    @Bean
    Queue bookingNotificationDlq(){
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    Binding bindingConfirmed(Queue bookingNotificationQueue, TopicExchange bookingExchange) {
        return BindingBuilder.bind(bookingNotificationQueue)
                .to(bookingExchange).with("booking.*");
    }

    @Bean
    Binding bindDlq(Queue bookingNotificationDlq,DirectExchange bookingDeadLetterExchange) {
        return BindingBuilder.bind(bookingNotificationDlq)
                .to(bookingDeadLetterExchange).with(DLQ);
    }
}
