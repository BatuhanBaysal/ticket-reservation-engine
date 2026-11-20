package com.ticket.inventory;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
class HoldUpdateCounting {

    @Bean
    static HoldUpdateCounter holdUpdateCounter() {
        return new HoldUpdateCounter();
    }
}
