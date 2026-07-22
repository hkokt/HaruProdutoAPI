package com.haru.product.product.application.dto;

import java.time.Instant;
import java.util.List;

import com.haru.product.product.domain.MeasurementUnit;
import com.haru.product.product.domain.ProductType;

public record ProductResponse(
		Long id,
		String name,
		String description,
		String sku,
		ProductType type,
		MeasurementUnit defaultMeasurementUnit,
		boolean active,
		Instant createdAt,
		Instant updatedAt,
		long version,
		List<ProductCompositionResponse> components) {

	public ProductResponse {
		components = components == null ? List.of() : List.copyOf(components);
	}
}
