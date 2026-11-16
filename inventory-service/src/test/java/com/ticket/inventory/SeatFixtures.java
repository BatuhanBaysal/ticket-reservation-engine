package com.ticket.inventory;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

final class SeatFixtures {

    static final BigDecimal PRICE = new BigDecimal("49.9000");

    private SeatFixtures() {
    }

    static void insertAvailable(JdbcClient jdbc, String eventId, String seatId) {
        insert(jdbc, eventId, seatId, "AVAILABLE", null, null);
    }

    static void insertHeld(JdbcClient jdbc, String eventId, String seatId, UUID orderId, Instant expiresAt) {
        insert(jdbc, eventId, seatId, "HELD", orderId, expiresAt);
    }

    static void insertSold(JdbcClient jdbc, String eventId, String seatId, UUID orderId) {
        insert(jdbc, eventId, seatId, "SOLD", orderId, null);
    }

    private static void insert(
        JdbcClient jdbc, String eventId, String seatId, String status, UUID orderId, Instant expiresAt) {
        OffsetDateTime expiresAtUtc = expiresAt == null ? null : OffsetDateTime.ofInstant(expiresAt, ZoneOffset.UTC);
        jdbc.sql("""
                INSERT INTO seat_inventory (id, event_id, seat_id, status, held_by_order_id, hold_expires_at, price)
                VALUES (:id, :eventId, :seatId, :status, CAST(:orderId AS uuid), CAST(:expiresAt AS timestamptz), :price)
                """)
            .param("id", UUID.randomUUID())
            .param("eventId", eventId)
            .param("seatId", seatId)
            .param("status", status)
            .param("orderId", orderId)
            .param("expiresAt", expiresAtUtc)
            .param("price", PRICE)
            .update();
    }
}
