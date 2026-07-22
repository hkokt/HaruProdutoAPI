package com.haru.product.production.application.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.haru.product.product.domain.MeasurementUnit;
import com.haru.product.production.domain.ProductionOrderStatus;

public record ProductionOrderResponse(
		Long id,
		Long productId,
		String productName,
		String productSku,
		BigDecimal quantityToProduce,
		MeasurementUnit measurementUnit,
		ProductionOrderStatus status,
		Instant createdAt,
		Instant startedAt,
		Instant completedAt,
		long version) {
}
