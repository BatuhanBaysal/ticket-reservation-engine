package com.ticket.inventory.messaging.kafka;

import com.ticket.events.InvalidMessageException;
import com.ticket.events.UnknownMessageTypeException;
import com.ticket.inventory.service.UnsupportedMessageException;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration(proxyBeanMethods = false)
class KafkaConsumerConfiguration {

    private static final long RETRIES = 3;

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> template, ConsumerProperties properties) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
            (record, exception) -> new TopicPartition(DeadLetterTopics.of(record.topic()), record.partition()));
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer,
            new FixedBackOff(properties.retryInterval().toMillis(), RETRIES));
        handler.addNotRetryableExceptions(
            UnknownMessageTypeException.class, InvalidMessageException.class, UnsupportedMessageException.class);
        handler.setCommitRecovered(true);
        return handler;
    }
}
