package com.haru.product.inventory.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.haru.product.inventory.application.dto.InventoryProductSummaryResponse;
import com.haru.product.inventory.infrastructure.persistence.InventoryLotRepository;
import com.haru.product.inventory.infrastructure.persistence.InventoryLotRepository.ProductInventorySummaryProjection;
import com.haru.product.product.application.ProductSearchService;
import com.haru.product.product.application.dto.ProductSearchResultResponse;
import com.haru.product.shared.pagination.OffsetPageResponse;

@Service
public class InventorySearchService {

	private final ProductSearchService productSearchService;
	private final InventoryLotRepository inventoryLotRepository;
	private final Clock clock;

	public InventorySearchService(
			ProductSearchService productSearchService,
			InventoryLotRepository inventoryLotRepository,
			Clock clock) {
		this.productSearchService = productSearchService;
		this.inventoryLotRepository = inventoryLotRepository;
		this.clock = clock;
	}

	public OffsetPageResponse<InventoryProductSummaryResponse> search(
			String query,
			long offset,
			int limit) {
		OffsetPageResponse<ProductSearchResultResponse> products = productSearchService.search(
				query,
				offset,
				limit);
		LocalDate referenceDate = LocalDate.now(clock);
		Map<Long, ProductInventorySummaryProjection> summaries = loadSummaries(
				products.content(),
				referenceDate);
		List<InventoryProductSummaryResponse> content = products.content().stream()
				.map(product -> toResponse(product, summaries.get(product.id()), referenceDate))
				.toList();
		return new OffsetPageResponse<>(
				content,
				products.offset(),
				products.limit(),
				products.totalElements(),
				products.hasPrevious(),
				products.hasNext());
	}

	private Map<Long, ProductInventorySummaryProjection> loadSummaries(
			List<ProductSearchResultResponse> products,
			LocalDate referenceDate) {
		if (products.isEmpty()) {
			return Map.of();
		}
		List<Long> productIds = products.stream()
				.map(ProductSearchResultResponse::id)
				.toList();
		return inventoryLotRepository.summarizeByProductIds(productIds, referenceDate).stream()
				.collect(Collectors.toUnmodifiableMap(
						ProductInventorySummaryProjection::getProductId,
						Function.identity()));
	}

	private static InventoryProductSummaryResponse toResponse(
			ProductSearchResultResponse product,
			ProductInventorySummaryProjection summary,
			LocalDate referenceDate) {
		return new InventoryProductSummaryResponse(
				product.id(),
				product.name(),
				product.sku(),
				product.defaultMeasurementUnit(),
				product.active(),
				summary == null ? BigDecimal.ZERO : summary.getAvailableQuantity(),
				summary == null ? 0 : summary.getLotCount(),
				referenceDate);
	}
}
