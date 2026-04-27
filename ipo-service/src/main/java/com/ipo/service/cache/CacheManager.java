package com.ipo.service.cache;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.slf4j.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class CacheManager {

    private static final Logger log = LoggerFactory.getLogger(CacheManager.class);
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * Set cache value with default TTL
     */
    public void set(String key, Object value) {
        set(key, value, CacheKeys.DEFAULT_TTL, TimeUnit.MINUTES);
    }

    /**
     * Set cache value with custom TTL
     */
    public void set(String key, Object value, long timeout, TimeUnit timeUnit) {
        try {
            redisTemplate.opsForValue().set(key, value, timeout, timeUnit);
            log.debug("Cache set: key={}, timeout={} {}", key, timeout, timeUnit);
        } catch (Exception e) {
            log.error("Error setting cache for key: {}", key, e);
        }
    }

    /**
     * Get cache value
     */
    public Object get(String key) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            log.debug("Cache get: key={}, value={}", key, value != null ? "found" : "not found");
            return value;
        } catch (Exception e) {
            log.error("Error getting cache for key: {}", key, e);
            return null;
        }
    }

    /**
     * Delete cache key
     */
    public boolean delete(String key) {
        try {
            Boolean result = redisTemplate.delete(key);
            log.debug("Cache deleted: key={}, deleted={}", key, result);
            return result != null && result;
        } catch (Exception e) {
            log.error("Error deleting cache for key: {}", key, e);
            return false;
        }
    }

    /**
     * Check if key exists
     */
    public boolean exists(String key) {
        try {
            Boolean result = redisTemplate.hasKey(key);
            return result != null && result;
        } catch (Exception e) {
            log.error("Error checking cache existence for key: {}", key, e);
            return false;
        }
    }

    /**
     * Clear all cache keys matching pattern
     */
    public void deletePattern(String pattern) {
        try {
            redisTemplate.delete(redisTemplate.keys(pattern));
            log.debug("Cache pattern deleted: pattern={}", pattern);
        } catch (Exception e) {
            log.error("Error deleting cache pattern: {}", pattern, e);
        }
    }

    /**
     * Set cache expiry
     */
    public void expire(String key, long timeout, TimeUnit timeUnit) {
        try {
            redisTemplate.expire(key, timeout, timeUnit);
            log.debug("Cache expiry set: key={}, timeout={} {}", key, timeout, timeUnit);
        } catch (Exception e) {
            log.error("Error setting expiry for cache key: {}", key, e);
        }
    }
}
