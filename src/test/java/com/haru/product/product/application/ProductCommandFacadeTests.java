package com.haru.product.product.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;

import com.haru.product.product.application.dto.CreateProductRequest;
import com.haru.product.product.application.dto.AddProductComponentRequest;
import com.haru.product.product.application.dto.ProductDeletionResult;
import com.haru.product.product.application.dto.ProductCompositionResponse;
import com.haru.product.product.application.dto.ProductResponse;
import com.haru.product.product.application.dto.UpdateProductRequest;
import com.haru.product.product.application.search.ProductSearchDocument;
import com.haru.product.product.application.search.ProductSearchGateway;
import com.haru.product.product.domain.MeasurementUnit;
import com.haru.product.product.domain.ProductType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

class ProductCommandFacadeTests {

	private ProductService productService;
	private ProductSearchGateway productSearchGateway;
	private ProductCommandFacade facade;

	@BeforeEach
	void setUp() {
		productService = mock(ProductService.class);
		productSearchGateway = mock(ProductSearchGateway.class);
		facade = new ProductCommandFacade(productService, productSearchGateway);
	}

	@Test
	void indexesACreatedProductAfterTheTransactionalServiceReturns() {
		CreateProductRequest request = createRequest();
		ProductResponse response = productResponse(0);
		when(productService.create(request)).thenReturn(response);

		assertThat(facade.create(request)).isSameAs(response);

		ArgumentCaptor<ProductSearchDocument> document = ArgumentCaptor.forClass(ProductSearchDocument.class);
		InOrder order = inOrder(productService, productSearchGateway);
		order.verify(productService).create(request);
		order.verify(productSearchGateway).put(document.capture());
		assertThat(document.getValue().id()).isEqualTo(1L);
		assertThat(document.getValue().databaseVersion()).isZero();
		assertThat(document.getValue().externalVersion()).isEqualTo(1L);
		assertThat(document.getValue().deleted()).isFalse();
	}

	@Test
	void indexesAnUpdatedProductAfterTheTransactionalServiceReturns() {
		UpdateProductRequest request = updateRequest();
		ProductResponse response = productResponse(3);
		when(productService.update(1L, request)).thenReturn(response);

		assertThat(facade.update(1L, request)).isSameAs(response);

		InOrder order = inOrder(productService, productSearchGateway);
		order.verify(productService).update(1L, request);
		order.verify(productSearchGateway).put(ProductSearchDocument.from(response));
	}

	@Test
	void keepsACommittedWriteSuccessfulWhenIndexingFails() {
		CreateProductRequest request = createRequest();
		ProductResponse response = productResponse(0);
		when(productService.create(request)).thenReturn(response);
		doThrow(new IllegalStateException("Elasticsearch is unavailable"))
				.when(productSearchGateway)
				.put(any(ProductSearchDocument.class));

		assertThat(facade.create(request)).isSameAs(response);
	}

	@Test
	void doesNotIndexWhenTheDatabaseWriteFails() {
		CreateProductRequest request = createRequest();
		when(productService.create(request)).thenThrow(new IllegalStateException("database failure"));

		assertThatThrownBy(() -> facade.create(request))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("database failure");
		verify(productSearchGateway, never()).put(any());
	}

	@Test
	void writesAFencedTombstoneAfterDeletion() {
		Instant deletedAt = Instant.parse("2026-07-23T12:00:00Z");
		when(productService.delete(1L)).thenReturn(new ProductDeletionResult(1L, 5L, deletedAt));

		facade.delete(1L);

		ArgumentCaptor<ProductSearchDocument> document = ArgumentCaptor.forClass(ProductSearchDocument.class);
		InOrder order = inOrder(productService, productSearchGateway);
		order.verify(productService).delete(1L);
		order.verify(productSearchGateway).put(document.capture());
		assertThat(document.getValue().id()).isEqualTo(1L);
		assertThat(document.getValue().databaseVersion()).isEqualTo(5L);
		assertThat(document.getValue().externalVersion())
				.isEqualTo(ProductSearchDocument.TOMBSTONE_EXTERNAL_VERSION);
		assertThat(document.getValue().deleted()).isTrue();
		assertThat(document.getValue().updatedAt()).isEqualTo(deletedAt);
	}

	@Test
	void refreshesTheProductDocumentAfterACompositionChangeCommits() {
		AddProductComponentRequest request = new AddProductComponentRequest(
				2L,
				new BigDecimal("50.000000"),
				MeasurementUnit.MILLILITER);
		Instant now = Instant.parse("2026-07-23T12:00:00Z");
		ProductCompositionResponse composition = new ProductCompositionResponse(
				10L,
				2L,
				"Sakura Essence",
				"ESS-SAKURA",
				new BigDecimal("50.000000"),
				MeasurementUnit.MILLILITER,
				now,
				now);
		ProductResponse product = productResponse(4);
		when(productService.addComponent(1L, request)).thenReturn(composition);
		when(productService.getById(1L)).thenReturn(product);

		assertThat(facade.addComponent(1L, request)).isSameAs(composition);

		InOrder order = inOrder(productService, productSearchGateway);
		order.verify(productService).addComponent(1L, request);
		order.verify(productService).getById(1L);
		order.verify(productSearchGateway).put(ProductSearchDocument.from(product));
	}

	private static CreateProductRequest createRequest() {
		return new CreateProductRequest(
				"Sakura Perfume 100 ml",
				"Finished perfume",
				"PERF-SAKURA-100ML",
				ProductType.FINISHED_PRODUCT,
				MeasurementUnit.UNIT,
				true);
	}

	private static UpdateProductRequest updateRequest() {
		return new UpdateProductRequest(
				"Sakura Perfume 100 ml",
				"Finished perfume",
				"PERF-SAKURA-100ML",
				ProductType.FINISHED_PRODUCT,
				MeasurementUnit.UNIT,
				true);
	}

	private static ProductResponse productResponse(long version) {
		Instant now = Instant.parse("2026-07-23T12:00:00Z");
		return new ProductResponse(
				1L,
				"Sakura Perfume 100 ml",
				"Finished perfume",
				"PERF-SAKURA-100ML",
				ProductType.FINISHED_PRODUCT,
				MeasurementUnit.UNIT,
				true,
				now,
				now,
				version,
				List.of());
	}
}
