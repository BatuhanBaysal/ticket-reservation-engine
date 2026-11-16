package com.ticket.inventory.service;

import com.ticket.events.Require;
import java.math.BigDecimal;
import java.time.Instant;

public sealed interface HoldResult {

    record Held(BigDecimal price, Instant holdExpiresAt) implements HoldResult {

        public Held {
            Require.positive(price, "price");
            Require.notNull(holdExpiresAt, "holdExpiresAt");
        }
    }

    record Rejected(String reason) implements HoldResult {

        public Rejected {
            Require.notBlank(reason, "reason");
        }
    }
}
