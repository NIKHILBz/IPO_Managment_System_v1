package com.ipo.service.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component("redisHealth")
public class RedisHealthIndicator implements HealthIndicator {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public Health health() {
        try {
            // Try a simple ping to Redis
            String result = redisTemplate.getConnectionFactory().getConnection().ping();
            if ("PONG".equalsIgnoreCase(result)) {
                return Health.up()
                        .withDetail("Redis", "Connected")
                        .withDetail("server", "Redis is running")
                        .build();
            } else {
                return Health.down()
                        .withDetail("Redis", "Unexpected response")
                        .build();
            }
        } catch (Exception e) {
            log.error("Redis health check failed", e);
            return Health.down()
                    .withDetail("Redis", "Connection failed")
                    .withException(e)
                    .build();
        }
    }
}
