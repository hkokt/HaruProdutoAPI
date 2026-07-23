package com.haru.product.product.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import com.haru.product.product.application.dto.ProductSearchReconcileResponse;
import com.haru.product.product.application.dto.ProductSearchReindexResponse;
import com.haru.product.product.application.dto.ProductSearchValidationResponse;
import com.haru.product.product.application.exception.ProductSearchUnavailableException;
import com.haru.product.product.application.exception.ProductSearchVersionConflictException;
import com.haru.product.product.application.search.ProductSearchDocument;
import com.haru.product.product.application.search.ProductSearchGateway;
import com.haru.product.product.application.search.ProductSearchIndexEntry;
import com.haru.product.product.domain.MeasurementUnit;
import com.haru.product.product.domain.Product;
import com.haru.product.product.domain.ProductType;
import com.haru.product.product.infrastructure.persistence.ProductRepository;

class ProductSearchMaintenanceServiceTests {

	private ProductRepository productRepository;
	private ProductSearchGateway productSearchGateway;
	private ProductSearchMaintenanceService service;

	@BeforeEach
	void setUp() {
		productRepository = mock(ProductRepository.class);
		productSearchGateway = mock(ProductSearchGateway.class);
		service = new ProductSearchMaintenanceService(productRepository, productSearchGateway);
	}

	@Test
	void reindexesEveryDatabasePageAndReportsIndividualFailures() {
		Product firstProduct = product(1L, 4L);
		Product secondProduct = product(2L, 7L);
		when(productRepository.findAllByIdGreaterThanOrderByIdAsc(anyLong(), any(Pageable.class)))
				.thenReturn(
						List.of(firstProduct),
						List.of(secondProduct),
						List.of());
		ProductSearchDocument failedDocument = ProductSearchDocument.from(secondProduct);
		doThrow(new IllegalStateException("index unavailable"))
				.when(productSearchGateway)
				.putWithoutRefresh(failedDocument);

		ProductSearchReindexResponse response = service.reindex();

		assertThat(response.scanned()).isEqualTo(2);
		assertThat(response.indexed()).isEqualTo(1);
		assertThat(response.failed()).isEqualTo(1);
		assertThat(response.failedProductIds()).containsExactly(2L);
		verify(productSearchGateway).refresh();

		ArgumentCaptor<Long> cursor = ArgumentCaptor.forClass(Long.class);
		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(productRepository, org.mockito.Mockito.times(3))
				.findAllByIdGreaterThanOrderByIdAsc(cursor.capture(), pageable.capture());
		assertThat(cursor.getAllValues()).containsExactly(0L, 1L, 2L);
		assertThat(pageable.getAllValues())
				.allSatisfy(request -> {
					assertThat(request.getPageSize()).isEqualTo(200);
					assertThat(request.getPageNumber()).isZero();
				});
	}

	@Test
	void stopsReindexingWhenElasticsearchIsUnavailable() {
		Product firstProduct = product(1L, 4L);
		when(productRepository.findAllByIdGreaterThanOrderByIdAsc(anyLong(), any(Pageable.class)))
				.thenReturn(List.of(firstProduct));
		doThrow(new ProductSearchUnavailableException("Product search is temporarily unavailable"))
				.when(productSearchGateway)
				.putWithoutRefresh(any(ProductSearchDocument.class));

		assertThatThrownBy(service::reindex)
				.isInstanceOf(ProductSearchUnavailableException.class);
		verify(productSearchGateway, never()).refresh();
	}

	@Test
	void treatsAnEqualLiveVersionAsAnIdempotentReindex() {
		Product firstProduct = product(1L, 4L);
		when(productRepository.findAllByIdGreaterThanOrderByIdAsc(anyLong(), any(Pageable.class)))
				.thenReturn(List.of(firstProduct), List.of());
		doThrow(new ProductSearchVersionConflictException(1L))
				.when(productSearchGateway)
				.putWithoutRefresh(any(ProductSearchDocument.class));
		when(productSearchGateway.findDatabaseVersions(List.of(1L))).thenReturn(Map.of(1L, 4L));

		ProductSearchReindexResponse response = service.reindex();

		assertThat(response.scanned()).isEqualTo(1);
		assertThat(response.indexed()).isEqualTo(1);
		assertThat(response.failed()).isZero();
		verify(productSearchGateway, never()).refresh();
	}

	@Test
	void validatesMatchingMissingStaleAndOrphanDocuments() {
		Product matchingProduct = product(1L, 4L);
		Product staleProduct = product(2L, 7L);
		Product missingProduct = product(3L, 9L);
		when(productRepository.findAllByIdGreaterThanOrderByIdAsc(anyLong(), any(Pageable.class)))
				.thenReturn(List.of(matchingProduct, staleProduct, missingProduct), List.of());
		when(productSearchGateway.findDatabaseVersions(anyCollection()))
				.thenReturn(Map.of(1L, 4L, 2L, 6L));
		when(productSearchGateway.countLiveDocuments()).thenReturn(3L);

		ProductSearchValidationResponse response = service.validate();

		assertThat(response.databaseProductCount()).isEqualTo(3);
		assertThat(response.liveIndexDocumentCount()).isEqualTo(3);
		assertThat(response.matchingCount()).isEqualTo(1);
		assertThat(response.missingCount()).isEqualTo(1);
		assertThat(response.staleCount()).isEqualTo(1);
		assertThat(response.orphanCount()).isEqualTo(1);
		assertThat(response.missingProductIds()).containsExactly(3L);
		assertThat(response.staleProductIds()).containsExactly(2L);
		assertThat(response.consistent()).isFalse();
	}

	@Test
	void reportsAConsistentIndexWhenEveryVersionMatches() {
		Product firstProduct = product(1L, 4L);
		Product secondProduct = product(2L, 7L);
		when(productRepository.findAllByIdGreaterThanOrderByIdAsc(anyLong(), any(Pageable.class)))
				.thenReturn(List.of(firstProduct, secondProduct), List.of());
		when(productSearchGateway.findDatabaseVersions(anyCollection()))
				.thenReturn(Map.of(1L, 4L, 2L, 7L));
		when(productSearchGateway.countLiveDocuments()).thenReturn(2L);

		ProductSearchValidationResponse response = service.validate();

		assertThat(response.consistent()).isTrue();
		assertThat(response.matchingCount()).isEqualTo(2);
		assertThat(response.missingProductIds()).isEmpty();
		assertThat(response.staleProductIds()).isEmpty();
	}

	@Test
	void reconcilesOrphanDocumentsAcrossIndexPagesAndReportsIndividualFailures() {
		Product persistedProduct = product(1L, 4L);
		when(productSearchGateway.findLiveDocumentsAfter(null, 200))
				.thenReturn(List.of(
						new ProductSearchIndexEntry(1L, 4L),
						new ProductSearchIndexEntry(2L, 7L)));
		when(productSearchGateway.findLiveDocumentsAfter(2L, 200))
				.thenReturn(List.of(new ProductSearchIndexEntry(3L, 9L)));
		when(productSearchGateway.findLiveDocumentsAfter(3L, 200)).thenReturn(List.of());
		when(productRepository.findAllById(any()))
				.thenReturn(List.of(persistedProduct), List.of());
		doAnswer(invocation -> {
			ProductSearchDocument document = invocation.getArgument(0);
			if (document.id().equals(3L)) {
				throw new IllegalStateException("index unavailable");
			}
			return null;
		}).when(productSearchGateway).putWithoutRefresh(any(ProductSearchDocument.class));

		ProductSearchReconcileResponse response = service.reconcile();

		assertThat(response.scanned()).isEqualTo(3);
		assertThat(response.tombstoned()).isEqualTo(1);
		assertThat(response.failed()).isEqualTo(1);
		assertThat(response.failedProductIds()).containsExactly(3L);
		verify(productSearchGateway).refresh();

		verify(productRepository).findAllById(List.of(1L, 2L));
		verify(productRepository).findAllById(List.of(3L));
		verify(productSearchGateway).findLiveDocumentsAfter(null, 200);
		verify(productSearchGateway).findLiveDocumentsAfter(2L, 200);
		verify(productSearchGateway).findLiveDocumentsAfter(3L, 200);

		ArgumentCaptor<ProductSearchDocument> documents = ArgumentCaptor.forClass(
				ProductSearchDocument.class);
		verify(productSearchGateway, times(2)).putWithoutRefresh(documents.capture());
		assertThat(documents.getAllValues())
				.extracting(ProductSearchDocument::id)
				.containsExactly(2L, 3L);
		assertThat(documents.getAllValues().get(0).deleted()).isTrue();
		assertThat(documents.getAllValues().get(0).databaseVersion()).isEqualTo(8L);
		assertThat(documents.getAllValues().get(0).externalVersion())
				.isEqualTo(ProductSearchDocument.TOMBSTONE_EXTERNAL_VERSION);
		assertThat(documents.getAllValues().get(1).databaseVersion()).isEqualTo(10L);
		assertThat(documents.getAllValues().get(1).externalVersion())
				.isEqualTo(ProductSearchDocument.TOMBSTONE_EXTERNAL_VERSION);
	}

	@Test
	void treatsAConflictAsAlreadyReconciledWhenNoLiveDocumentRemains() {
		when(productSearchGateway.findLiveDocumentsAfter(null, 200))
				.thenReturn(List.of(new ProductSearchIndexEntry(2L, 7L)));
		when(productSearchGateway.findLiveDocumentsAfter(2L, 200)).thenReturn(List.of());
		when(productRepository.findAllById(List.of(2L))).thenReturn(List.of());
		doThrow(new ProductSearchVersionConflictException(2L))
				.when(productSearchGateway)
				.putWithoutRefresh(any(ProductSearchDocument.class));
		when(productSearchGateway.findDatabaseVersions(List.of(2L))).thenReturn(Map.of());

		ProductSearchReconcileResponse response = service.reconcile();

		assertThat(response.scanned()).isEqualTo(1);
		assertThat(response.tombstoned()).isZero();
		assertThat(response.failed()).isZero();
		verify(productSearchGateway, never()).refresh();
	}

	private static Product product(long id, long version) {
		Product product = mock(Product.class);
		when(product.getId()).thenReturn(id);
		when(product.getName()).thenReturn("Product " + id);
		when(product.getDescription()).thenReturn("Description " + id);
		when(product.getSku()).thenReturn("SKU-" + id);
		when(product.getType()).thenReturn(ProductType.FINISHED_PRODUCT);
		when(product.getDefaultMeasurementUnit()).thenReturn(MeasurementUnit.UNIT);
		when(product.isActive()).thenReturn(true);
		when(product.getVersion()).thenReturn(version);
		when(product.getUpdatedAt()).thenReturn(Instant.parse("2026-01-01T00:00:00Z"));
		return product;
	}
}
