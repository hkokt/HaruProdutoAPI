package com.haru.product.product.application.search;

import java.time.Instant;

import com.haru.product.product.application.dto.ProductDeletionResult;
import com.haru.product.product.application.dto.ProductResponse;
import com.haru.product.product.domain.MeasurementUnit;
import com.haru.product.product.domain.Product;
import com.haru.product.product.domain.ProductType;

public record ProductSearchDocument(
		Long id,
		String name,
		String description,
		String sku,
		ProductType type,
		MeasurementUnit defaultMeasurementUnit,
		boolean active,
		long databaseVersion,
		Instant updatedAt,
		boolean deleted) {

	public static final long TOMBSTONE_EXTERNAL_VERSION = 9_000_000_000_000_000_000L;

	public static ProductSearchDocument from(ProductResponse product) {
		return new ProductSearchDocument(
				product.id(),
				product.name(),
				product.description(),
				product.sku(),
				product.type(),
				product.defaultMeasurementUnit(),
				product.active(),
				product.version(),
				product.updatedAt(),
				false);
	}

	public static ProductSearchDocument from(Product product) {
		return new ProductSearchDocument(
				product.getId(),
				product.getName(),
				product.getDescription(),
				product.getSku(),
				product.getType(),
				product.getDefaultMeasurementUnit(),
				product.isActive(),
				product.getVersion(),
				product.getUpdatedAt(),
				false);
	}

	public static ProductSearchDocument tombstone(ProductDeletionResult deletion) {
		return new ProductSearchDocument(
				deletion.id(),
				null,
				null,
				null,
				null,
				null,
				false,
				deletion.databaseVersion(),
				deletion.deletedAt(),
				true);
	}

	public long externalVersion() {
		if (deleted) {
			return TOMBSTONE_EXTERNAL_VERSION;
		}
		long liveVersion = Math.incrementExact(databaseVersion);
		if (liveVersion >= TOMBSTONE_EXTERNAL_VERSION) {
			throw new IllegalStateException("Product database version reached the reserved tombstone range");
		}
		return liveVersion;
	}
}
