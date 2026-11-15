package com.ticket.inventory.domain;

import com.ticket.events.Require;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "seat_inventory")
public class Seat {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "seat_id", nullable = false)
    private String seatId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatStatus status;

    @Column(name = "held_by_order_id")
    private UUID heldByOrderId;

    @Column(name = "hold_expires_at")
    private Instant holdExpiresAt;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @Version
    private Long version;

    protected Seat() {
    }

    private Seat(String eventId, String seatId, BigDecimal price) {
        this.id = UUID.randomUUID();
        this.eventId = Require.notBlank(eventId, "eventId");
        this.seatId = Require.notBlank(seatId, "seatId");
        this.status = SeatStatus.AVAILABLE;
        this.price = Require.positive(price, "price");
    }

    public static Seat available(String eventId, String seatId, BigDecimal price) {
        return new Seat(eventId, seatId, price);
    }

    public void reprice(BigDecimal newPrice) {
        this.price = Require.positive(newPrice, "price");
    }

    public UUID getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getSeatId() {
        return seatId;
    }

    public SeatStatus getStatus() {
        return status;
    }

    public UUID getHeldByOrderId() {
        return heldByOrderId;
    }

    public Instant getHoldExpiresAt() {
        return holdExpiresAt;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Long getVersion() {
        return version;
    }
}
