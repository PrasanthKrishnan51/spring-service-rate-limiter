package com.pk.ratelimiter.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Apply a custom Token Bucket rate limit to a specific controller method.
 *
 * <p>When present on a method, this overrides the global read/write route
 * config from {@code application.yml} for that endpoint only.
 *
 * <pre>{@code
 * @RateLimit(replenishRate = 2, burstCapacity = 5)
 * @PostMapping("/products/bulk")
 * public ResponseEntity<?> bulkCreate(...) { ... }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** Tokens replenished per second. */
    int replenishRate() default 10;

    /** Maximum token bucket size (burst allowance). */
    int burstCapacity() default 20;

    /** Tokens consumed per invocation. */
    int requestedTokens() default 1;

    /**
     * Optional logical name for the bucket.
     * Defaults to the fully-qualified method signature.
     * Override to share a bucket across multiple endpoints.
     */
    String key() default "";
}
