package com.ticket.events;

import java.io.Serial;

public final class UnknownMessageTypeException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public UnknownMessageTypeException(String type) {
        super("Unknown message type: " + type);
    }
}
