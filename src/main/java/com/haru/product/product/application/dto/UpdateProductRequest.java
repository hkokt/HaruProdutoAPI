package com.haru.product.product.application.dto;

import com.haru.product.product.domain.MeasurementUnit;
import com.haru.product.product.domain.ProductType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateProductRequest(
		@NotBlank @Size(max = 150) String name,
		@Size(max = 1000) String description,
		@NotBlank @Size(max = 60) String sku,
		@NotNull ProductType type,
		@NotNull MeasurementUnit defaultMeasurementUnit,
		@NotNull Boolean active) {
}
