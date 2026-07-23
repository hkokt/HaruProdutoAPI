package com.haru.product.product.application.dto;

import java.util.List;

public record ProductSearchPageResponse(
		List<ProductSearchResultResponse> content,
		int page,
		int size,
		long totalElements,
		int totalPages) {

	public ProductSearchPageResponse {
		content = content == null ? List.of() : List.copyOf(content);
	}
}
