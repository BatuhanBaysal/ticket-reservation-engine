package com.ticket.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TopicsTest {

    @Test
    void topicNamesMatchTheMessagingTable() {
        List<String> names = ConstantReader.stringConstantsOf(Topics.class);

        assertThat(names).containsExactlyInAnyOrder(
            "inventory.commands",
            "inventory.events",
            "payment.commands",
            "payment.events");
    }
}
