package com.haru.product.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.haru.product.inventory.infrastructure.persistence.InventoryLotRepository;
import com.haru.product.inventory.infrastructure.persistence.InventoryLotRepository.ProductInventorySummaryProjection;
import com.haru.product.product.application.ProductSearchService;
import com.haru.product.product.application.dto.ProductSearchResultResponse;
import com.haru.product.product.domain.MeasurementUnit;
import com.haru.product.product.domain.ProductType;
import com.haru.product.shared.pagination.OffsetPageResponse;

class InventorySearchServiceTests {

	private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 7, 23);

	private final ProductSearchService productSearchService = mock(ProductSearchService.class);
	private final InventoryLotRepository inventoryLotRepository = mock(InventoryLotRepository.class);
	private final InventorySearchService service = new InventorySearchService(
			productSearchService,
			inventoryLotRepository,
			Clock.fixed(Instant.parse("2026-07-23T12:00:00Z"), ZoneOffset.UTC));

	@Test
	void aggregatesOneDatabaseBatchAndPreservesTheElasticsearchOrderAndMetadata() {
		ProductSearchResultResponse secondProduct = product(2L, "Second product", "PRD-0000000002", false);
		ProductSearchResultResponse firstProduct = product(1L, "First product", "PRD-0000000001", true);
		when(productSearchService.search("product", 15, 20)).thenReturn(new OffsetPageResponse<>(
				List.of(secondProduct, firstProduct),
				15,
				20,
				36,
				true,
				true));
		ProductInventorySummaryProjection firstSummary = mock(ProductInventorySummaryProjection.class);
		when(firstSummary.getProductId()).thenReturn(1L);
		when(firstSummary.getAvailableQuantity()).thenReturn(new BigDecimal("75.000000"));
		when(firstSummary.getLotCount()).thenReturn(3L);
		when(inventoryLotRepository.summarizeByProductIds(List.of(2L, 1L), REFERENCE_DATE))
				.thenReturn(List.of(firstSummary));

		var response = service.search("product", 15, 20);

		assertThat(response.content()).extracting(summary -> summary.productId())
				.containsExactly(2L, 1L);
		assertThat(response.content().get(0).availableQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(response.content().get(0).lotCount()).isZero();
		assertThat(response.content().get(0).active()).isFalse();
		assertThat(response.content().get(1).availableQuantity()).isEqualByComparingTo("75");
		assertThat(response.content().get(1).lotCount()).isEqualTo(3);
		assertThat(response.content()).allSatisfy(summary ->
				assertThat(summary.referenceDate()).isEqualTo(REFERENCE_DATE));
		assertThat(response.offset()).isEqualTo(15);
		assertThat(response.hasPrevious()).isTrue();
		assertThat(response.hasNext()).isTrue();
		verify(inventoryLotRepository).summarizeByProductIds(List.of(2L, 1L), REFERENCE_DATE);
	}

	@Test
	void skipsTheDatabaseAggregateWhenTheProductPageIsEmpty() {
		when(productSearchService.search("missing", 0, 20)).thenReturn(new OffsetPageResponse<>(
				List.of(), 0, 20, 0, false, false));

		var response = service.search("missing", 0, 20);

		assertThat(response.content()).isEmpty();
		verifyNoInteractions(inventoryLotRepository);
	}

	private static ProductSearchResultResponse product(
			Long id,
			String name,
			String sku,
			boolean active) {
		return new ProductSearchResultResponse(
				id,
				name,
				sku,
				ProductType.FINISHED_PRODUCT,
				MeasurementUnit.UNIT,
				active,
				1.0);
	}
}
