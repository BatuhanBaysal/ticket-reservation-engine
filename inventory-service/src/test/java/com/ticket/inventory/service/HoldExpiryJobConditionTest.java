package com.ticket.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class HoldExpiryJobConditionTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(HoldExpiryJob.class)
        .withBean(SeatService.class, () -> mock(SeatService.class))
        .withBean(ExpiryProperties.class, () -> new ExpiryProperties(true, Duration.ofSeconds(30)));

    @Test
    void createsTheJobByDefault() {
        runner.run(context -> assertThat(context).hasSingleBean(HoldExpiryJob.class));
    }

    @Test
    void createsNoJobWhenTheSettingIsFalse() {
        runner.withPropertyValues("inventory.hold.expiry.enabled=false")
            .run(context -> assertThat(context).doesNotHaveBean(HoldExpiryJob.class));
    }
}
