package com.pk.ratelimiter.service;

import com.mongodb.internal.bulk.UpdateRequest;
import com.pk.ratelimiter.dto.ProductListResponse;
import com.pk.ratelimiter.dto.ProductRequest;
import com.pk.ratelimiter.dto.ProductResponse;
import com.pk.ratelimiter.exception.ProductException;
import com.pk.ratelimiter.model.Product;
import com.pk.ratelimiter.repository.ProductRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Business logic layer.
 *
 * Cache names:
 *   "product"  – single Product keyed by id
 *   "products" – collections (all, by-category, in-stock …)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    @Cacheable(value = "products", key = "'all'")
    public ProductListResponse getAllProducts() {
        log.debug("Cache MISS – fetching all products from MongoDB");
        List<ProductResponse> list =  productRepository.findAll()
                .stream()
                .map(this::toDTO)
                .toList();
        return new ProductListResponse(list);
    }

    @Cacheable(value = "product", key = "#id")
    public ProductResponse getProductById(String id) {
        log.debug("Cache MISS – fetching product {} from MongoDB", id);
        return productRepository.findById(id)
                .map(this::toDTO)
                .orElseThrow(() -> new ProductException.ProductNotFoundException(id));
    }

    @Cacheable(value = "products", key = "'category:' + #category.toLowerCase()")
    public ProductListResponse getProductsByCategory(String category) {
        log.debug("Cache MISS – fetching products by category '{}' from MongoDB", category);
        List<ProductResponse> list =  productRepository.findByCategoryIgnoreCase(category)
                .stream()
                .map(this::toDTO)
                .toList();
        return new ProductListResponse(list);
    }

    @Cacheable(value = "products", key = "'in-stock'")
    public ProductListResponse getInStockProducts() {
        log.debug("Cache MISS – fetching in-stock products from MongoDB");
        List<ProductResponse> list = productRepository.findInStock()
                .stream()
                .map(this::toDTO)
                .toList();
        return new ProductListResponse(list);
    }

    public ProductListResponse searchByName(String name) {
        List<ProductResponse> list = productRepository.findByNameContainingIgnoreCase(name)
                .stream()
                .map(this::toDTO)
                .toList();
        return new ProductListResponse(list);
    }

    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse createProduct(ProductRequest req) {
        if (productRepository.existsBySku(req.getSku())) {
            throw new ProductException.DuplicateSkuException(req.getSku());
        }
        Product product = Product.builder()
                .name(req.getName())
                .description(req.getDescription())
                .sku(req.getSku())
                .price(req.getPrice())
                .category(req.getCategory())
                .stock(req.getStock())
                .build();

        Product saved = productRepository.save(product);
        log.debug("Created product {}", saved.getId());
        return toDTO(saved);
    }

    @Caching(
        put   = { @CachePut(value = "product", key = "#id") },
        evict = { @CacheEvict(value = "products", allEntries = true) }
    )
    public ProductResponse updateProduct(String id, ProductRequest req) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductException.ProductNotFoundException(id));

        product.setName(req.getName());
        product.setDescription(req.getDescription());
        product.setPrice(req.getPrice());
        product.setCategory(req.getCategory());
        product.setStock(req.getStock());

        Product updated = productRepository.save(product);
        log.debug("Updated product {}", updated.getId());
        return toDTO(updated);
    }

    @Caching(evict = {
        @CacheEvict(value = "product",  key = "#id"),
        @CacheEvict(value = "products", allEntries = true)
    })
    public void deleteProduct(String id) {
        if (!productRepository.existsById(id)) {
            throw new ProductException.ProductNotFoundException(id);
        }
        productRepository.deleteById(id);
        log.debug("Deleted product {}", id);
    }

    public List<ProductResponse> bulkCreate(@Valid List<ProductRequest> requests) {
        return List.of();
    }

    public ProductResponse toDTO(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .sku(product.getSku())
                .price(product.getPrice())
                .category(product.getCategory())
                .stock(product.getStock())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
