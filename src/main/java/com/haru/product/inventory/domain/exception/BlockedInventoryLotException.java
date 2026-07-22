package com.haru.product.inventory.domain.exception;

public final class BlockedInventoryLotException extends RuntimeException {

	public BlockedInventoryLotException(Long lotId, String lotNumber) {
		super("Inventory lot '%s' (ID %s) is blocked".formatted(lotNumber, lotId));
	}
}
