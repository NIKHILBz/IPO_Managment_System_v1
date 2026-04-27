package com.ipo.service.cache;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Cacheable {

    /**
     * Cache key pattern
     */
    String key();

    /**
     * Cache TTL in specified TimeUnit
     */
    long ttl() default 10;

    /**
     * TimeUnit for TTL
     */
    TimeUnit timeUnit() default TimeUnit.MINUTES;

    /**
     * Whether to cache null values
     */
    boolean cacheNull() default false;
}
