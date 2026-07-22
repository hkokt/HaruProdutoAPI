package com.haru.product.inventory.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.haru.product.inventory.domain.InventoryLotStatus;

public record InventoryLotResponse(
		Long id,
		Long productId,
		String productName,
		String productSku,
		String lotNumber,
		LocalDate manufactureDate,
		LocalDate expirationDate,
		BigDecimal initialQuantity,
		BigDecimal availableQuantity,
		BigDecimal unitCost,
		InventoryLotStatus status,
		Instant createdAt,
		Instant updatedAt,
		long version) {
}
