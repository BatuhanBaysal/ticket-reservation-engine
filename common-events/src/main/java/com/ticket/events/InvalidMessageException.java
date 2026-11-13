package com.ticket.events;

import java.io.Serial;

public final class InvalidMessageException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidMessageException(String message) {
        super(message);
    }

    public InvalidMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
