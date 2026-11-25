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
        return TopicBuilder.name(Topics.INVENTORY_EVENTS).partitions(PARTITIONS).replicas(REPLICAS).build();
    }
}
