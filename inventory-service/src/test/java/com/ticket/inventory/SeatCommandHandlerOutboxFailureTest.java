package com.ticket.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import com.ticket.events.MessageEnvelope;
import com.ticket.events.MessageTypes;
import com.ticket.events.inventory.ConfirmSeatCommand;
import com.ticket.events.inventory.HoldSeatCommand;
import com.ticket.inventory.domain.Seat;
import com.ticket.inventory.domain.SeatStatus;
import com.ticket.inventory.messaging.outbox.OutboxWriter;
import com.ticket.inventory.repository.SeatRepository;
import com.ticket.inventory.service.SeatCommandHandler;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SeatCommandHandlerOutboxFailureTest {

    private static final String EVENT_ID = "evt-001";
    private static final String SEAT_ID = "A-12";
    private static final UUID ORDER_ID = UUID.fromString("5b2d7a40-91c8-4e6f-8d3a-2c4f1a9b7e55");
    private static final String KEY = RedisFixtures.holdKey(EVENT_ID, SEAT_ID);

    @MockitoBean
    private OutboxWriter outbox;

    @Autowired
    private SeatCommandHandler handler;

    @Autowired
    private SeatRepository repository;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void emptyTheTablesAndBreakTheWriter() {
        jdbc.sql("DELETE FROM seat_inventory").update();
        jdbc.sql("DELETE FROM processed_message").update();
        RedisFixtures.clearHoldKeys(redis);
        doThrow(new IllegalStateException("boom")).when(outbox).write(any(), any(), any());
    }

    @Test
    void throwsTheFailureOfTheOutboxWrite() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, ORDER_ID, inFiveMinutes());
        MessageEnvelope<Object> message = confirmCommand();

        assertThatThrownBy(() -> handler.handle(message))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("boom");
    }

    @Test
    void rollsTheSeatAndTheInboxRowBackWhenTheOutboxWriteFails() {
        SeatFixtures.insertHeld(jdbc, EVENT_ID, SEAT_ID, ORDER_ID, inFiveMinutes());
        MessageEnvelope<Object> message = confirmCommand();
        catchThrowable(() -> handler.handle(message));

        Seat seat = repository.findByEventIdAndSeatId(EVENT_ID, SEAT_ID).orElseThrow();
        Long inboxRows = jdbc.sql("SELECT count(*) FROM processed_message").query(Long.class).single();

        assertSoftly(softly -> {
            softly.assertThat(seat.getStatus()).isEqualTo(SeatStatus.HELD);
            softly.assertThat(seat.getVersion()).isZero();
            softly.assertThat(inboxRows).isZero();
        });
    }

    @Test
    void releasesTheGateKeyWhenAHoldFails() {
        SeatFixtures.insertAvailable(jdbc, EVENT_ID, SEAT_ID);
        MessageEnvelope<Object> message = holdCommand();
        catchThrowable(() -> handler.handle(message));

        Boolean keyExists = redis.hasKey(KEY);

        assertThat(keyExists).isFalse();
    }

    private static MessageEnvelope<Object> confirmCommand() {
        return new MessageEnvelope<>(UUID.randomUUID(), "c-test", MessageTypes.CONFIRM_SEAT_COMMAND,
            Instant.parse("2026-11-27T10:15:30.123Z"), new ConfirmSeatCommand(ORDER_ID, EVENT_ID, SEAT_ID));
    }

    private static MessageEnvelope<Object> holdCommand() {
        return new MessageEnvelope<>(UUID.randomUUID(), "c-test", MessageTypes.HOLD_SEAT_COMMAND,
            Instant.parse("2026-11-27T10:15:30.123Z"), new HoldSeatCommand(ORDER_ID, EVENT_ID, SEAT_ID));
    }

    private static Instant inFiveMinutes() {
        return Instant.now().plus(Duration.ofMinutes(5));
    }
}
