package com.ticket.inventory.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ticket.inventory.domain.Seat;
import com.ticket.inventory.domain.SeatStatus;
import com.ticket.inventory.repository.SeatRepository;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

class SeatStepsConflictTest {

    private static final String EVENT_ID = "evt-001";
    private static final String SEAT_ID = "A-12";
    private static final UUID ORDER_ID = UUID.fromString("5b2d7a40-91c8-4e6f-8d3a-2c4f1a9b7e55");

    private final SeatRepository repository = mock(SeatRepository.class);
    private final Seat seat = mock(Seat.class);
    private final SeatSteps steps = new SeatSteps(repository, new HoldProperties(Duration.ofMinutes(10)));

    @BeforeEach
    void holdTheSeatForTheOrderAndFailTheSave() {
        when(seat.getStatus()).thenReturn(SeatStatus.HELD);
        when(seat.getHeldByOrderId()).thenReturn(ORDER_ID);
        when(repository.findByEventIdAndSeatId(EVENT_ID, SEAT_ID)).thenReturn(Optional.of(seat));
        when(repository.saveAndFlush(any(Seat.class)))
            .thenThrow(new ObjectOptimisticLockingFailureException(Seat.class, "seat"));
    }

    @Test
    void theConfirmStepThrowsTheConflictAtOnce() {
        assertThatThrownBy(() -> steps.confirm(ORDER_ID, EVENT_ID, SEAT_ID))
            .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void theConfirmStepSavesOnlyOnce() {
        catchThrowable(() -> steps.confirm(ORDER_ID, EVENT_ID, SEAT_ID));

        verify(repository, times(1)).saveAndFlush(any(Seat.class));
    }

    @Test
    void theReleaseStepThrowsTheConflictAtOnce() {
        assertThatThrownBy(() -> steps.release(ORDER_ID, EVENT_ID, SEAT_ID))
            .isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }

    @Test
    void theReleaseStepSavesOnlyOnce() {
        catchThrowable(() -> steps.release(ORDER_ID, EVENT_ID, SEAT_ID));

        verify(repository, times(1)).saveAndFlush(any(Seat.class));
    }
}
