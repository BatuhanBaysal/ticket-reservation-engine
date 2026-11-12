package com.ticket.events.payment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PaymentMessageValidationTest {

    private static final UUID PAYMENT_REQUEST_ID = UUID.fromString("0f8b2c36-5d0e-4c53-9b1a-7e2a5f4d9c10");
    private static final UUID ORDER_ID = UUID.fromString("5b2d7a40-91c8-4e6f-8d3a-2c4f1a9b7e55");
    private static final BigDecimal AMOUNT = new BigDecimal("49.90");

    @FunctionalInterface
    interface PaymentMessageFactory {
        Object create(UUID paymentRequestId, UUID orderId);
    }

    static Stream<Arguments> messages() {
        return Stream.of(
            message("ProcessPaymentCommand", (paymentRequestId, orderId) ->
                new ProcessPaymentCommand(paymentRequestId, orderId, AMOUNT)),
            message("CancelPaymentCommand", CancelPaymentCommand::new),
            message("PaymentCompleted", (paymentRequestId, orderId) ->
                new PaymentCompleted(paymentRequestId, orderId, AMOUNT)),
            message("PaymentDeclined", (paymentRequestId, orderId) ->
                new PaymentDeclined(paymentRequestId, orderId, "CARD_DECLINED")),
            message("PaymentCancelled", (paymentRequestId, orderId) ->
                new PaymentCancelled(paymentRequestId, orderId, PaymentCancelled.REFUNDED)));
    }

    private static Arguments message(String name, PaymentMessageFactory factory) {
        return Arguments.of(Named.of(name, factory));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("messages")
    void rejectsNullPaymentRequestId(PaymentMessageFactory factory) {
        assertThatThrownBy(() -> factory.create(null, ORDER_ID))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("paymentRequestId");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("messages")
    void rejectsNullOrderId(PaymentMessageFactory factory) {
        assertThatThrownBy(() -> factory.create(PAYMENT_REQUEST_ID, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("orderId");
    }
}
