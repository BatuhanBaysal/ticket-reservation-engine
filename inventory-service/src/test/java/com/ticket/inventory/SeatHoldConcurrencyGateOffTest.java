package com.ticket.inventory;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "inventory.hold.gate.enabled=false")
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, HoldUpdateCounting.class})
class SeatHoldConcurrencyGateOffTest extends SeatHoldConcurrencyCases {

    @Override
    int expectedDatabaseUpdates() {
        return ORDERS;
    }
}
