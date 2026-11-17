package com.ticket.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.ticket.inventory.service.GateDecision;
import com.ticket.inventory.service.HoldGate;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RedisHoldGateTest {

    private static final String EVENT_ID = "evt-001";
    private static final String SEAT_ID = "A-12";
    private static final UUID ORDER_ID = UUID.fromString("5b2d7a40-91c8-4e6f-8d3a-2c4f1a9b7e55");
    private static final UUID OTHER_ORDER_ID = UUID.fromString("c3e1f6a8-2b94-4d57-9a0e-6f8d1b7c4e23");
    private static final String KEY = RedisFixtures.holdKey(EVENT_ID, SEAT_ID);

    @Autowired
    private HoldGate gate;

    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void clearTheKeys() {
        RedisFixtures.clearHoldKeys(redis);
    }

    @Test
    void proceedsForTheFirstOrder() {
        GateDecision decision = gate.admit(ORDER_ID, EVENT_ID, SEAT_ID);

        assertThat(decision).isEqualTo(GateDecision.PROCEED);
    }

    @Test
    void storesTheOrderIdInTheKey() {
        gate.admit(ORDER_ID, EVENT_ID, SEAT_ID);

        String holder = redis.opsForValue().get(KEY);

        assertThat(holder).isEqualTo(ORDER_ID.toString());
    }

    @Test
    void expiresTheKeyAfterTheHoldDuration() {
        gate.admit(ORDER_ID, EVENT_ID, SEAT_ID);

        Long seconds = redis.getExpire(KEY, TimeUnit.SECONDS);

        assertThat(seconds).isBetween(595L, 600L);
    }

    @Test
    void rejectsAnotherOrderWhileTheKeyExists() {
        gate.admit(ORDER_ID, EVENT_ID, SEAT_ID);

        GateDecision decision = gate.admit(OTHER_ORDER_ID, EVENT_ID, SEAT_ID);

        assertThat(decision).isEqualTo(GateDecision.REJECT);
    }

    @Test
    void keepsTheKeyOfTheFirstOrderWhenAnotherIsRejected() {
        gate.admit(ORDER_ID, EVENT_ID, SEAT_ID);

        gate.admit(OTHER_ORDER_ID, EVENT_ID, SEAT_ID);

        assertThat(redis.opsForValue().get(KEY)).isEqualTo(ORDER_ID.toString());
    }

    @Test
    void proceedsForTheSameOrderAgain() {
        gate.admit(ORDER_ID, EVENT_ID, SEAT_ID);

        GateDecision decision = gate.admit(ORDER_ID, EVENT_ID, SEAT_ID);

        assertThat(decision).isEqualTo(GateDecision.PROCEED);
    }

    @Test
    void deletesTheKeyOfTheSameOrderOnRelease() {
        gate.admit(ORDER_ID, EVENT_ID, SEAT_ID);

        gate.release(ORDER_ID, EVENT_ID, SEAT_ID);

        assertThat(redis.hasKey(KEY)).isFalse();
    }

    @Test
    void keepsTheKeyOfAnotherOrderOnRelease() {
        gate.admit(ORDER_ID, EVENT_ID, SEAT_ID);

        gate.release(OTHER_ORDER_ID, EVENT_ID, SEAT_ID);

        assertThat(redis.opsForValue().get(KEY)).isEqualTo(ORDER_ID.toString());
    }

    @Test
    void ignoresTheReleaseOfAnUnknownKey() {
        assertThatCode(() -> gate.release(ORDER_ID, EVENT_ID, SEAT_ID)).doesNotThrowAnyException();
    }
}
