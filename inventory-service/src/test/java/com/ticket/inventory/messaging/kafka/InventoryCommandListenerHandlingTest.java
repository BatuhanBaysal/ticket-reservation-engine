package com.ticket.inventory.messaging.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ticket.events.InvalidMessageException;
import com.ticket.events.MessageCodec;
import com.ticket.events.MessageEnvelope;
import com.ticket.events.MessageTypes;
import com.ticket.events.Topics;
import com.ticket.events.inventory.HoldSeatCommand;
import com.ticket.inventory.service.SeatCommandHandler;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.slf4j.MDC;
import org.springframework.kafka.support.Acknowledgment;

class InventoryCommandListenerHandlingTest {

    private static final UUID ORDER_ID = UUID.fromString("5b2d7a40-91c8-4e6f-8d3a-2c4f1a9b7e55");

    private final MessageCodec codec = new MessageCodec();
    private final SeatCommandHandler handler = mock(SeatCommandHandler.class);
    private final Acknowledgment acknowledgment = mock(Acknowledgment.class);
    private final InventoryCommandListener listener = new InventoryCommandListener(codec, handler);
    private final MessageEnvelope<HoldSeatCommand> command = new MessageEnvelope<>(
        UUID.fromString("0f8b2c36-5d0e-4c53-9b1a-7e2a5f4d9c10"), "c-7f3a91", MessageTypes.HOLD_SEAT_COMMAND,
        Instant.parse("2026-11-28T10:15:30.123Z"), new HoldSeatCommand(ORDER_ID, "evt-001", "A-12"));

    @Test
    void passesTheDecodedEnvelopeToTheHandler() {
        listener.onMessage(record(codec.toJson(command)), acknowledgment);

        ArgumentCaptor<MessageEnvelope<?>> captor = ArgumentCaptor.captor();
        verify(handler).handle(captor.capture());
        assertThat(captor.getValue()).isEqualTo(command);
    }

    @Test
    void acknowledgesAfterTheHandlerReturns() {
        listener.onMessage(record(codec.toJson(command)), acknowledgment);

        InOrder inOrder = inOrder(handler, acknowledgment);
        inOrder.verify(handler).handle(any());
        inOrder.verify(acknowledgment).acknowledge();
    }

    @Test
    void doesNotAcknowledgeWhenTheHandlerFails() {
        doThrow(new IllegalStateException("boom")).when(handler).handle(any());

        catchThrowable(() -> listener.onMessage(record(codec.toJson(command)), acknowledgment));

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void passesTheFailureOfTheHandlerOn() {
        doThrow(new IllegalStateException("boom")).when(handler).handle(any());
        ConsumerRecord<String, String> record = record(codec.toJson(command));

        assertThatThrownBy(() -> listener.onMessage(record, acknowledgment))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("boom");
    }

    @Test
    void rejectsAnInvalidMessage() {
        ConsumerRecord<String, String> record = record("this is not json");

        assertThatThrownBy(() -> listener.onMessage(record, acknowledgment))
            .isInstanceOf(InvalidMessageException.class);
    }

    @Test
    void doesNotCallTheHandlerForAnInvalidMessage() {
        ConsumerRecord<String, String> record = record("this is not json");

        catchThrowable(() -> listener.onMessage(record, acknowledgment));

        verify(handler, never()).handle(any());
    }

    @Test
    void doesNotAcknowledgeAnInvalidMessage() {
        ConsumerRecord<String, String> record = record("this is not json");

        catchThrowable(() -> listener.onMessage(record, acknowledgment));

        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void rejectsARecordWithoutAValue() {
        ConsumerRecord<String, String> record = record(null);

        assertThatThrownBy(() -> listener.onMessage(record, acknowledgment))
            .isInstanceOf(InvalidMessageException.class)
            .hasMessage("The record has no value");
    }

    @Test
    void putsTheCorrelationIdInTheLoggingContextWhileTheHandlerRuns() {
        AtomicReference<String> seen = new AtomicReference<>();
        doAnswer(invocation -> {
            seen.set(MDC.get("correlationId"));
            return null;
        }).when(handler).handle(any());

        listener.onMessage(record(codec.toJson(command)), acknowledgment);

        assertThat(seen.get()).isEqualTo("c-7f3a91");
    }

    @Test
    void removesTheCorrelationIdAfterTheHandlerReturns() {
        listener.onMessage(record(codec.toJson(command)), acknowledgment);

        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void removesTheCorrelationIdWhenTheHandlerFails() {
        doThrow(new IllegalStateException("boom")).when(handler).handle(any());

        catchThrowable(() -> listener.onMessage(record(codec.toJson(command)), acknowledgment));

        assertThat(MDC.get("correlationId")).isNull();
    }

    private static ConsumerRecord<String, String> record(String value) {
        return new ConsumerRecord<>(Topics.INVENTORY_COMMANDS, 0, 5L, ORDER_ID.toString(), value);
    }
}
