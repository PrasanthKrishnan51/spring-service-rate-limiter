package com.pk.ratelimiter.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

public class ProductException extends RuntimeException {

    @ResponseStatus(HttpStatus.NOT_FOUND)
    public static class ProductNotFoundException extends RuntimeException {
        public ProductNotFoundException(String id) {
            super("Product not found with id: " + id);
        }
    }

    @ResponseStatus(HttpStatus.CONFLICT)
    public static class DuplicateSkuException extends RuntimeException {
        public DuplicateSkuException(String sku) {
            super("A product with SKU " + sku + " already exists");
        }
    }
}
