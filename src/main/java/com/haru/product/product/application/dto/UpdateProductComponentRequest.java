package com.haru.product.product.application.dto;

import java.math.BigDecimal;

import com.haru.product.product.domain.MeasurementUnit;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record UpdateProductComponentRequest(
		@NotNull @Positive @Digits(integer = 13, fraction = 6) BigDecimal quantity,
		@NotNull MeasurementUnit measurementUnit) {
}
