package com.pk.ratelimiter.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO for creating / updating a product.
 * Uses Jakarta Validation so bad payloads are rejected before hitting the service.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ProductRequest {

    @NotBlank(message = "name is required")
    @Size(max = 200)
    String name;

    @NotBlank(message = "sku is required")
    @Pattern(regexp = "^[A-Z0-9\\-]+$", message = "SKU must be uppercase alphanumeric with dashes")
    String sku;

    String description;

    @NotNull(message = "price is required")
    @DecimalMin(value = "0.0", inclusive = false, message = "price must be positive")
    BigDecimal price;

    @NotBlank(message = "category is required")
    String category;

    @Min(value = 0, message = "stock cannot be negative")
    int stock;
}
