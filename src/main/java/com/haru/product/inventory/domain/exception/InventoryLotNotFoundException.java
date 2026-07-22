package com.haru.product.inventory.domain.exception;

public final class InventoryLotNotFoundException extends RuntimeException {

	public InventoryLotNotFoundException(Long id) {
		super("Inventory lot with ID %s was not found".formatted(id));
	}
}
