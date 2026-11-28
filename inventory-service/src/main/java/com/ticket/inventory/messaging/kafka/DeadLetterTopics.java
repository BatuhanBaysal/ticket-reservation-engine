package com.ticket.inventory.messaging.kafka;

import com.ticket.events.Require;

public final class DeadLetterTopics {

    private static final String SUFFIX = ".dlt";

    private DeadLetterTopics() {
    }

    public static String of(String topic) {
        return Require.notBlank(topic, "topic") + SUFFIX;
    }
}
