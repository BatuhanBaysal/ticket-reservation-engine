package com.ticket.events.payment;

import com.ticket.events.Require;

import java.math.BigDecimal;
import java.util.UUID;

public record ProcessPaymentCommand(UUID paymentRequestId, UUID orderId, BigDecimal amount) {

    public ProcessPaymentCommand {
        Require.notNull(paymentRequestId, "paymentRequestId");
        Require.notNull(orderId, "orderId");
        Require.positive(amount, "amount");
    }
}
