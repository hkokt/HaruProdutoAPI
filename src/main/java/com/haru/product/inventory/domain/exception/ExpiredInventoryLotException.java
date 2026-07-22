package com.haru.product.inventory.domain.exception;

public final class ExpiredInventoryLotException extends RuntimeException {

	public ExpiredInventoryLotException(Long lotId, String lotNumber) {
		super("Inventory lot '%s' (ID %s) is expired".formatted(lotNumber, lotId));
	}
}
