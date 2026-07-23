package com.haru.product.product.application.search;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.haru.product.product.application.dto.ProductSearchResultResponse;
import com.haru.product.shared.pagination.OffsetPageResponse;

public interface ProductSearchGateway {

	void put(ProductSearchDocument document);

	void putWithoutRefresh(ProductSearchDocument document);

	void refresh();

	OffsetPageResponse<ProductSearchResultResponse> search(String query, long offset, int limit);

	Map<Long, Long> findDatabaseVersions(Collection<Long> productIds);

	List<ProductSearchIndexEntry> findLiveDocumentsAfter(Long productId, int size);

	long countLiveDocuments();
}
