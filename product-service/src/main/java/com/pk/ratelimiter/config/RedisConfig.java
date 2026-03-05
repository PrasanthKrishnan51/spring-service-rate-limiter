package com.pk.ratelimiter.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.StaticScriptSource;

import java.util.List;

/**
 * Redis configuration for the service-level rate limiter.
 * Re-uses the same Redis instance as the API Gateway
 * (keys are namespaced to avoid collisions).
 */
@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        var config = new RedisStandaloneConfiguration(redisHost, redisPort);
        return new LettuceConnectionFactory(config);
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    @Bean
    public DefaultRedisScript<List<Long>> tokenBucketScript() {
        String lua = """
            local tokens_key = KEYS[1]
            local timestamp_key = KEYS[2]

            local rate        = tonumber(ARGV[1])
            local capacity    = tonumber(ARGV[2])
            local now         = tonumber(ARGV[3])
            local requested   = tonumber(ARGV[4])

            local ttl = math.ceil(capacity / rate) * 2

            local last_tokens    = tonumber(redis.call('get', tokens_key))
            local last_refreshed = tonumber(redis.call('get', timestamp_key))

            if last_tokens == nil then
                last_tokens = capacity
            end
            if last_refreshed == nil then
                last_refreshed = 0
            end

            local delta         = math.max(0, now - last_refreshed)
            local filled_tokens = math.min(capacity, last_tokens + (delta * rate))
            local allowed       = filled_tokens >= requested
            local new_tokens    = filled_tokens
            local allowed_num   = 0

            if allowed then
                new_tokens  = filled_tokens - requested
                allowed_num = 1
            end

            redis.call('setex', tokens_key,    ttl, new_tokens)
            redis.call('setex', timestamp_key, ttl, now)

            return { allowed_num, new_tokens }
            """;

        var script = new DefaultRedisScript<List<Long>>();
        script.setScriptSource(new StaticScriptSource(lua));
        script.setResultType((Class<List<Long>>) (Class<?>) List.class);
        return script;
    }
}
