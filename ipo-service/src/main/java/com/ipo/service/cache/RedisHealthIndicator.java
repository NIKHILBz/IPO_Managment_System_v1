package com.ipo.service.cache;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.logging.Level;
import java.util.logging.Logger;

@Component("redisHealth")
public class RedisHealthIndicator implements HealthIndicator {

    private static final Logger log = Logger.getLogger(RedisHealthIndicator.class.getName());

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Override
    public Health health() {
        var connectionFactory = redisTemplate.getConnectionFactory();
        if (connectionFactory == null) {
            return Health.down()
                    .withDetail("Redis", "Connection factory is not configured")
                    .build();
        }

        try (RedisConnection connection = connectionFactory.getConnection()) {
            // Try a simple ping to Redis
            String result = connection.ping();
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
            log.log(Level.SEVERE, "Redis health check failed", e);
            return Health.down()
                    .withDetail("Redis", "Connection failed")
                    .withException(e)
                    .build();
        }
    }
}
