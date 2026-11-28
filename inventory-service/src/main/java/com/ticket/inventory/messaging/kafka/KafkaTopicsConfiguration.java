package com.ticket.inventory.messaging.kafka;

import com.ticket.events.Topics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration(proxyBeanMethods = false)
class KafkaTopicsConfiguration {

    private static final int PARTITIONS = 3;
    private static final int REPLICAS = 1;

    @Bean
    NewTopic inventoryEventsTopic() {
        return topic(Topics.INVENTORY_EVENTS);
    }

    @Bean
    NewTopic inventoryCommandsTopic() {
        return topic(Topics.INVENTORY_COMMANDS);
    }

    @Bean
    NewTopic inventoryCommandsDeadLetterTopic() {
        return topic(DeadLetterTopics.of(Topics.INVENTORY_COMMANDS));
    }

    private static NewTopic topic(String name) {
        return TopicBuilder.name(name).partitions(PARTITIONS).replicas(REPLICAS).build();
    }
}
