package com.ticket.inventory;

import static org.assertj.core.api.SoftAssertions.assertSoftly;

import com.ticket.events.inventory.SeatHoldRejected;
import com.ticket.inventory.domain.Seat;
import com.ticket.inventory.domain.SeatStatus;
import com.ticket.inventory.repository.SeatRepository;
import com.ticket.inventory.service.HoldResult;
import com.ticket.inventory.service.SeatService;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;

abstract class SeatHoldConcurrencyCases {

    static final int ROUNDS = 20;
    static final int ORDERS = 100;
    private static final String EVENT_ID = "evt-001";
    private static final HoldResult.Rejected UNAVAILABLE = new HoldResult.Rejected(SeatHoldRejected.SEAT_UNAVAILABLE);

    @Autowired
    private SeatService service;

    @Autowired
    private SeatRepository repository;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private HoldUpdateCounter counter;

    abstract int expectedDatabaseUpdates();

    @BeforeEach
    void emptyTheTableTheGateAndTheCounter() {
        jdbc.sql("DELETE FROM seat_inventory").update();
        RedisFixtures.clearHoldKeys(redis);
        counter.reset();
    }

    @RepeatedTest(value = ROUNDS, name = "round {currentRepetition} of {totalRepetitions}")
    void exactlyOneOfHundredOrdersHoldsTheSeat(RepetitionInfo repetition) {
        String seatId = "R-" + repetition.getCurrentRepetition();
        SeatFixtures.insertAvailable(jdbc, EVENT_ID, seatId);
        List<UUID> orderIds = newOrderIds();

        List<HoldResult> results = holdTogether(orderIds, Collections.nCopies(ORDERS, seatId));

        List<UUID> winners = IntStream.range(0, ORDERS)
            .filter(index -> results.get(index) instanceof HoldResult.Held)
            .mapToObj(orderIds::get)
            .toList();
        long rejected = results.stream().filter(UNAVAILABLE::equals).count();
        Seat seat = repository.findByEventIdAndSeatId(EVENT_ID, seatId).orElseThrow();
        assertSoftly(softly -> {
            softly.assertThat(winners).hasSize(1);
            softly.assertThat(rejected).isEqualTo(ORDERS - 1L);
            softly.assertThat(seat.getStatus()).isEqualTo(SeatStatus.HELD);
            softly.assertThat(winners).containsExactly(seat.getHeldByOrderId());
            softly.assertThat(seat.getVersion()).isEqualTo(1L);
            softly.assertThat(counter.count()).isEqualTo(expectedDatabaseUpdates());
        });
    }

    @Test
    void everyOrderHoldsItsOwnSeatWhenHundredOrdersUseHundredSeats() {
        List<String> seatIds = IntStream.range(0, ORDERS).mapToObj(number -> "S-" + number).toList();
        seatIds.forEach(seatId -> SeatFixtures.insertAvailable(jdbc, EVENT_ID, seatId));

        List<HoldResult> results = holdTogether(newOrderIds(), seatIds);

        long held = results.stream().filter(HoldResult.Held.class::isInstance).count();
        Long heldRows = jdbc.sql("SELECT count(*) FROM seat_inventory WHERE status = 'HELD'")
            .query(Long.class)
            .single();
        assertSoftly(softly -> {
            softly.assertThat(held).isEqualTo(ORDERS);
            softly.assertThat(heldRows).isEqualTo((long) ORDERS);
            softly.assertThat(counter.count()).isEqualTo(ORDERS);
        });
    }

    private List<HoldResult> holdTogether(List<UUID> orderIds, List<String> seatIds) {
        List<Callable<HoldResult>> tasks = IntStream.range(0, orderIds.size())
            .<Callable<HoldResult>>mapToObj(index -> () -> service.hold(orderIds.get(index), EVENT_ID, seatIds.get(index)))
            .toList();
        return ConcurrentRunner.runTogether(tasks);
    }

    private static List<UUID> newOrderIds() {
        return IntStream.range(0, ORDERS).mapToObj(number -> UUID.randomUUID()).toList();
    }
}
