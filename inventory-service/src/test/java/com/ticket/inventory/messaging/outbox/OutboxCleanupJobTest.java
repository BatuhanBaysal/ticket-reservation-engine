package com.ticket.inventory.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

class OutboxCleanupJobTest {

    private final OutboxCleanup cleanup = mock(OutboxCleanup.class);
    private final OutboxCleanupJob job =
        new OutboxCleanupJob(cleanup, new OutboxCleanupProperties(true, Duration.ofHours(1), Duration.ofDays(7)));

    @Test
    void deletesOldPublishedRowsWhenItRuns() {
        job.run();

        verify(cleanup).deleteOldPublishedRows();
    }

    @Test
    void registersAFixedDelayTaskWithTheConfiguredInterval() {
        ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();

        job.configureTasks(registrar);

        assertThat(registrar.getFixedDelayTaskList()).singleElement()
            .satisfies(task -> assertThat(task.getIntervalDuration()).isEqualTo(Duration.ofHours(1)));
    }
}
