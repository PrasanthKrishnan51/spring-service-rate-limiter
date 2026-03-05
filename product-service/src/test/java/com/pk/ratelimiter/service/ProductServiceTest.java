/*
package com.pk.ratelimiter.service;

import com.pk.ratelimiter.dto.ProductListResponse;
import com.pk.ratelimiter.dto.ProductRequest;
import com.pk.ratelimiter.dto.ProductResponse;
import com.pk.ratelimiter.exception.ProductException;
import com.pk.ratelimiter.model.Product;
import com.pk.ratelimiter.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService Unit Tests")
class ProductServiceTest {

    @Mock
    ProductRepository productRepository;
    @InjectMocks
    ProductService productService;

    Product sampleProduct;

    @BeforeEach
    void setUp() {
        sampleProduct = Product.builder()
                .id("abc123")
                .name("Wireless Mouse")
                .description("Ergonomic wireless mouse")
                .sku("SKU-001")
                .price(BigDecimal.valueOf(29.99))
                .category("Electronics")
                .stock(50)
                .build();
    }

    @Nested
    @DisplayName("getAllProducts()")
    class GetAllProducts {

        @Test
        @DisplayName("returns mapped DTOs for every product in repository")
        void returnsAllProducts() {
            when(productRepository.findAll()).thenReturn(List.of(sampleProduct));
            ProductListResponse result = productService.getAllProducts();
            assertThat(result.getProducts().getFirst().getName()).isEqualTo("Wireless Mouse");
            verify(productRepository).findAll();
        }

        @Test
        @DisplayName("should return empty list when repository has no products")
        void shouldReturnEmptyList() {
            when(productRepository.findAll()).thenReturn(Collections.emptyList());
            List<ProductResponse> result = productService.getAllProducts().getProducts();
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getProductById()")
    class GetProductById {

        @Test
        @DisplayName("returns DTO when product exists")
        void found() {
            when(productRepository.findById("abc123")).thenReturn(Optional.of(sampleProduct));
            ProductResponse dto = productService.getProductById("abc123");
            assertThat(dto.getId()).isEqualTo("abc123");
            assertThat(dto.getSku()).isEqualTo("SKU-001");
        }

        @Test
        @DisplayName("throws ProductNotFoundException when product does not exist")
        void notFound() {
            when(productRepository.findById("missing")).thenReturn(Optional.empty());
            assertThatThrownBy(() -> productService.getProductById("missing"))
                    .isInstanceOf(ProductException.ProductNotFoundException.class)
                    .hasMessageContaining("missing");
        }
    }

    @Nested
    @DisplayName("createProduct()")
    class CreateProduct {

        ProductRequest validRequest = new ProductRequest(
                "Mechanical Keyboard",
                "RGB backlit mechanical keyboard",
                "SKU-002",
                BigDecimal.valueOf(89.99),
                "Electronics",
                20
        );

        @Test
        @DisplayName("saves and returns new product when SKU is unique")
        void successfulCreate() {
            when(productRepository.existsBySku("SKU-002")).thenReturn(false);
            when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
                Product p = inv.getArgument(0);
                p.setId("newId");
                return p;
            });

            ProductResponse result = productService.createProduct(validRequest);

            assertThat(result.getName()).isEqualTo("Mechanical Keyboard");
            assertThat(result.getId()).isEqualTo("newId");
            verify(productRepository).save(any(Product.class));
        }

        @Test
        @DisplayName("throws DuplicateSkuException when SKU already exists")
        void duplicateSku() {
            when(productRepository.existsBySku("SKU-002")).thenReturn(true);

            assertThatThrownBy(() -> productService.createProduct(validRequest))
                    .isInstanceOf(ProductException.DuplicateSkuException.class)
                    .hasMessageContaining("SKU-002");

            verify(productRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("deleteProduct()")
    class DeleteProduct {

        @Test
        @DisplayName("deletes product when it exists")
        void successfulDelete() {
            when(productRepository.existsById("abc123")).thenReturn(true);
            productService.deleteProduct("abc123");
            verify(productRepository).deleteById("abc123");
        }

        @Test
        @DisplayName("throws ProductNotFoundException when product does not exist")
        void notFound() {
            when(productRepository.existsById("ghost")).thenReturn(false);
            assertThatThrownBy(() -> productService.deleteProduct("ghost"))
                    .isInstanceOf(ProductException.ProductNotFoundException.class);
            verify(productRepository, never()).deleteById(any());
        }
    }
}
*/
