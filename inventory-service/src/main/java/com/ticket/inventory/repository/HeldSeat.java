package com.ticket.inventory.repository;

import java.math.BigDecimal;
import java.time.Instant;

public record HeldSeat(BigDecimal price, Instant holdExpiresAt) {
}
