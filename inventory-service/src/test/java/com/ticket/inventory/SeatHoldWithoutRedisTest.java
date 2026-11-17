package com.ticket.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeout;

import com.ticket.events.inventory.SeatHoldRejected;
import com.ticket.inventory.service.HoldResult;
import com.ticket.inventory.service.SeatService;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "inventory.test.redis=stopped")
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SeatHoldWithoutRedisTest {

    private static final String EVENT_ID = "evt-001";
    private static final String SEAT_ID = "A-12";
    private static final UUID ORDER_ID = UUID.fromString("5b2d7a40-91c8-4e6f-8d3a-2c4f1a9b7e55");
    private static final UUID OTHER_ORDER_ID = UUID.fromString("c3e1f6a8-2b94-4d57-9a0e-6f8d1b7c4e23");
    private static final Duration LIMIT = Duration.ofSeconds(5);

    @Autowired
    private SeatService service;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    @Qualifier("redisContainer")
    private GenericContainer<?> redisContainer;

    @BeforeEach
    void stopRedisAndEmptyTheTable() {
        redisContainer.stop();
        jdbc.sql("DELETE FROM seat_inventory").update();
    }

    @Test
    void holdsASeatThroughTheDatabase() {
        SeatFixtures.insertAvailable(jdbc, EVENT_ID, SEAT_ID);

        HoldResult result = assertTimeout(LIMIT, () -> service.hold(ORDER_ID, EVENT_ID, SEAT_ID));

        assertThat(result).isInstanceOf(HoldResult.Held.class);
    }

    @Test
    void rejectsASeatHeldByAnotherOrderThroughTheDatabase() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, OTHER_ORDER_ID, Instant.now().plus(Duration.ofMinutes(5)));

        HoldResult result = assertTimeout(LIMIT, () -> service.hold(ORDER_ID, EVENT_ID, SEAT_ID));

        assertThat(result).isEqualTo(new HoldResult.Rejected(SeatHoldRejected.SEAT_UNAVAILABLE));
    }
}
