package com.haru.product.inventory.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.haru.product.product.domain.MeasurementUnit;

public record InventoryAvailabilityResponse(
		Long productId,
		String productName,
		String productSku,
		MeasurementUnit measurementUnit,
		BigDecimal availableQuantity,
		LocalDate referenceDate) {
}
