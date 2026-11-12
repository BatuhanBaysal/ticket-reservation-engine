package com.ticket.events.payment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AmountMessagesTest {

    private static final UUID PAYMENT_REQUEST_ID = UUID.fromString("0f8b2c36-5d0e-4c53-9b1a-7e2a5f4d9c10");
    private static final UUID ORDER_ID = UUID.fromString("5b2d7a40-91c8-4e6f-8d3a-2c4f1a9b7e55");

    @FunctionalInterface
    interface AmountMessageFactory {
        Object create(BigDecimal amount);
    }

    static Stream<Named<AmountMessageFactory>> factories() {
        return Stream.of(
            named("ProcessPaymentCommand", amount ->
                new ProcessPaymentCommand(PAYMENT_REQUEST_ID, ORDER_ID, amount)),
            named("PaymentCompleted", amount ->
                new PaymentCompleted(PAYMENT_REQUEST_ID, ORDER_ID, amount)));
    }

    static Stream<Arguments> messages() {
        return factories().map(Arguments::of);
    }

    static Stream<Arguments> invalidAmounts() {
        return Stream.of("0", "0.00", "-1", "-0.01")
            .flatMap(value -> factories().map(factory -> Arguments.of(factory, value)));
    }

    private static Named<AmountMessageFactory> named(String name, AmountMessageFactory factory) {
        return Named.of(name, factory);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("messages")
    void acceptsAPositiveAmount(AmountMessageFactory factory) {
        BigDecimal amount = new BigDecimal("49.90");

        assertThatCode(() -> factory.create(amount)).doesNotThrowAnyException();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("messages")
    void rejectsANullAmount(AmountMessageFactory factory) {
        assertThatThrownBy(() -> factory.create(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("amount");
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("invalidAmounts")
    void rejectsAnAmountOfZeroOrLess(AmountMessageFactory factory, String value) {
        BigDecimal amount = new BigDecimal(value);

        assertThatThrownBy(() -> factory.create(amount))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("amount must be positive");
    }
}
