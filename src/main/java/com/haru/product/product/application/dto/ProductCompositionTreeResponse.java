package com.haru.product.product.application.dto;

import java.math.BigDecimal;
import java.util.List;

import com.haru.product.product.domain.MeasurementUnit;
import com.haru.product.product.domain.ProductType;

public record ProductCompositionTreeResponse(
		Long productId,
		String name,
		String sku,
		ProductType type,
		MeasurementUnit defaultMeasurementUnit,
		List<Component> components) {

	public ProductCompositionTreeResponse {
		components = components == null ? List.of() : List.copyOf(components);
	}

	public record Component(
			Long compositionId,
			Long productId,
			String name,
			String sku,
			BigDecimal quantity,
			MeasurementUnit measurementUnit,
			List<Component> components) {

		public Component {
			components = components == null ? List.of() : List.copyOf(components);
		}
	}
}
