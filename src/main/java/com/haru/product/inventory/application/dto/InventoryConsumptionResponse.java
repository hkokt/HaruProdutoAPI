package com.haru.product.inventory.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.haru.product.inventory.domain.InventoryLotStatus;
import com.haru.product.product.domain.MeasurementUnit;

public record InventoryConsumptionResponse(
		Long productId,
		String productName,
		String productSku,
		BigDecimal requestedQuantity,
		BigDecimal consumedQuantity,
		MeasurementUnit measurementUnit,
		LocalDate referenceDate,
		List<LotConsumption> lots) {

	public InventoryConsumptionResponse {
		lots = lots == null ? List.of() : List.copyOf(lots);
	}

	public record LotConsumption(
			Long lotId,
			String lotNumber,
			LocalDate expirationDate,
			BigDecimal quantity,
			BigDecimal resultingQuantity,
			InventoryLotStatus status,
			Long movementId) {
	}
}
