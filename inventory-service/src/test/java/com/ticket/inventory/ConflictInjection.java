package com.ticket.inventory;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
class ConflictInjection {

    @Bean
    static ConflictInjector conflictInjector() {
        return new ConflictInjector();
    }
}
