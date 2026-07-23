package com.haru.product.product.application.dto;

import java.util.List;

public record ProductSearchReindexResponse(
		long scanned,
		long indexed,
		long failed,
		List<Long> failedProductIds) {

	public ProductSearchReindexResponse {
		failedProductIds = List.copyOf(failedProductIds);
	}
}
