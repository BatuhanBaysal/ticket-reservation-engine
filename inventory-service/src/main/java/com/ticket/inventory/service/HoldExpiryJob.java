package com.ticket.inventory.service;

import com.ticket.inventory.repository.ExpiredHold;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "inventory.hold.expiry.enabled", havingValue = "true", matchIfMissing = true)
class HoldExpiryJob implements SchedulingConfigurer {

    private static final Logger LOGGER = LoggerFactory.getLogger(HoldExpiryJob.class);

    private final SeatService seatService;
    private final ExpiryProperties properties;

    HoldExpiryJob(SeatService seatService, ExpiryProperties properties) {
        this.seatService = seatService;
        this.properties = properties;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(this::run, properties.interval());
    }

    void run() {
        List<ExpiredHold> expired = seatService.expireOverdueHolds();
        if (!expired.isEmpty()) {
            LOGGER.info("Expired {} overdue hold(s)", expired.size());
        }
    }
}
