package com.pk.ratelimiter.controller;

import com.pk.ratelimiter.config.RateLimiterProperties;
import com.pk.ratelimiter.service.TokenBucketService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin endpoints for inspecting and managing service-level rate limit buckets.
 *
 * <p>Mirrors the Gateway's {@code /gateway/rate-limit/status} endpoint so
 * operators have consistent tooling across both layers.
 *
 * <pre>
 *   GET    /service/rate-limit/status?key=127.0.0.1
 *   DELETE /service/rate-limit/reset?key=127.0.0.1
 *   GET    /service/rate-limit/config
 * </pre>
 */
@RestController
@RequestMapping("/service/rate-limit")
@RequiredArgsConstructor
public class RateLimitStatusController {

    private final TokenBucketService tokenBucketService;
    private final RateLimiterProperties properties;

    /**
     * Peek at the current token count for a given client key.
     *
     * @param key the client key to inspect
     *            e.g. "ip:127.0.0.1", "api-key:my-key-123", "user:user-42"
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status(@RequestParam String key) {
        long readTokens  = tokenBucketService.peek(key + ":read");
        long writeTokens = tokenBucketService.peek(key + ":write");

        return ResponseEntity.ok(Map.of(
                "key",                key,
                "readBucket",  Map.of(
                        "remainingTokens",  readTokens == -1 ? "bucket not yet created" : readTokens,
                        "replenishRate",    properties.getRead().getReplenishRate(),
                        "burstCapacity",    properties.getRead().getBurstCapacity()
                ),
                "writeBucket", Map.of(
                        "remainingTokens",  writeTokens == -1 ? "bucket not yet created" : writeTokens,
                        "replenishRate",    properties.getWrite().getReplenishRate(),
                        "burstCapacity",    properties.getWrite().getBurstCapacity()
                )
        ));
    }

    /**
     * Reset both read and write buckets for a given client key.
     * Useful in testing and for unblocking a client after a mistake.
     */
    @DeleteMapping("/reset")
    public ResponseEntity<Map<String, Object>> reset(@RequestParam String key) {
        tokenBucketService.reset(key + ":read");
        tokenBucketService.reset(key + ":write");
        return ResponseEntity.ok(Map.of(
                "key",     key,
                "message", "Read and write buckets reset successfully"
        ));
    }

    /**
     * View the active rate-limit configuration.
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> config() {
        return ResponseEntity.ok(Map.of(
                "enabled",     properties.isEnabled(),
                "keyStrategy", properties.getKeyStrategy(),
                "readBucket",  Map.of(
                        "replenishRate",   properties.getRead().getReplenishRate(),
                        "burstCapacity",   properties.getRead().getBurstCapacity(),
                        "requestedTokens", properties.getRead().getRequestedTokens(),
                        "methods",         properties.getRead().getMethods()
                ),
                "writeBucket", Map.of(
                        "replenishRate",   properties.getWrite().getReplenishRate(),
                        "burstCapacity",   properties.getWrite().getBurstCapacity(),
                        "requestedTokens", properties.getWrite().getRequestedTokens(),
                        "methods",         properties.getWrite().getMethods()
                )
        ));
    }
}
