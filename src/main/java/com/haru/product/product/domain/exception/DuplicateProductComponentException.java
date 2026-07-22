package com.haru.product.product.domain.exception;

public final class DuplicateProductComponentException extends RuntimeException {

	public DuplicateProductComponentException(String componentSku) {
		super("Component with SKU '%s' is already part of this product".formatted(componentSku));
	}
}
