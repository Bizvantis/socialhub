package com.socialhub.userservice.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.time.Duration;

/**
 * Redis caching. Set app.cache.enabled=false to switch it off entirely
 * (kill switch for incidents, and a clean baseline for benchmarks).
 */
@Configuration
@EnableCaching
@ConditionalOnProperty(prefix = "app.cache", name = "enabled", havingValue = "true", matchIfMissing = true)
public class CacheConfig {

    @Bean
    RedisCacheManager cacheManager(RedisConnectionFactory cf) {
        RedisCacheConfiguration cfg = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .prefixCacheNameWith("user-service:")
                .disableCachingNullValues();

        return RedisCacheManager.builder(cf)
                .cacheDefaults(cfg)
                .build();
    }
}
