package com.haru.product.production.application.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateProductionOrderRequest(
		@NotNull Long productId,
		@NotNull @Positive @Digits(integer = 13, fraction = 6) BigDecimal quantityToProduce) {
}
