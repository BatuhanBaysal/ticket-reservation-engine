package com.ticket.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.ticket.inventory.domain.SeatStatus;
import com.ticket.inventory.messaging.outbox.OutboxWriter;
import com.ticket.inventory.repository.SeatRepository;
import com.ticket.inventory.service.SeatService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SeatHoldExpiryOutboxFailureTest {

    private static final String EVENT_ID = "evt-001";
    private static final String SEAT_ID = "A-12";
    private static final UUID ORDER_ID = UUID.fromString("5b2d7a40-91c8-4e6f-8d3a-2c4f1a9b7e55");

    @MockitoBean
    private OutboxWriter outbox;

    @Autowired
    private SeatService service;

    @Autowired
    private SeatRepository repository;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void emptyTheTableAndBreakTheWriter() {
        jdbc.sql("DELETE FROM seat_inventory").update();
        doThrow(new IllegalStateException("boom")).when(outbox).write(any(), any(), any());
    }

    @Test
    void throwsTheFailureOfTheOutboxWrite() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, ORDER_ID, overdue());

        assertThatThrownBy(service::expireOverdueHolds)
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("boom");
    }

    @Test
    void keepsTheSeatHeldWhenTheOutboxWriteFails() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, ORDER_ID, overdue());
        catchThrowable(service::expireOverdueHolds);

        SeatStatus status = repository.findByEventIdAndSeatId(EVENT_ID, SEAT_ID).orElseThrow().getStatus();

        assertThat(status).isEqualTo(SeatStatus.HELD);
    }

    private static Instant overdue() {
        return Instant.now().minus(Duration.ofMinutes(1));
    }
}
