package com.haru.product.inventory.application.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.haru.product.inventory.domain.InventoryMovementType;

public record InventoryMovementResponse(
		Long id,
		Long inventoryLotId,
		String lotNumber,
		Long productId,
		InventoryMovementType type,
		BigDecimal quantity,
		BigDecimal resultingQuantity,
		String referenceType,
		Long referenceId,
		String description,
		Instant occurredAt,
		String createdBy) {
}
