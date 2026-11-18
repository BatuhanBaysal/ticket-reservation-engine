package com.ticket.inventory.service;

import java.io.Serial;

public final class SeatConflictException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public SeatConflictException(int attempts, Throwable cause) {
        super("The seat was changed concurrently in all " + attempts + " attempts", cause);
    }
}
