package com.haru.product.inventory.domain.exception;

public final class InvalidInventoryLotException extends RuntimeException {

	public InvalidInventoryLotException(String message) {
		super(message);
	}
}
