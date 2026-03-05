package com.pk.ratelimiter.ratelimit;

import com.pk.ratelimiter.config.RateLimiterProperties;
import com.pk.ratelimiter.config.RateLimiterProperties.KeyStrategy;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;


/**
 * Resolves the per-client rate-limit key from an incoming HTTP request.
 *
 * <p>Mirrors the Gateway's key-resolver strategies:
 * <ul>
 *   <li>{@code API_KEY}  → {@code X-API-Key} header, falls back to IP</li>
 *   <li>{@code USER_ID}  → {@code X-User-Id} header, falls back to IP</li>
 *   <li>{@code IP}       → {@code X-Forwarded-For} → {@code remoteAddr}</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class ClientKeyResolver {

    private final RateLimiterProperties properties;

    public String resolve(HttpServletRequest request) {
        KeyStrategy strategy = properties.getKeyStrategy();

        return switch (strategy) {
            case API_KEY -> {
                String apiKey = request.getHeader("X-API-Key");
                yield StringUtils.hasText(apiKey)
                        ? "api-key:" + apiKey
                        : "ip:" + extractIp(request);
            }
            case USER_ID -> {
                String userId = request.getHeader("X-User-Id");
                yield StringUtils.hasText(userId)
                        ? "user:" + userId
                        : "ip:" + extractIp(request);
            }
            case IP -> "ip:" + extractIp(request);
        };
    }

    private String extractIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            // X-Forwarded-For can be "client, proxy1, proxy2" — take the first entry
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
