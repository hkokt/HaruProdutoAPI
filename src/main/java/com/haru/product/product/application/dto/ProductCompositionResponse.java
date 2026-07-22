package com.haru.product.product.application.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.haru.product.product.domain.MeasurementUnit;

public record ProductCompositionResponse(
		Long id,
		Long componentProductId,
		String componentProductName,
		String componentProductSku,
		BigDecimal quantity,
		MeasurementUnit measurementUnit,
		Instant createdAt,
		Instant updatedAt) {
}
