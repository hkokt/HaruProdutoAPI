package com.haru.product.product.application.dto;

import com.haru.product.product.domain.MeasurementUnit;
import com.haru.product.product.domain.ProductType;

public record ProductSearchResultResponse(
		Long id,
		String name,
		String sku,
		ProductType type,
		MeasurementUnit defaultMeasurementUnit,
		boolean active,
		double score) {
}
