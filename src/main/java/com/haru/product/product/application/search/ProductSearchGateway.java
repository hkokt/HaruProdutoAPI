package com.haru.product.product.application.search;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.haru.product.product.application.dto.ProductSearchPageResponse;

public interface ProductSearchGateway {

	void put(ProductSearchDocument document);

	void putWithoutRefresh(ProductSearchDocument document);

	void refresh();

	ProductSearchPageResponse search(String query, int page, int size);

	Map<Long, Long> findDatabaseVersions(Collection<Long> productIds);

	List<ProductSearchIndexEntry> findLiveDocumentsAfter(Long productId, int size);

	long countLiveDocuments();
}
