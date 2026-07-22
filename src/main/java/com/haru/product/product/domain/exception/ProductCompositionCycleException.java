package com.haru.product.product.domain.exception;

public final class ProductCompositionCycleException extends RuntimeException {

	public ProductCompositionCycleException(String parentSku, String componentSku) {
		super("Adding product '%s' as a component of '%s' would create a composition cycle"
				.formatted(componentSku, parentSku));
	}
}
