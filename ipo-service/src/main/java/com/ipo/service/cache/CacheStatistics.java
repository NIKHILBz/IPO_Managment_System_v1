package com.ipo.service.cache;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.slf4j.*;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@Getter
public class CacheStatistics {

    private static final Logger log = LoggerFactory.getLogger(CacheStatistics.class);

    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong cacheEvictions = new AtomicLong(0);

    public void recordHit() {
        cacheHits.incrementAndGet();
    }

    public void recordMiss() {
        cacheMisses.incrementAndGet();
    }

    public void recordEviction() {
        cacheEvictions.incrementAndGet();
    }

    public double getHitRate() {
        long total = cacheHits.get() + cacheMisses.get();
        if (total == 0) {
            return 0.0;
        }
        return (double) cacheHits.get() / total * 100;
    }

    public void reset() {
        cacheHits.set(0);
        cacheMisses.set(0);
        cacheEvictions.set(0);
        log.info("Cache statistics reset");
    }

    public String getStats() {
        return String.format("Cache Stats - Hits: %d, Misses: %d, Evictions: %d, Hit Rate: %.2f%%",
                cacheHits.get(), cacheMisses.get(), cacheEvictions.get(), getHitRate());
    }
}
