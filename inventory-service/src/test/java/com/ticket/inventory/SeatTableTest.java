package com.ticket.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ticket.inventory.domain.Seat;
import com.ticket.inventory.domain.SeatStatus;
import com.ticket.inventory.repository.SeatRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SeatTableTest {

    private static final String EVENT_ID = "evt-001";
    private static final String SEAT_ID = "A-12";
    private static final UUID ORDER_ID = UUID.fromString("5b2d7a40-91c8-4e6f-8d3a-2c4f1a9b7e55");
    private static final Instant HOLD_EXPIRES_AT = Instant.parse("2026-11-15T10:25:30.123456Z");
    private static final BigDecimal PRICE = new BigDecimal("49.9000");

    @Autowired
    private SeatRepository repository;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void emptyTheTable() {
        jdbc.sql("DELETE FROM seat_inventory").update();
    }

    static Stream<Arguments> inconsistentRows() {
        OffsetDateTime expiresAt = offset(HOLD_EXPIRES_AT);
        return Stream.of(
            row("AVAILABLE with an order", "AVAILABLE", ORDER_ID, null),
            row("AVAILABLE with an expiry", "AVAILABLE", null, expiresAt),
            row("HELD without an order", "HELD", null, expiresAt),
            row("HELD without an expiry", "HELD", ORDER_ID, null),
            row("SOLD without an order", "SOLD", null, null),
            row("SOLD with an expiry", "SOLD", ORDER_ID, expiresAt),
            row("an unknown status", "BLOCKED", null, null));
    }

    @Test
    void storesAnAvailableSeatAndReadsItBack() {
        repository.saveAndFlush(Seat.available(EVENT_ID, SEAT_ID, PRICE));

        Seat read = repository.findByEventIdAndSeatId(EVENT_ID, SEAT_ID).orElseThrow();

        assertThat(read.getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThat(read.getHeldByOrderId()).isNull();
        assertThat(read.getHoldExpiresAt()).isNull();
        assertThat(read.getPrice()).isEqualByComparingTo("49.9000");
        assertThat(read.getVersion()).isZero();
    }

    @Test
    void readsAHeldSeat() {
        insertSeat("HELD", ORDER_ID, offset(HOLD_EXPIRES_AT), PRICE);

        Seat read = repository.findByEventIdAndSeatId(EVENT_ID, SEAT_ID).orElseThrow();

        assertThat(read.getStatus()).isEqualTo(SeatStatus.HELD);
        assertThat(read.getHeldByOrderId()).isEqualTo(ORDER_ID);
        assertThat(read.getHoldExpiresAt()).isEqualTo(HOLD_EXPIRES_AT);
    }

    @Test
    void readsASoldSeat() {
        insertSeat("SOLD", ORDER_ID, null, PRICE);

        Seat read = repository.findByEventIdAndSeatId(EVENT_ID, SEAT_ID).orElseThrow();

        assertThat(read.getStatus()).isEqualTo(SeatStatus.SOLD);
        assertThat(read.getHeldByOrderId()).isEqualTo(ORDER_ID);
        assertThat(read.getHoldExpiresAt()).isNull();
    }

    @Test
    void rejectsASecondSeatWithTheSameEventAndSeat() {
        insertSeat("AVAILABLE", null, null, PRICE);

        assertThatThrownBy(() -> insertSeat("AVAILABLE", null, null, PRICE))
            .isInstanceOf(DuplicateKeyException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "-0.0001"})
    void rejectsAPriceOfZeroOrLess(String value) {
        BigDecimal price = new BigDecimal(value);

        assertThatThrownBy(() -> insertSeat("AVAILABLE", null, null, price))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("inconsistentRows")
    void rejectsAStatusThatDoesNotMatchTheOrderAndExpiryFields(
        String status, UUID orderId, OffsetDateTime expiresAt) {
        assertThatThrownBy(() -> insertSeat(status, orderId, expiresAt, PRICE))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void createsThePartialExpiryIndex() {
        String definition = jdbc.sql("""
                SELECT indexdef FROM pg_indexes
                WHERE tablename = 'seat_inventory' AND indexname = 'idx_seat_inventory_held_expiry'
                """)
            .query(String.class)
            .single();

        assertThat(definition).contains("(hold_expires_at)").contains("HELD");
    }

    @Test
    void versionChangesWhenTheEntityIsUpdated() {
        Seat saved = repository.saveAndFlush(Seat.available(EVENT_ID, SEAT_ID, PRICE));
        saved.reprice(new BigDecimal("59.9000"));

        Seat updated = repository.saveAndFlush(saved);

        Long stored = jdbc.sql("SELECT version FROM seat_inventory WHERE event_id = :eventId AND seat_id = :seatId")
            .param("eventId", EVENT_ID)
            .param("seatId", SEAT_ID)
            .query(Long.class)
            .single();
        assertThat(updated.getVersion()).isEqualTo(1L);
        assertThat(stored).isEqualTo(1L);
    }

    private void insertSeat(String status, UUID orderId, OffsetDateTime expiresAt, BigDecimal price) {
        jdbc.sql("""
                INSERT INTO seat_inventory (id, event_id, seat_id, status, held_by_order_id, hold_expires_at, price)
                VALUES (:id, :eventId, :seatId, :status, CAST(:orderId AS uuid), CAST(:expiresAt AS timestamptz), :price)
                """)
            .param("id", UUID.randomUUID())
            .param("eventId", EVENT_ID)
            .param("seatId", SEAT_ID)
            .param("status", status)
            .param("orderId", orderId)
            .param("expiresAt", expiresAt)
            .param("price", price)
            .update();
    }

    private static Arguments row(String name, String status, UUID orderId, OffsetDateTime expiresAt) {
        return Arguments.of(Named.of(name, status), orderId, expiresAt);
    }

    private static OffsetDateTime offset(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
