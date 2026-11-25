package com.ticket.inventory.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

class OutboxPollerJobTest {

    private final OutboxPoller poller = mock(OutboxPoller.class);
    private final OutboxPollerJob job =
        new OutboxPollerJob(poller, new OutboxPollerProperties(true, Duration.ofMillis(500), Duration.ofSeconds(10)));

    @Test
    void publishesABatchWhenItRuns() {
        job.run();

        verify(poller).publishBatch();
    }

    @Test
    void registersAFixedDelayTaskWithTheConfiguredInterval() {
        ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();

        job.configureTasks(registrar);

        assertThat(registrar.getFixedDelayTaskList()).singleElement()
            .satisfies(task -> assertThat(task.getIntervalDuration()).isEqualTo(Duration.ofMillis(500)));
    }
}
