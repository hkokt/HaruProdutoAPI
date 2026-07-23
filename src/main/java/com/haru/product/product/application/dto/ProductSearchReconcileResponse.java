package com.haru.product.product.application.dto;

import java.util.List;

public record ProductSearchReconcileResponse(
		long scanned,
		long tombstoned,
		long failed,
		List<Long> failedProductIds) {

	public ProductSearchReconcileResponse {
		failedProductIds = List.copyOf(failedProductIds);
	}
}
