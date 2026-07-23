package com.haru.product.product.application;

import org.springframework.stereotype.Service;

import com.haru.product.product.application.dto.ProductSearchResultResponse;
import com.haru.product.product.application.exception.InvalidProductSearchRequestException;
import com.haru.product.product.application.search.ProductSearchGateway;
import com.haru.product.shared.pagination.OffsetPageResponse;

@Service
public class ProductSearchService {

	private final ProductSearchGateway productSearchGateway;

	public ProductSearchService(ProductSearchGateway productSearchGateway) {
		this.productSearchGateway = productSearchGateway;
	}

	public OffsetPageResponse<ProductSearchResultResponse> search(String query, long offset, int limit) {
		String normalizedQuery = query == null ? "" : query.strip();
		if (normalizedQuery.length() > 150
				|| offset < 0
				|| limit < 1
				|| limit > 50
				|| offset > 10_000 - limit) {
			throw new InvalidProductSearchRequestException();
		}
		return productSearchGateway.search(normalizedQuery, offset, limit);
	}
}
