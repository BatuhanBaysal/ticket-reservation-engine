package com.ticket.inventory;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.data.redis.timeout=5s")
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, HoldUpdateCounting.class})
class SeatHoldConcurrencyGateOnTest extends SeatHoldConcurrencyCases {

    @Override
    int expectedDatabaseUpdates() {
        return 1;
    }
}
