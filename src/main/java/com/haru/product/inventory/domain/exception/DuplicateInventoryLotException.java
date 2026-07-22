package com.haru.product.inventory.domain.exception;

public final class DuplicateInventoryLotException extends RuntimeException {

	public DuplicateInventoryLotException(Long productId, String lotNumber) {
		super("Inventory lot '%s' already exists for product %s"
				.formatted(lotNumber, productId));
	}
}
