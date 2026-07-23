package com.haru.product.product.application;

import org.springframework.stereotype.Service;

import com.haru.product.product.application.dto.ProductSearchPageResponse;
import com.haru.product.product.application.exception.InvalidProductSearchRequestException;
import com.haru.product.product.application.search.ProductSearchGateway;

@Service
public class ProductSearchService {

	private final ProductSearchGateway productSearchGateway;

	public ProductSearchService(ProductSearchGateway productSearchGateway) {
		this.productSearchGateway = productSearchGateway;
	}

	public ProductSearchPageResponse search(String query, int page, int size) {
		String normalizedQuery = query == null ? "" : query.strip();
		if (normalizedQuery.isEmpty()
				|| normalizedQuery.length() > 150
				|| page < 0
				|| size < 1
				|| size > 50
				|| ((long) page * size) + size > 10_000) {
			throw new InvalidProductSearchRequestException();
		}
		return productSearchGateway.search(normalizedQuery, page, size);
	}
}
