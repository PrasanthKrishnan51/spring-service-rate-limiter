/*
package com.pk.ratelimiter.controller;

import com.pk.ratelimiter.dto.ProductRequest;
import com.pk.ratelimiter.dto.ProductResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pk.ratelimiter.service.TokenBucketService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

*/
/**
 * Slice test for ProductController — mocks ProductService, uses MockMvc.
 * Rate limiting is disabled in this slice (no Redis needed).
 *//*

@WebMvcTest(
        controllers = ProductController.class,
        excludeAutoConfiguration = {}
)
class ProductControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;

    @MockBean com.pk.ratelimiter.service.ProductService productService;
    @MockBean com.pk.ratelimiter.ratelimit.RateLimitInterceptor rateLimitInterceptor;
    @MockBean com.pk.ratelimiter.service.TokenBucketService tokenBucketService;
    @MockBean com.pk.ratelimiter.ratelimit.ClientKeyResolver clientKeyResolver;
    @MockBean com.pk.ratelimiter.config.RateLimiterProperties rateLimiterProperties;

    private ProductResponse sampleResponse() {
        return new ProductResponse("id1", "WIDGET-A", "desc","sku", BigDecimal.valueOf(9.99), "tools", 10, true, null, null);
    }

    @Test
    void getAll_returns200() throws Exception {
        when(rateLimitInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        when(productService.findAll()).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sku").value("WIDGET-A"));
    }

    @Test
    void getById_returns200() throws Exception {
        when(rateLimitInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        when(productService.findById("id1")).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/v1/products/id1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("id1"));
    }

    @Test
    void create_validPayload_returns201() throws Exception {
        when(rateLimitInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        when(productService.create(any())).thenReturn(sampleResponse());

        ProductRequest req = ProductRequest.builder()
                .name("Widget A").sku("WIDGET-A").price(BigDecimal.valueOf(9.99))
                .category("tools").stock(10).build();

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("WIDGET-A"));
    }

    @Test
    void create_missingName_returns400() throws Exception {
        when(rateLimitInterceptor.preHandle(any(), any(), any())).thenReturn(true);

        String badJson = """
            {"sku":"WIDGET-A","price":9.99,"category":"tools","stock":5}
            """;

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(badJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void delete_returns204() throws Exception {
        when(rateLimitInterceptor.preHandle(any(), any(), any())).thenReturn(true);

        mockMvc.perform(delete("/api/v1/products/id1"))
                .andExpect(status().isNoContent());
    }
}
*/
