package com.ticket.inventory.messaging.outbox;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "inventory.outbox.poller.enabled", havingValue = "true", matchIfMissing = true)
class OutboxPollerJob implements SchedulingConfigurer {

    private final OutboxPoller poller;
    private final OutboxPollerProperties properties;

    OutboxPollerJob(OutboxPoller poller, OutboxPollerProperties properties) {
        this.poller = poller;
        this.properties = properties;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(this::run, properties.interval());
    }

    void run() {
        poller.publishBatch();
    }
}
