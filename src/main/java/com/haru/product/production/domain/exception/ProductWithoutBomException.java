package com.haru.product.production.domain.exception;

public final class ProductWithoutBomException extends RuntimeException {

	public ProductWithoutBomException(Long productId) {
		super("Product with ID %s does not have a bill of materials".formatted(productId));
	}

	public ProductWithoutBomException(String productSku) {
		super("Product with SKU '%s' does not have a bill of materials".formatted(productSku));
	}
}
