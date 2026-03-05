package com.pk.ratelimiter.config;

import com.pk.ratelimiter.ratelimit.RateLimitInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the {@link RateLimitInterceptor} to apply to all API paths.
 *
 * <p>Exclusions (actuator, swagger) are added so that health checks and
 * OpenAPI docs are never rate-limited — just like the gateway config.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**")           // rate-limit all API paths
                .excludePathPatterns(
                        "/actuator/**",               // health / metrics — never limit
                        "/swagger-ui/**",             // Swagger UI
                        "/v3/api-docs/**"             // OpenAPI spec
                );
    }
}
