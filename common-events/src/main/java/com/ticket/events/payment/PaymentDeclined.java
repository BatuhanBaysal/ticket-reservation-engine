package com.ticket.events.payment;

import com.ticket.events.Require;

import java.util.UUID;

public record PaymentDeclined(UUID paymentRequestId, UUID orderId, String reason) {

    public PaymentDeclined {
        Require.notNull(paymentRequestId, "paymentRequestId");
        Require.notNull(orderId, "orderId");
        Require.notBlank(reason, "reason");
    }
}
