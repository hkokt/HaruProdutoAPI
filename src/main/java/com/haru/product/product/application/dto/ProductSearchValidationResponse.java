package com.haru.product.product.application.dto;

import java.util.List;

public record ProductSearchValidationResponse(
		long databaseProductCount,
		long liveIndexDocumentCount,
		long matchingCount,
		long missingCount,
		long staleCount,
		long orphanCount,
		List<Long> missingProductIds,
		List<Long> staleProductIds,
		boolean consistent) {

	public ProductSearchValidationResponse {
		missingProductIds = List.copyOf(missingProductIds);
		staleProductIds = List.copyOf(staleProductIds);
	}
}
