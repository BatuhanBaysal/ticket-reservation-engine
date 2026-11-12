package com.ticket.events.payment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PaymentTextFieldsTest {

    private static final UUID PAYMENT_REQUEST_ID = UUID.fromString("0f8b2c36-5d0e-4c53-9b1a-7e2a5f4d9c10");
    private static final UUID ORDER_ID = UUID.fromString("5b2d7a40-91c8-4e6f-8d3a-2c4f1a9b7e55");

    @FunctionalInterface
    interface TextMessageFactory {
        Object create(String text);
    }

    static Stream<Arguments> messages() {
        return Stream.of(
            message("PaymentDeclined", "reason", text ->
                new PaymentDeclined(PAYMENT_REQUEST_ID, ORDER_ID, text)),
            message("PaymentCancelled", "outcome", text ->
                new PaymentCancelled(PAYMENT_REQUEST_ID, ORDER_ID, text)));
    }

    private static Arguments message(String name, String field, TextMessageFactory factory) {
        return Arguments.of(Named.of(name, factory), field);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("messages")
    void rejectsANullValue(TextMessageFactory factory, String field) {
        assertThatThrownBy(() -> factory.create(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage(field);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("messages")
    void rejectsABlankValue(TextMessageFactory factory, String field) {
        assertThatThrownBy(() -> factory.create(" "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage(field + " must not be blank");
    }
}
