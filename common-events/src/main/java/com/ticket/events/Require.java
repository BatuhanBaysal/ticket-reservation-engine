package com.ticket.events;

import java.math.BigDecimal;
import java.util.Objects;

public class Require {

    private Require() {
    }

    public static <T> T notNull(T value, String name) {
        return Objects.requireNonNull(value, name);
    }

    public static String notBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    public static BigDecimal positive(BigDecimal value, String name) {
        Objects.requireNonNull(value, name);
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }
}
