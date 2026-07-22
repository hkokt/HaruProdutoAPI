package com.haru.product.production.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CompleteProductionRequest(
		@NotBlank @Size(max = 80) String producedLotNumber,
		LocalDate manufactureDate,
		LocalDate expirationDate,
		@NotNull @PositiveOrZero @Digits(integer = 15, fraction = 4) BigDecimal producedUnitCost) {
}
