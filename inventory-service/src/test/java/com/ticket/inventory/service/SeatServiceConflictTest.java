package com.ticket.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import org.springframework.transaction.PlatformTransactionManager;

class SeatServiceConflictTest {

    private static final String EVENT_ID = "evt-001";
    private static final String SEAT_ID = "A-12";
    private static final UUID ORDER_ID = UUID.fromString("5b2d7a40-91c8-4e6f-8d3a-2c4f1a9b7e55");

    private final SeatRepository repository = mock(SeatRepository.class);
    private final HoldGate gate = mock(HoldGate.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final Seat seat = mock(Seat.class);
    private final SeatService service =
        new SeatService(repository, new HoldProperties(Duration.ofMinutes(10)), gate, transactionManager);

    @BeforeEach
    void holdTheSeatForTheOrder() {
        when(seat.getStatus()).thenReturn(SeatStatus.HELD);
        when(seat.getHeldByOrderId()).thenReturn(ORDER_ID);
        when(repository.findByEventIdAndSeatId(EVENT_ID, SEAT_ID)).thenReturn(Optional.of(seat));
    }

    @Test
    void confirmsOnTheThirdAttemptAfterTwoConflicts() {
        when(repository.saveAndFlush(any(Seat.class))).thenThrow(conflict()).thenThrow(conflict()).thenReturn(seat);

        ConfirmResult result = service.confirm(ORDER_ID, EVENT_ID, SEAT_ID);

        assertThat(result).isEqualTo(new ConfirmResult.Confirmed());
    }

    @Test
    void releasesOnTheThirdAttemptAfterTwoConflicts() {
        when(repository.saveAndFlush(any(Seat.class))).thenThrow(conflict()).thenThrow(conflict()).thenReturn(seat);

        service.release(ORDER_ID, EVENT_ID, SEAT_ID);

        verify(gate).release(ORDER_ID, EVENT_ID, SEAT_ID);
    }

    @Test
    void throwsSeatConflictExceptionAfterThreeConflicts() {
        when(repository.saveAndFlush(any(Seat.class))).thenThrow(conflict());

        assertThatThrownBy(() -> service.confirm(ORDER_ID, EVENT_ID, SEAT_ID))
            .isInstanceOf(SeatConflictException.class)
            .hasMessage("The seat was changed concurrently in all 3 attempts");
    }

    @Test
    void triesThreeTimesBeforeGivingUp() {
        when(repository.saveAndFlush(any(Seat.class))).thenThrow(conflict());

        assertThatThrownBy(() -> service.confirm(ORDER_ID, EVENT_ID, SEAT_ID))
            .isInstanceOf(SeatConflictException.class);

        verify(repository, times(3)).saveAndFlush(any(Seat.class));
    }

    @Test
    void commitsNothingAfterThreeConflicts() {
        when(repository.saveAndFlush(any(Seat.class))).thenThrow(conflict());

        assertThatThrownBy(() -> service.confirm(ORDER_ID, EVENT_ID, SEAT_ID))
            .isInstanceOf(SeatConflictException.class);

        verify(transactionManager, never()).commit(any());
        verify(transactionManager, times(3)).rollback(any());
    }

    @Test
    void keepsTheRedisKeyWhenReleaseGivesUp() {
        when(repository.saveAndFlush(any(Seat.class))).thenThrow(conflict());

        assertThatThrownBy(() -> service.release(ORDER_ID, EVENT_ID, SEAT_ID))
            .isInstanceOf(SeatConflictException.class);

        verify(gate, never()).release(any(), any(), any());
    }

    private static ObjectOptimisticLockingFailureException conflict() {
        return new ObjectOptimisticLockingFailureException(Seat.class, "seat");
    }
}
