package com.ticket.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ticket.inventory.domain.Seat;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;

class TransactionAttemptsTest {

    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final TransactionAttempts attempts = new TransactionAttempts(transactionManager);
    private final AtomicInteger calls = new AtomicInteger();

    @Test
    void returnsTheResultOfTheWork() {
        String result = attempts.run(() -> "done");

        assertThat(result).isEqualTo("done");
    }

    @Test
    void runsTheWorkOnceWhenThereIsNoConflict() {
        attempts.run(conflictingTimes(0));

        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void retriesAfterAConflict() {
        String result = attempts.run(conflictingTimes(1));

        assertThat(result).isEqualTo("done");
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void succeedsOnTheThirdAttemptAfterTwoConflicts() {
        String result = attempts.run(conflictingTimes(2));

        assertThat(result).isEqualTo("done");
        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void throwsSeatConflictExceptionAfterThreeConflicts() {
        Supplier<String> work = conflictingTimes(3);

        assertThatThrownBy(() -> attempts.run(work))
            .isInstanceOf(SeatConflictException.class)
            .hasMessage("The seat was changed concurrently in all 3 attempts");
    }

    @Test
    void triesThreeTimesBeforeGivingUp() {
        Supplier<String> work = conflictingTimes(3);

        catchThrowable(() -> attempts.run(work));

        assertThat(calls.get()).isEqualTo(3);
    }

    @Test
    void startsANewTransactionForEachAttempt() {
        Supplier<String> work = conflictingTimes(3);

        catchThrowable(() -> attempts.run(work));

        verify(transactionManager, times(3)).getTransaction(any());
    }

    @Test
    void commitsOnlyTheSuccessfulAttempt() {
        attempts.run(conflictingTimes(2));

        verify(transactionManager, times(2)).rollback(any());
        verify(transactionManager, times(1)).commit(any());
    }

    @Test
    void doesNotRetryOtherExceptions() {
        Supplier<String> work = () -> {
            throw new IllegalStateException("boom");
        };

        assertThatThrownBy(() -> attempts.run(work))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("boom");
    }

    @Test
    void runsWorkThatFailsWithAnotherExceptionOnlyOnce() {
        Supplier<String> work = () -> {
            calls.incrementAndGet();
            throw new IllegalStateException("boom");
        };

        catchThrowable(() -> attempts.run(work));

        assertThat(calls.get()).isEqualTo(1);
    }

    private Supplier<String> conflictingTimes(int conflicts) {
        return () -> {
            if (calls.incrementAndGet() <= conflicts) {
                throw new ObjectOptimisticLockingFailureException(Seat.class, "seat");
            }
            return "done";
        };
    }
}
