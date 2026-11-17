package com.ticket.inventory.service;

import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
class RedisHoldGate implements HoldGate {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisHoldGate.class);

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
        if redis.call('get', KEYS[1]) == ARGV[1] then
            return redis.call('del', KEYS[1])
        end
        return 0
        """, Long.class);

    private final StringRedisTemplate redis;
    private final HoldProperties holdProperties;
    private final GateProperties gateProperties;

    RedisHoldGate(StringRedisTemplate redis, HoldProperties holdProperties, GateProperties gateProperties) {
        this.redis = redis;
        this.holdProperties = holdProperties;
        this.gateProperties = gateProperties;
    }

    @Override
    public GateDecision admit(UUID orderId, String eventId, String seatId) {
        if (!gateProperties.enabled()) {
            return GateDecision.PROCEED;
        }
        try {
            return decide(keyOf(eventId, seatId), orderId.toString());
        } catch (DataAccessException e) {
            LOGGER.warn("The Redis gate is skipped for seat {} of event {}: {}", seatId, eventId, e.getMessage());
            return GateDecision.PROCEED;
        }
    }

    @Override
    public void release(UUID orderId, String eventId, String seatId) {
        if (!gateProperties.enabled()) {
            return;
        }
        try {
            redis.execute(RELEASE_SCRIPT, List.of(keyOf(eventId, seatId)), orderId.toString());
        } catch (DataAccessException e) {
            LOGGER.warn("The Redis key of seat {} of event {} was not released: {}", seatId, eventId, e.getMessage());
        }
    }

    private GateDecision decide(String key, String holder) {
        Boolean keyWasFree = redis.opsForValue().setIfAbsent(key, holder, holdProperties.duration());
        if (Boolean.TRUE.equals(keyWasFree)) {
            return GateDecision.PROCEED;
        }
        String current = redis.opsForValue().get(key);
        boolean heldByAnotherOrder = current != null && !current.equals(holder);
        return heldByAnotherOrder ? GateDecision.REJECT : GateDecision.PROCEED;
    }

    private static String keyOf(String eventId, String seatId) {
        return "hold:" + eventId + ":" + seatId;
    }
}
