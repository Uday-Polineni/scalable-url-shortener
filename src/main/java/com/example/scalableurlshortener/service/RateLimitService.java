package com.example.scalableurlshortener.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RateLimitService {

    private static final String KEY_PREFIX = "rl:";

    private final StringRedisTemplate redisTemplate;

    public RateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Checks whether the caller has exceeded the rate limit for a given bucket.
     *
     * Uses a fixed-window counter keyed by {@code rl:{clientId}:{bucket}:{minuteEpoch}}.
     * Each key auto-expires after 60 seconds so stale windows are cleaned up by Redis.
     *
     * @return the current request count after incrementing, or -1 if Redis is unavailable
     */
    public long incrementAndGet(String clientId, String bucket, int maxPerMinute) {
        long minuteEpoch = System.currentTimeMillis() / 60_000;
        String key = KEY_PREFIX + clientId + ":" + bucket + ":" + minuteEpoch;

        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count == null) {
                return -1;
            }
            if (count == 1L) {
                redisTemplate.expire(key, Duration.ofSeconds(60));
            }
            return count;
        } catch (Exception e) {
            // If Redis is down, fail open (allow the request)
            return -1;
        }
    }

    public boolean isRateLimited(String clientId, String bucket, int maxPerMinute) {
        long count = incrementAndGet(clientId, bucket, maxPerMinute);
        return count > maxPerMinute;
    }
}
