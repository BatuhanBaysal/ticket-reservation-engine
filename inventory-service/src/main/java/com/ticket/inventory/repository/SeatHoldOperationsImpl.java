package com.ticket.inventory.repository;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

class SeatHoldOperationsImpl implements SeatHoldOperations {

    private static final String HOLD_IF_AVAILABLE = """
        UPDATE seat_inventory
        SET status = 'HELD',
            held_by_order_id = :orderId,
            hold_expires_at = now() + make_interval(secs => :holdSeconds),
            version = version + 1
        WHERE event_id = :eventId AND seat_id = :seatId
          AND (status = 'AVAILABLE' OR (status = 'HELD' AND hold_expires_at < now()))
        RETURNING price, hold_expires_at
        """;

    private final JdbcClient jdbc;

    SeatHoldOperationsImpl(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<HeldSeat> holdIfAvailable(UUID orderId, String eventId, String seatId, Duration holdDuration) {
        return jdbc.sql(HOLD_IF_AVAILABLE)
            .param("orderId", orderId)
            .param("eventId", eventId)
            .param("seatId", seatId)
            .param("holdSeconds", holdDuration.toMillis() / 1000.0)
            .query((resultSet, rowNumber) -> new HeldSeat(
                resultSet.getBigDecimal("price"),
                resultSet.getObject("hold_expires_at", OffsetDateTime.class).toInstant()))
            .optional();
    }
}
