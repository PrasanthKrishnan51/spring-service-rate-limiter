package com.pk.ratelimiter.service;

import com.pk.ratelimiter.config.RateLimiterProperties;
import com.pk.ratelimiter.dto.RateLimitReponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Executes the Token Bucket Lua script atomically against Redis.
 *
 * <p>Key naming convention (intentionally different from the gateway):
 * <pre>
 *   {prefix}.{clientKey}.tokens      ← current token count
 *   {prefix}.{clientKey}.timestamp   ← last refill epoch-second
 * </pre>
 *
 * e.g. {@code svc_rate_limiter.127.0.0.1.tokens}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBucketService {

    private final StringRedisTemplate        redisTemplate;
    private final DefaultRedisScript<List<Long>> tokenBucketScript;
    private final RateLimiterProperties properties;

    /**
     * Attempt to consume {@code requestedTokens} from the named bucket.
     *
     * @param clientKey      resolved client identifier (IP, API key, user-id)
     * @param replenishRate  tokens added per second
     * @param burstCapacity  maximum bucket size
     * @param requestedTokens tokens to consume (usually 1)
     * @return evaluation result with allowed flag and remaining tokens
     */
    public RateLimitReponse tryConsume(
            String clientKey, int replenishRate, int burstCapacity, int requestedTokens) {

        String prefix    = properties.getRedisKeyPrefix();
        String tokensKey = prefix + "." + clientKey + ".tokens";
        String tsKey     = prefix + "." + clientKey + ".timestamp";
        long   now       = Instant.now().getEpochSecond();

        try {
            List<Long> result = redisTemplate.execute(
                    tokenBucketScript,
                    List.of(tokensKey, tsKey),
                    String.valueOf(replenishRate),
                    String.valueOf(burstCapacity),
                    String.valueOf(now),
                    String.valueOf(requestedTokens)
            );

            if (result == null || result.size() < 2) {
                log.warn("Token bucket script returned null for key={}, failing open", clientKey);
                return new RateLimitReponse(true, burstCapacity, replenishRate, burstCapacity);
            }

            boolean allowed   = result.get(0) == 1L;
            long    remaining = result.get(1);

            log.debug("Rate limit check: clientKey={}, allowed={}, remaining={}", clientKey, allowed, remaining);
            return new RateLimitReponse(allowed, remaining, replenishRate, burstCapacity);

        } catch (Exception ex) {
            log.error("Redis error during rate limit check for key={}: {}", clientKey, ex.getMessage());
            return new RateLimitReponse(true, burstCapacity, replenishRate, burstCapacity);
        }
    }

    /**
     * Reset the token bucket for a client (useful for testing / admin ops).
     */
    public void reset(String clientKey) {
        String prefix    = properties.getRedisKeyPrefix();
        String tokensKey = prefix + "." + clientKey + ".tokens";
        String tsKey     = prefix + "." + clientKey + ".timestamp";
        redisTemplate.delete(List.of(tokensKey, tsKey));
        log.info("Token bucket reset for clientKey={}", clientKey);
    }

    /**
     * Read current token count without consuming (non-destructive peek).
     */
    public long peek(String clientKey) {
        String tokensKey = properties.getRedisKeyPrefix() + "." + clientKey + ".tokens";
        String val = redisTemplate.opsForValue().get(tokensKey);
        return val != null ? Long.parseLong(val) : -1L;
    }
}
