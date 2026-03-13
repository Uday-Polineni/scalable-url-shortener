package com.example.scalableurlshortener.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class UrlCacheService {

    private static final String URL_PREFIX = "url:";
    private static final String CLICKS_PREFIX = "clicks:";
    private static final Duration TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;

    public UrlCacheService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // ── URL cache (code → longUrl) ──

    public Optional<String> getLongUrl(String code) {
        String value = redisTemplate.opsForValue().get(URL_PREFIX + code);
        return Optional.ofNullable(value);
    }

    public void putLongUrl(String code, String longUrl) {
        redisTemplate.opsForValue().set(URL_PREFIX + code, longUrl, TTL);
    }

    // ── Click counters (code → pending count) ──

    public void incrementClickCount(String code) {
        redisTemplate.opsForValue().increment(CLICKS_PREFIX + code);
    }

    public long getClickCount(String code) {
        String val = redisTemplate.opsForValue().get(CLICKS_PREFIX + code);
        return val == null ? 0L : Long.parseLong(val);
    }

    /**
     * Atomically reads and deletes all pending click counters.
     * Returns a map of code → delta to flush into Postgres.
     */
    public Map<String, Long> drainAllClickCounts() {
        Set<String> keys = redisTemplate.keys(CLICKS_PREFIX + "*");
        if (keys == null || keys.isEmpty()) {
            return Map.of();
        }

        Map<String, Long> result = new HashMap<>();
        for (String key : keys) {
            String val = redisTemplate.opsForValue().getAndDelete(key);
            if (val != null) {
                long delta = Long.parseLong(val);
                if (delta > 0) {
                    String code = key.substring(CLICKS_PREFIX.length());
                    result.put(code, delta);
                }
            }
        }
        return result;
    }
}

