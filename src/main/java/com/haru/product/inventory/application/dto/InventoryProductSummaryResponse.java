package com.haru.product.inventory.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.haru.product.product.domain.MeasurementUnit;

public record InventoryProductSummaryResponse(
		Long productId,
		String productName,
		String productSku,
		MeasurementUnit defaultMeasurementUnit,
		boolean active,
		BigDecimal availableQuantity,
		long lotCount,
		LocalDate referenceDate) {
}
