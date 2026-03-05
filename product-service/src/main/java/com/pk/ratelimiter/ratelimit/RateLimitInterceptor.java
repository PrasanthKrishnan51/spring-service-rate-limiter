package com.pk.ratelimiter.ratelimit;

import com.pk.ratelimiter.config.RateLimiterProperties;
import com.pk.ratelimiter.dto.RateLimitReponse;
import com.pk.ratelimiter.service.TokenBucketService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * Global {@link HandlerInterceptor} that enforces Token Bucket rate limiting
 * on every incoming HTTP request — before it reaches a controller.
 *
 * <p>Two buckets are maintained per client (keyed by the configured strategy):
 * <ul>
 *   <li><b>read  bucket</b> — applied to GET requests</li>
 *   <li><b>write bucket</b> — applied to POST / PUT / DELETE / PATCH</li>
 * </ul>
 *
 * <p>This mirrors the API Gateway's route-level buckets but operates inside
 * the service, acting as a second line of defence for direct or internal calls.
 *
 * <p>Response headers on every request:
 * <pre>
 *   X-RateLimit-Remaining  : tokens left in the bucket
 *   X-RateLimit-Limit      : burst capacity
 * </pre>
 *
 * <p>On 429:
 * <pre>
 *   X-RateLimit-Retry-After : seconds to wait
 *   X-RateLimit-Message      : human-readable explanation
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiterProperties properties;
    private final TokenBucketService tokenBucketService;
    private final ClientKeyResolver clientKeyResolver;

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) throws IOException {

        // Skip if rate limiting is globally disabled
        if (!properties.isEnabled()) {
            return true;
        }

        String method    = request.getMethod().toUpperCase();
        String clientKey = clientKeyResolver.resolve(request);

        // Select bucket config based on HTTP method
        RateLimiterProperties.RouteConfig bucketConfig = resolveBucketConfig(method);

        // Incorporate method type into the Redis key so read/write use separate buckets
        String bucketType  = isReadMethod(method) ? "read" : "write";
        String fullKey     = clientKey + ":" + bucketType;

        RateLimitReponse result = tokenBucketService.tryConsume(
                fullKey,
                bucketConfig.getReplenishRate(),
                bucketConfig.getBurstCapacity(),
                bucketConfig.getRequestedTokens()
        );

        // Always add informational headers
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remainingTokens()));
        response.setHeader("X-RateLimit-Limit",     String.valueOf(result.burstCapacity()));

        if (result.allowed()) {
            return true; // proceed to controller
        }

        // ── 429 Too Many Requests ─────────────────────────────────────────────
        long retryAfter = result.retryAfterSeconds();
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("X-RateLimit-Retry-After", String.valueOf(retryAfter));
        response.setHeader("X-RateLimit-Message",
                "Too many requests. Token bucket exhausted. Retry after " + retryAfter + " second(s).");

        String body = """
                {
                  "status": 429,
                  "error": "Too Many Requests",
                  "message": "Service-level rate limit exceeded. Retry after %d second(s).",
                  "retryAfter": %d,
                  "remainingTokens": %d
                }
                """.formatted(retryAfter, retryAfter, result.remainingTokens());

        response.getWriter().write(body);

        log.warn("Rate limit exceeded: clientKey={}, method={}, remaining={}", clientKey, method, result.remainingTokens());
        return false;
    }

    private RateLimiterProperties.RouteConfig resolveBucketConfig(String method) {
        if (isReadMethod(method)) {
            return properties.getRead();
        }
        return properties.getWrite();
    }

    private boolean isReadMethod(String method) {
        return "GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method);
    }
}
