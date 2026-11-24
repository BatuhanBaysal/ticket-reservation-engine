package com.ticket.inventory.messaging;

import com.ticket.events.MessageCodec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class MessagingConfiguration {

    @Bean
    MessageCodec messageCodec() {
        return new MessageCodec();
    }
}
