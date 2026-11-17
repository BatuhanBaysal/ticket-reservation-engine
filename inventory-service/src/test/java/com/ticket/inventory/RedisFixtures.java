package com.ticket.inventory;

import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;

final class RedisFixtures {

    private RedisFixtures() {
    }

    static String holdKey(String eventId, String seatId) {
        return "hold:" + eventId + ":" + seatId;
    }

    static void clearHoldKeys(StringRedisTemplate redis) {
        Set<String> keys = redis.keys("hold:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }
}
