package com.ipo.service.cache;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
public class CachingAspect {

    @Autowired
    private CacheManager cacheManager;

    /**
     * Cache interceptor for methods annotated with @Cacheable
     */
    @Around("@annotation(com.ipo.service.cache.Cacheable)")
    public Object handleCacheable(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();
        String cacheKey = generateCacheKey(methodName, args);

        // Try to get from cache
        Object cachedValue = cacheManager.get(cacheKey);
        if (cachedValue != null) {
            log.debug("Cache hit for method: {} with key: {}", methodName, cacheKey);
            return cachedValue;
        }

        // Execute method if cache miss
        log.debug("Cache miss for method: {} with key: {}", methodName, cacheKey);
        Object result = joinPoint.proceed();

        // Store in cache
        if (result != null) {
            cacheManager.set(cacheKey, result);
        }

        return result;
    }

    /**
     * Generate cache key from method name and arguments
     */
    private String generateCacheKey(String methodName, Object[] args) {
        StringBuilder keyBuilder = new StringBuilder(methodName);
        for (Object arg : args) {
            if (arg != null) {
                keyBuilder.append("::").append(arg.toString());
            }
        }
        return keyBuilder.toString();
    }
}
