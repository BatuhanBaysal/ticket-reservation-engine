package com.ticket.inventory.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class OutboxJobsConditionTest {

    private final ApplicationContextRunner pollerRunner = new ApplicationContextRunner()
        .withUserConfiguration(OutboxPollerJob.class)
        .withBean(OutboxPoller.class, () -> mock(OutboxPoller.class))
        .withBean(OutboxPollerProperties.class,
            () -> new OutboxPollerProperties(true, Duration.ofMillis(500), Duration.ofSeconds(10)));

    private final ApplicationContextRunner cleanupRunner = new ApplicationContextRunner()
        .withUserConfiguration(OutboxCleanupJob.class)
        .withBean(OutboxCleanup.class, () -> mock(OutboxCleanup.class))
        .withBean(OutboxCleanupProperties.class,
            () -> new OutboxCleanupProperties(true, Duration.ofHours(1), Duration.ofDays(7)));

    @Test
    void createsThePollerJobByDefault() {
        pollerRunner.run(context -> assertThat(context).hasSingleBean(OutboxPollerJob.class));
    }

    @Test
    void createsNoPollerJobWhenTheSettingIsFalse() {
        pollerRunner.withPropertyValues("inventory.outbox.poller.enabled=false")
            .run(context -> assertThat(context).doesNotHaveBean(OutboxPollerJob.class));
    }

    @Test
    void createsTheCleanupJobByDefault() {
        cleanupRunner.run(context -> assertThat(context).hasSingleBean(OutboxCleanupJob.class));
    }

    @Test
    void createsNoCleanupJobWhenTheSettingIsFalse() {
        cleanupRunner.withPropertyValues("inventory.outbox.cleanup.enabled=false")
            .run(context -> assertThat(context).doesNotHaveBean(OutboxCleanupJob.class));
    }
}
