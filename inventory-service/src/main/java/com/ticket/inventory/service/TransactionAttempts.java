package com.ticket.inventory.service;

import java.util.function.Supplier;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class TransactionAttempts {

    private static final int MAX_ATTEMPTS = 3;

    private final TransactionTemplate transaction;

    public TransactionAttempts(PlatformTransactionManager transactionManager) {
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public <T> T run(Supplier<T> work) {
        OptimisticLockingFailureException lastConflict = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return transaction.execute(status -> work.get());
            } catch (OptimisticLockingFailureException e) {
                lastConflict = e;
            }
        }
        throw new SeatConflictException(MAX_ATTEMPTS, lastConflict);
    }
}
