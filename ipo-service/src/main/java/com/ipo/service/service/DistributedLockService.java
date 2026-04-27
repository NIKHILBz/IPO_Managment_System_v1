package com.ipo.service.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Distributed Locking Service using Redis
 *
 * Provides Redis-based distributed locks for preventing concurrent operations
 * across multiple application instances. Used for:
 * - Application submission (duplicate prevention)
 * - Status transitions
 * - Critical resource allocation
 *
 * Lock Strategy:
 * - SET with NX (only if not exists) and EX (expiration)
 * - Unique token per lock holder to prevent accidental release
 * - Automatic expiration prevents deadlocks
 */
@Slf4j
@Service
public class DistributedLockService {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private static final String LOCK_PREFIX = "lock:";
    private static final long DEFAULT_TIMEOUT_SECONDS = 30;
    private static final long LONG_OPERATION_TIMEOUT_SECONDS = 300; // 5 minutes

    /**
     * Acquire a distributed lock
     *
     * @param lockKey the key to lock
     * @param timeoutSeconds timeout in seconds
     * @return unique token if lock acquired, null if lock already held
     */
    public String acquireLock(String lockKey, long timeoutSeconds) {
        String token = UUID.randomUUID().toString();
        String key = LOCK_PREFIX + lockKey;

        log.debug("Attempting to acquire lock: key={}, timeout={}s", key, timeoutSeconds);

        try {
            Boolean locked = redisTemplate.opsForValue().setIfAbsent(key, token, timeoutSeconds, TimeUnit.SECONDS);
            if (Boolean.TRUE.equals(locked)) {
                log.info("Lock acquired: key={}, token={}", key, token);
                return token;
            } else {
                log.warn("Lock already held: key={}", key);
                return null;
            }
        } catch (Exception e) {
            log.error("Error acquiring lock: key={}", key, e);
            return null;
        }
    }

    /**
     * Acquire a distributed lock with default timeout (30 seconds)
     */
    public String acquireLock(String lockKey) {
        return acquireLock(lockKey, DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * Acquire a lock for long-running operations (5 minutes)
     */
    public String acquireLockForLongOperation(String lockKey) {
        return acquireLock(lockKey, LONG_OPERATION_TIMEOUT_SECONDS);
    }

    /**
     * Release a distributed lock (only if token matches - prevents accidental release)
     *
     * @param lockKey the key to unlock
     * @param token the token acquired with the lock
     * @return true if lock released, false if token mismatch or lock not held
     */
    public boolean releaseLock(String lockKey, String token) {
        if (token == null) {
            log.warn("Cannot release lock with null token: key={}", lockKey);
            return false;
        }

        String key = LOCK_PREFIX + lockKey;
        log.debug("Attempting to release lock: key={}, token={}", key, token);

        try {
            String currentToken = redisTemplate.opsForValue().get(key);

            // Only release if token matches (prevent releasing other's locks)
            if (token.equals(currentToken)) {
                redisTemplate.delete(key);
                log.info("Lock released: key={}", key);
                return true;
            } else {
                log.warn("Token mismatch - lock not released: key={}, expected={}, actual={}",
                    key, token, currentToken);
                return false;
            }
        } catch (Exception e) {
            log.error("Error releasing lock: key={}", key, e);
            return false;
        }
    }

    /**
     * Check if a lock is currently held
     */
    public boolean isLocked(String lockKey) {
        String key = LOCK_PREFIX + lockKey;
        try {
            Boolean exists = redisTemplate.hasKey(key);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.error("Error checking lock: key={}", key, e);
            return false;
        }
    }

    /**
     * Force release a lock (use with caution - may cause issues if operation still running)
     */
    public void forceClearLock(String lockKey) {
        String key = LOCK_PREFIX + lockKey;
        log.warn("Force clearing lock: key={}", key);
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.error("Error force clearing lock: key={}", key, e);
        }
    }

    /**
     * Get remaining TTL for a lock (for monitoring)
     */
    public long getLockTTL(String lockKey) {
        String key = LOCK_PREFIX + lockKey;
        try {
            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            return ttl != null ? ttl : -1;
        } catch (Exception e) {
            log.error("Error getting lock TTL: key={}", key, e);
            return -1;
        }
    }
}
