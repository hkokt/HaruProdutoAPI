package com.haru.product.product.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.haru.product.product.application.dto.ProductDeletionResult;
import com.haru.product.product.application.dto.ProductSearchReconcileResponse;
import com.haru.product.product.application.dto.ProductSearchReindexResponse;
import com.haru.product.product.application.dto.ProductSearchValidationResponse;
import com.haru.product.product.application.exception.ProductSearchUnavailableException;
import com.haru.product.product.application.exception.ProductSearchVersionConflictException;
import com.haru.product.product.application.search.ProductSearchDocument;
import com.haru.product.product.application.search.ProductSearchGateway;
import com.haru.product.product.application.search.ProductSearchIndexEntry;
import com.haru.product.product.domain.Product;
import com.haru.product.product.infrastructure.persistence.ProductRepository;

@Service
public class ProductSearchMaintenanceService {

	private static final Logger LOGGER = LoggerFactory.getLogger(ProductSearchMaintenanceService.class);
	private static final int BATCH_SIZE = 200;
	private static final int MAX_REPORTED_PRODUCT_IDS = 100;
	private static final PageRequest PRODUCT_BATCH = PageRequest.of(0, BATCH_SIZE);

	private final ProductRepository productRepository;
	private final ProductSearchGateway productSearchGateway;

	public ProductSearchMaintenanceService(
			ProductRepository productRepository,
			ProductSearchGateway productSearchGateway) {
		this.productRepository = productRepository;
		this.productSearchGateway = productSearchGateway;
	}

	public ProductSearchReindexResponse reindex() {
		long scanned = 0;
		long indexed = 0;
		long written = 0;
		long failed = 0;
		List<Long> failedProductIds = new ArrayList<>();
		Long cursor = 0L;

		while (true) {
			List<Product> products = productRepository.findAllByIdGreaterThanOrderByIdAsc(
					cursor,
					PRODUCT_BATCH);
			if (products.isEmpty()) {
				break;
			}

			for (Product product : products) {
				scanned++;
				try {
					productSearchGateway.putWithoutRefresh(ProductSearchDocument.from(product));
					indexed++;
					written++;
				}
				catch (ProductSearchVersionConflictException exception) {
					Long indexedVersion = productSearchGateway.findDatabaseVersions(List.of(product.getId()))
							.get(product.getId());
					if (indexedVersion != null && indexedVersion >= product.getVersion()) {
						indexed++;
					}
					else {
						failed++;
						addSample(failedProductIds, product.getId());
						LOGGER.warn(
								"Product {} could not be reindexed because a newer tombstone exists",
								product.getId(),
								exception);
					}
				}
				catch (ProductSearchUnavailableException exception) {
					throw exception;
				}
				catch (RuntimeException exception) {
					failed++;
					addSample(failedProductIds, product.getId());
					LOGGER.warn("Product {} could not be reindexed", product.getId(), exception);
				}
			}

			cursor = products.getLast().getId();
		}

		if (written > 0) {
			productSearchGateway.refresh();
		}
		return new ProductSearchReindexResponse(scanned, indexed, failed, failedProductIds);
	}

	public ProductSearchValidationResponse validate() {
		long databaseProductCount = 0;
		long matchingCount = 0;
		long missingCount = 0;
		long staleCount = 0;
		List<Long> missingProductIds = new ArrayList<>();
		List<Long> staleProductIds = new ArrayList<>();
		Long cursor = 0L;

		while (true) {
			List<Product> products = productRepository.findAllByIdGreaterThanOrderByIdAsc(
					cursor,
					PRODUCT_BATCH);
			if (products.isEmpty()) {
				break;
			}
			Map<Long, Long> databaseVersions = databaseVersions(products);
			databaseProductCount += databaseVersions.size();

			if (!databaseVersions.isEmpty()) {
				Map<Long, Long> indexVersions = productSearchGateway.findDatabaseVersions(
						databaseVersions.keySet());

				for (Map.Entry<Long, Long> databaseEntry : databaseVersions.entrySet()) {
					Long productId = databaseEntry.getKey();
					if (!indexVersions.containsKey(productId)) {
						missingCount++;
						addSample(missingProductIds, productId);
					}
					else if (!Objects.equals(indexVersions.get(productId), databaseEntry.getValue())) {
						staleCount++;
						addSample(staleProductIds, productId);
					}
					else {
						matchingCount++;
					}
				}
			}

			cursor = products.getLast().getId();
		}

		long liveIndexDocumentCount = productSearchGateway.countLiveDocuments();
		long indexedDatabaseProducts = matchingCount + staleCount;
		long orphanCount = Math.max(0, liveIndexDocumentCount - indexedDatabaseProducts);
		boolean consistent = missingCount == 0
				&& staleCount == 0
				&& orphanCount == 0
				&& databaseProductCount == liveIndexDocumentCount;

		return new ProductSearchValidationResponse(
				databaseProductCount,
				liveIndexDocumentCount,
				matchingCount,
				missingCount,
				staleCount,
				orphanCount,
				missingProductIds,
				staleProductIds,
				consistent);
	}

	public ProductSearchReconcileResponse reconcile() {
		long scanned = 0;
		long tombstoned = 0;
		long failed = 0;
		List<Long> failedProductIds = new ArrayList<>();
		Long cursor = null;

		while (true) {
			List<ProductSearchIndexEntry> entries = productSearchGateway.findLiveDocumentsAfter(
					cursor,
					BATCH_SIZE);
			if (entries.isEmpty()) {
				break;
			}

			scanned += entries.size();
			Set<Long> databaseProductIds = databaseProductIds(entries);
			for (ProductSearchIndexEntry entry : entries) {
				if (databaseProductIds.contains(entry.id())) {
					continue;
				}

				try {
					if (!tombstoneOrphan(entry)) {
						continue;
					}
					tombstoned++;
				}
				catch (ProductSearchUnavailableException exception) {
					throw exception;
				}
				catch (RuntimeException exception) {
					failed++;
					addSample(failedProductIds, entry.id());
					LOGGER.warn(
							"Orphan product search document {} could not be tombstoned",
							entry.id(),
							exception);
				}
			}

			cursor = entries.getLast().id();
		}

		if (tombstoned > 0) {
			productSearchGateway.refresh();
		}
		return new ProductSearchReconcileResponse(scanned, tombstoned, failed, failedProductIds);
	}

	private boolean tombstoneOrphan(ProductSearchIndexEntry entry) {
		ProductDeletionResult deletion = new ProductDeletionResult(
				entry.id(),
				Math.incrementExact(entry.databaseVersion()),
				Instant.now());
		try {
			productSearchGateway.putWithoutRefresh(ProductSearchDocument.tombstone(deletion));
			return true;
		}
		catch (ProductSearchVersionConflictException exception) {
			Long currentLiveVersion = productSearchGateway.findDatabaseVersions(List.of(entry.id()))
					.get(entry.id());
			if (currentLiveVersion == null) {
				return false;
			}
			throw new IllegalStateException(
					"A live product search document reached the reserved tombstone version",
					exception);
		}
	}

	private Set<Long> databaseProductIds(List<ProductSearchIndexEntry> entries) {
		List<Long> productIds = entries.stream()
				.map(ProductSearchIndexEntry::id)
				.toList();
		Set<Long> databaseProductIds = new HashSet<>();
		for (Product product : productRepository.findAllById(productIds)) {
			databaseProductIds.add(product.getId());
		}
		return databaseProductIds;
	}

	private static Map<Long, Long> databaseVersions(List<Product> products) {
		Map<Long, Long> versions = new LinkedHashMap<>();
		for (Product product : products) {
			versions.put(product.getId(), product.getVersion());
		}
		return versions;
	}

	private static void addSample(List<Long> samples, Long productId) {
		if (samples.size() < MAX_REPORTED_PRODUCT_IDS) {
			samples.add(productId);
		}
	}
}
