package com.pk.ratelimiter.dto;

/**
 * Immutable result from one token-bucket evaluation.
 *
 * @param allowed        true  → request may proceed
 * @param remainingTokens tokens left in the bucket after this request
 * @param replenishRate  tokens added per second (for Retry-After calculation)
 * @param burstCapacity  max bucket size
 */
public record RateLimitReponse(
        boolean allowed,
        long    remainingTokens,
        int     replenishRate,
        int     burstCapacity
) {
    /** Approximate seconds until one token is replenished. */
    public long retryAfterSeconds() {
        return allowed ? 0L : Math.max(1L, 1L);
    }
}
