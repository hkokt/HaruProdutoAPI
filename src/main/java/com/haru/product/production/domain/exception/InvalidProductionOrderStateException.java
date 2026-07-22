package com.haru.product.production.domain.exception;

import com.haru.product.production.domain.ProductionOrderStatus;

public final class InvalidProductionOrderStateException extends RuntimeException {

	public InvalidProductionOrderStateException(String message) {
		super(message);
	}

	public InvalidProductionOrderStateException(
			Long orderId,
			ProductionOrderStatus currentStatus,
			String operation) {
		super("Production order %s cannot be %s while its status is %s"
				.formatted(orderId, operation, currentStatus));
	}
}
