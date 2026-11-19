package com.ticket.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ticket.inventory.repository.ExpiredHold;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

class HoldExpiryJobTest {

    private final SeatService service = mock(SeatService.class);
    private final HoldExpiryJob job = new HoldExpiryJob(service, new ExpiryProperties(true, Duration.ofSeconds(30)));

    @Test
    void expiresOverdueHoldsWhenItRuns() {
        UUID orderId = UUID.fromString("5b2d7a40-91c8-4e6f-8d3a-2c4f1a9b7e55");
        when(service.expireOverdueHolds()).thenReturn(List.of(new ExpiredHold(orderId, "evt-001", "A-12")));

        job.run();

        verify(service).expireOverdueHolds();
    }

    @Test
    void registersAFixedDelayTaskWithTheConfiguredInterval() {
        ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();

        job.configureTasks(registrar);

        assertThat(registrar.getFixedDelayTaskList()).singleElement()
            .satisfies(task -> assertThat(task.getIntervalDuration()).isEqualTo(Duration.ofSeconds(30)));
    }
}
