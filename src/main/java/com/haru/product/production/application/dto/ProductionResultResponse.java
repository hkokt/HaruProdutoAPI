package com.haru.product.production.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.haru.product.inventory.domain.InventoryLotStatus;
import com.haru.product.product.domain.MeasurementUnit;

public record ProductionResultResponse(
		ProductionOrderResponse order,
		ProducedLotSummary producedLot,
		List<ProductionConsumptionResponse> consumptions) {

	public ProductionResultResponse {
		consumptions = consumptions == null ? List.of() : List.copyOf(consumptions);
	}

	public record ProducedLotSummary(
			Long id,
			Long inventoryLotId,
			String lotNumber,
			BigDecimal producedQuantity,
			MeasurementUnit measurementUnit,
			InventoryLotStatus status,
			LocalDate manufactureDate,
			LocalDate expirationDate,
			Instant createdAt) {
	}
}
