package com.haru.product.production.domain.exception;

public final class ProductionOrderNotFoundException extends RuntimeException {

	public ProductionOrderNotFoundException(Long id) {
		super("Production order with ID %s was not found".formatted(id));
	}
}
