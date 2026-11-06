package com.claimflow.config;

import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.time.Duration;

@Configuration
public class RedisConfig {

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(RedisConnectionFactory factory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        return container;
    }

    /** Analytics are expensive aggregate queries; 30s staleness is fine for a dashboard. */
    @Bean
    public RedisCacheManagerBuilderCustomizer cacheCustomizer() {
        return builder -> builder.withCacheConfiguration("analytics",
                RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofSeconds(30)).disableCachingNullValues());
    }
}
