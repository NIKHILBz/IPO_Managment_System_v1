package com.ipo.service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "cache")
public class CacheProperties {

    private boolean enabled = true;
    private long defaultTtl = 10;
    private long shortTtl = 5;
    private long longTtl = 60;
    private boolean cacheNullValues = false;
    private Map<String, CachePolicyConfig> policies = new HashMap<>();

    @Data
    public static class CachePolicyConfig {
        private long ttl = 10;
        private boolean cacheNull = false;
    }
}
