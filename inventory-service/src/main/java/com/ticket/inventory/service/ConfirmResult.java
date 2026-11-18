package com.ticket.inventory.service;

import com.ticket.events.Require;

public sealed interface ConfirmResult {

    record Confirmed() implements ConfirmResult {
    }

    record Failed(String reason) implements ConfirmResult {

        public Failed {
            Require.notBlank(reason, "reason");
        }
    }
}
