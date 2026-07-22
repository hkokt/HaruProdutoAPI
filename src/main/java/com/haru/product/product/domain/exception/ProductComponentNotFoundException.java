package com.haru.product.product.domain.exception;

public final class ProductComponentNotFoundException extends RuntimeException {

	public ProductComponentNotFoundException(Long productId, Long componentId) {
		super("Component with product ID %s was not found in product %s"
				.formatted(componentId, productId));
	}
}
