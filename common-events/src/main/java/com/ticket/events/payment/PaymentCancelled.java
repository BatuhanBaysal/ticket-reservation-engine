package com.ticket.events.payment;

import com.ticket.events.Require;

import java.util.UUID;

public record PaymentCancelled(UUID paymentRequestId, UUID orderId, String outcome) {

    public static final String REFUNDED = "REFUNDED";
    public static final String CANCELLED = "CANCELLED";

    public PaymentCancelled {
        Require.notNull(paymentRequestId, "paymentRequestId");
        Require.notNull(orderId, "orderId");
        Require.notBlank(outcome, "outcome");
    }
}
