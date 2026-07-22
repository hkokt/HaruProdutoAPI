package com.haru.product.inventory.application.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ConsumeInventoryRequest(
		@NotNull @Positive @Digits(integer = 13, fraction = 6) BigDecimal quantity,
		@Size(max = 60) String referenceType,
		Long referenceId,
		@Size(max = 500) String description) {
}
