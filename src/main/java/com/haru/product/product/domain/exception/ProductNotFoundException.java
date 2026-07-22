package com.haru.product.product.domain.exception;

public final class ProductNotFoundException extends RuntimeException {

	public ProductNotFoundException(Long id) {
		super("Product with ID %s was not found".formatted(id));
	}
}
