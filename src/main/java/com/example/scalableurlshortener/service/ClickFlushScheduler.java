package com.example.scalableurlshortener.service;

import com.example.scalableurlshortener.store.UrlRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Component
public class ClickFlushScheduler {

    private static final Logger log = LoggerFactory.getLogger(ClickFlushScheduler.class);

    private final UrlCacheService cacheService;
    private final UrlRepository urlRepository;

    public ClickFlushScheduler(UrlCacheService cacheService, UrlRepository urlRepository) {
        this.cacheService = cacheService;
        this.urlRepository = urlRepository;
    }

    @Scheduled(fixedRate = 30_000)
    @Transactional
    public void flushClickCounters() {
        Map<String, Long> pending = cacheService.drainAllClickCounts();
        if (pending.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Long> entry : pending.entrySet()) {
            urlRepository.incrementRedirectCount(entry.getKey(), entry.getValue());
        }

        log.info("Flushed {} click counters to DB", pending.size());
    }
}
