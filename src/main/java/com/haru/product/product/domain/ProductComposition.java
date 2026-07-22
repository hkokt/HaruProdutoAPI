package com.haru.product.product.domain;

import java.math.BigDecimal;
import java.time.Instant;

import com.haru.product.product.domain.exception.InvalidProductCompositionException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "product_compositions")
public class ProductComposition {

	private static final int MAX_INTEGER_DIGITS = 13;
	private static final int MAX_FRACTION_DIGITS = 6;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "parent_product_id", nullable = false)
	private Product parentProduct;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "component_product_id", nullable = false)
	private Product componentProduct;

	@Column(nullable = false, precision = 19, scale = 6)
	private BigDecimal quantity;

	@Enumerated(EnumType.STRING)
	@Column(name = "measurement_unit", nullable = false, length = 40)
	private MeasurementUnit measurementUnit;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected ProductComposition() {
	}

	ProductComposition(
			Product parentProduct,
			Product componentProduct,
			BigDecimal quantity,
			MeasurementUnit measurementUnit) {
		if (parentProduct == null) {
			throw new InvalidProductCompositionException("Parent product is required");
		}
		if (componentProduct == null) {
			throw new InvalidProductCompositionException("Component product is required");
		}
		validateQuantity(quantity);
		validateMeasurementUnit(measurementUnit);

		this.parentProduct = parentProduct;
		this.componentProduct = componentProduct;
		this.quantity = quantity;
		this.measurementUnit = measurementUnit;
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void update(BigDecimal quantity, MeasurementUnit measurementUnit) {
		validateQuantity(quantity);
		validateMeasurementUnit(measurementUnit);
		if (!componentProduct.isActive()) {
			throw new InvalidProductCompositionException("Component product must be active");
		}
		this.quantity = quantity;
		this.measurementUnit = measurementUnit;
		this.updatedAt = Instant.now();
	}

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		if (createdAt == null) {
			createdAt = now;
		}
		if (updatedAt == null) {
			updatedAt = now;
		}
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}

	private static void validateQuantity(BigDecimal quantity) {
		if (quantity == null || quantity.signum() <= 0) {
			throw new InvalidProductCompositionException("Component quantity must be greater than zero");
		}
		int integerDigits = quantity.precision() - quantity.scale();
		if (integerDigits > MAX_INTEGER_DIGITS || quantity.scale() > MAX_FRACTION_DIGITS) {
			throw new InvalidProductCompositionException(
					"Component quantity must fit NUMERIC(19, 6)");
		}
	}

	private static void validateMeasurementUnit(MeasurementUnit measurementUnit) {
		if (measurementUnit == null) {
			throw new InvalidProductCompositionException("Component measurement unit is required");
		}
	}

	public Long getId() {
		return id;
	}

	public Product getParentProduct() {
		return parentProduct;
	}

	public Product getComponentProduct() {
		return componentProduct;
	}

	public BigDecimal getQuantity() {
		return quantity;
	}

	public MeasurementUnit getMeasurementUnit() {
		return measurementUnit;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
