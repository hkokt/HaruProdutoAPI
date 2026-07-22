package com.haru.product.inventory.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateInventoryLotRequest(
		@NotNull Long productId,
		@NotBlank @Size(max = 80) String lotNumber,
		LocalDate manufactureDate,
		LocalDate expirationDate,
		@NotNull @Positive @Digits(integer = 13, fraction = 6) BigDecimal initialQuantity,
		@NotNull @PositiveOrZero @Digits(integer = 15, fraction = 4) BigDecimal unitCost) {
}
