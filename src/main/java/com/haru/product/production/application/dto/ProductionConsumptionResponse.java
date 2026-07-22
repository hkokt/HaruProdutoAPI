package com.haru.product.production.application.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.haru.product.product.domain.MeasurementUnit;

public record ProductionConsumptionResponse(
		Long id,
		Long componentProductId,
		String componentProductName,
		String componentProductSku,
		Long consumedLotId,
		String consumedLotNumber,
		BigDecimal consumedQuantity,
		MeasurementUnit measurementUnit,
		Instant createdAt) {
}
