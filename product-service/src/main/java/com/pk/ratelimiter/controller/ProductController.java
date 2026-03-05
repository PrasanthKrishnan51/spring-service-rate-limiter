package com.pk.ratelimiter.controller;

import com.pk.ratelimiter.annotation.RateLimit;
import com.pk.ratelimiter.dto.ProductListResponse;
import com.pk.ratelimiter.dto.ProductRequest;
import com.pk.ratelimiter.dto.ProductResponse;
import com.pk.ratelimiter.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "Products", description = "CRUD operations for the product catalog")
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "Get all products", description = "Returns every product in the catalog")
    @GetMapping
    public ResponseEntity<ProductListResponse> getAllProducts() {
        return ResponseEntity.ok(productService.getAllProducts());
    }

    @Operation(summary = "Get product by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Product found"),
        @ApiResponse(responseCode = "404", description = "Product not found", content = @Content)
    })
    @RateLimit(replenishRate = 5, burstCapacity = 10, key = "products:get")
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProductById(
            @Parameter(description = "MongoDB document ID") @PathVariable String id) {
        return ResponseEntity.ok(productService.getProductById(id));
    }

    @Operation(summary = "Get products by category", description = "Case-insensitive category filter. Cached per category.")
    @RateLimit(replenishRate = 3, burstCapacity = 5, key = "products:by-category")
    @GetMapping("/category/{category}")
    public ResponseEntity<ProductListResponse> getByCategory(@PathVariable String category) {
        return ResponseEntity.ok(productService.getProductsByCategory(category));
    }

    @Operation(summary = "Get in-stock products", description = "Returns only products with stock > 0.")
    @RateLimit(replenishRate = 2, burstCapacity = 4, key = "products:instock")
    @GetMapping("/in-stock")
    public ResponseEntity<ProductListResponse> getInStock() {
        return ResponseEntity.ok(productService.getInStockProducts());
    }

    @Operation(summary = "Search products by name (partial match, not cached)")
    @RateLimit(replenishRate = 5, burstCapacity = 10, key = "products:search")
    @GetMapping("/search")
    public ResponseEntity<ProductListResponse> searchByName(
            @Parameter(description = "Product name fragment") @RequestParam String name) {
        return ResponseEntity.ok(productService.searchByName(name));
    }

    @Operation(summary = "Create a new product")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Product created"),
        @ApiResponse(responseCode = "400", description = "Validation error", content = @Content),
        @ApiResponse(responseCode = "409", description = "SKU already exists", content = @Content)
    })
    @RateLimit(replenishRate = 2, burstCapacity = 5, key = "products:create")
    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(
            @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.createProduct(request));
    }

    @Operation(summary = "Update an existing product")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Product updated"),
        @ApiResponse(responseCode = "404", description = "Product not found", content = @Content)
    })
    @RateLimit(replenishRate = 1, burstCapacity = 3, key = "products:update")
    @PutMapping("/{id}")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable String id,
            @Valid @RequestBody ProductRequest request) {
        return ResponseEntity.ok(productService.updateProduct(id, request));
    }

    @Operation(summary = "Delete a product")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Product deleted"),
        @ApiResponse(responseCode = "404", description = "Product not found", content = @Content)
    })
    @RateLimit(replenishRate = 1, burstCapacity = 3, key = "products:delete")
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteProduct(@PathVariable String id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok(Map.of("message", "Product deleted successfully", "id", id));
    }

    /**
     * Bulk create — expensive operation, tighter dedicated bucket (1 req/s, burst 3).
     * Global write bucket also applies — both must pass.
     */
    @Operation(summary = "Bulk create products")
    @RateLimit(replenishRate = 1, burstCapacity = 3, key = "products:bulk-create")
    @PostMapping("/bulk")
    public ResponseEntity<List<ProductResponse>> bulkCreate(@Valid @RequestBody List<ProductRequest> requests) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productService.bulkCreate(requests));
    }
}
