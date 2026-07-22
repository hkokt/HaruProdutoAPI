package com.haru.product.production.domain.exception;

public final class DuplicateProducedLotException extends RuntimeException {

	public DuplicateProducedLotException(String lotNumber) {
		super("Produced inventory lot '%s' already exists".formatted(lotNumber));
	}

	public DuplicateProducedLotException(Long productId, String lotNumber) {
		super("Produced inventory lot '%s' already exists for product %s"
				.formatted(lotNumber, productId));
	}
}
