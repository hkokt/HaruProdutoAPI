package com.haru.product.product.domain.exception;

public final class DuplicateProductSkuException extends RuntimeException {

	public DuplicateProductSkuException(String sku) {
		super("A product with SKU '%s' already exists".formatted(sku));
	}
}
