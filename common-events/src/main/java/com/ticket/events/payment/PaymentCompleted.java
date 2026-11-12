package com.ticket.events.payment;

import com.ticket.events.Require;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentCompleted(UUID paymentRequestId, UUID orderId, BigDecimal amount) {

    public PaymentCompleted {
        Require.notNull(paymentRequestId, "paymentRequestId");
        Require.notNull(orderId, "orderId");
        Require.positive(amount, "amount");
    }
}
