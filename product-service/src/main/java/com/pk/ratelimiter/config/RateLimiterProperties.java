package com.pk.ratelimiter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Strongly-typed binding for the {@code rate-limiter.*} YAML block.
 * Mirrors the Gateway's RedisRateLimiter parameters so that both layers
 * share identical token-bucket semantics.
 */
@Data
@Component
@ConfigurationProperties(prefix = "rate-limiter")
public class RateLimiterProperties {

    /** Globally enable / disable service-level rate limiting. */
    private boolean enabled = true;

    private KeyStrategy keyStrategy = KeyStrategy.API_KEY;

    /** Redis key namespace — keeps service keys separate from gateway keys. */
    private String redisKeyPrefix = "svc_rate_limiter";

    private RouteConfig read  = new RouteConfig();
    private RouteConfig write = new RouteConfig();

    public enum KeyStrategy {
        IP, API_KEY, USER_ID
    }

    @Data
    public static class RouteConfig {
        /** Tokens added to the bucket per second. */
        private int replenishRate = 10;

        /** Maximum tokens the bucket can hold (burst allowance). */
        private int burstCapacity = 20;

        /** Tokens consumed per request (almost always 1). */
        private int requestedTokens = 1;

        /**
         * Comma-separated HTTP methods this bucket applies to.
         * e.g. "GET" or "POST,PUT,DELETE,PATCH"
         */
        private String methods = "GET";

        public List<String> methodList() {
            return List.of(methods.split(","));
        }
    }
}
