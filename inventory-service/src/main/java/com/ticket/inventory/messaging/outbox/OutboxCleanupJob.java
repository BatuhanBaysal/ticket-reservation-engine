package com.ticket.inventory.messaging.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "inventory.outbox.cleanup.enabled", havingValue = "true", matchIfMissing = true)
class OutboxCleanupJob implements SchedulingConfigurer {

    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxCleanupJob.class);

    private final OutboxCleanup cleanup;
    private final OutboxCleanupProperties properties;

    OutboxCleanupJob(OutboxCleanup cleanup, OutboxCleanupProperties properties) {
        this.cleanup = cleanup;
        this.properties = properties;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(this::run, properties.interval());
    }

    void run() {
        int deleted = cleanup.deleteOldPublishedRows();
        if (deleted > 0) {
            LOGGER.info("Deleted {} published outbox row(s)", deleted);
        }
    }
}
