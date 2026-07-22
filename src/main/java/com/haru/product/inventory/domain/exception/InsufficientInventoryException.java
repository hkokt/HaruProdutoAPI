package com.haru.product.inventory.domain.exception;

import java.math.BigDecimal;

public final class InsufficientInventoryException extends RuntimeException {

	public InsufficientInventoryException(
			BigDecimal requestedQuantity,
			BigDecimal availableQuantity) {
		super("Insufficient inventory: requested %s, available %s"
				.formatted(requestedQuantity, availableQuantity));
	}

	public InsufficientInventoryException(
			Long productId,
			BigDecimal requestedQuantity,
			BigDecimal availableQuantity) {
		super("Insufficient inventory for product %s: requested %s, available %s"
				.formatted(productId, requestedQuantity, availableQuantity));
	}
}
