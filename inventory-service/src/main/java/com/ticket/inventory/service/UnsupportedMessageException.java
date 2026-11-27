package com.ticket.inventory.service;

import java.io.Serial;

public final class UnsupportedMessageException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public UnsupportedMessageException(String type) {
        super("inventory-service does not handle messages of type " + type);
    }
}
