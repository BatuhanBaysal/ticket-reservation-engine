package com.ticket.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.ticket.inventory.domain.SeatStatus;
import com.ticket.inventory.repository.SeatRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"inventory.hold.expiry.enabled=true", "inventory.hold.expiry.interval=200ms"})
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class HoldExpiryJobScheduleTest {

    private static final String EVENT_ID = "evt-001";
    private static final String SEAT_ID = "A-12";
    private static final UUID ORDER_ID = UUID.fromString("5b2d7a40-91c8-4e6f-8d3a-2c4f1a9b7e55");

    @Autowired
    private SeatRepository repository;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void emptyTheTable() {
        jdbc.sql("DELETE FROM seat_inventory").update();
    }

    @Test
    void expiresAnOverdueHoldOnItsOwn() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, ORDER_ID, Instant.now().minus(Duration.ofMinutes(1)));

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
            assertThat(repository.findByEventIdAndSeatId(EVENT_ID, SEAT_ID).orElseThrow().getStatus())
                .isEqualTo(SeatStatus.AVAILABLE));
    }
}
