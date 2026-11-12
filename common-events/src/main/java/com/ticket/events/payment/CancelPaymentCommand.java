package com.ticket.events.payment;

import com.ticket.events.Require;

import java.util.UUID;

public record CancelPaymentCommand(UUID paymentRequestId, UUID orderId) {

    public CancelPaymentCommand {
        Require.notNull(paymentRequestId, "paymentRequestId");
        Require.notNull(orderId, "orderId");
    }
}
