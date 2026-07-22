package com.haru.product.inventory.application.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AdjustInventoryRequest(
		@NotNull @Positive @Digits(integer = 13, fraction = 6) BigDecimal quantity,
		@NotBlank @Size(max = 500) String justification) {
}
