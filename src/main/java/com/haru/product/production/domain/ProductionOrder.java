package com.haru.product.production.domain;

import java.math.BigDecimal;
import java.time.Instant;

import com.haru.product.product.domain.Product;
import com.haru.product.production.domain.exception.InvalidProductionOrderException;
import com.haru.product.production.domain.exception.InvalidProductionOrderStateException;

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
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "production_orders")
public class ProductionOrder {

	private static final int QUANTITY_MAX_INTEGER_DIGITS = 13;
	private static final int QUANTITY_MAX_FRACTION_DIGITS = 6;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "product_id", nullable = false)
	private Product product;

	@Column(name = "quantity_to_produce", nullable = false, precision = 19, scale = 6)
	private BigDecimal quantityToProduce;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private ProductionOrderStatus status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "completed_at")
	private Instant completedAt;

	@Version
	@Column(nullable = false)
	private long version;

	protected ProductionOrder() {
	}

	private ProductionOrder(Product product, BigDecimal quantityToProduce, Instant createdAt) {
		this.product = requireProduct(product);
		this.quantityToProduce = requirePositiveQuantity(quantityToProduce);
		this.status = ProductionOrderStatus.CREATED;
		this.createdAt = requireInstant(createdAt, "Production order creation time is required");
	}

	public static ProductionOrder create(
			Product product,
			BigDecimal quantityToProduce,
			Instant createdAt) {
		return new ProductionOrder(product, quantityToProduce, createdAt);
	}

	public void start(Instant startedAt) {
		requireStatus(ProductionOrderStatus.CREATED, "started");
		Instant validatedStartedAt = requireInstant(
				startedAt,
				"Production order start time is required");
		this.status = ProductionOrderStatus.IN_PROGRESS;
		this.startedAt = validatedStartedAt;
	}

	public void complete(Instant completedAt) {
		requireStatus(ProductionOrderStatus.IN_PROGRESS, "completed");
		Instant validatedCompletedAt = requireInstant(
				completedAt,
				"Production order completion time is required");
		this.status = ProductionOrderStatus.COMPLETED;
		this.completedAt = validatedCompletedAt;
	}

	public void cancel() {
		if (status == ProductionOrderStatus.COMPLETED
				|| status == ProductionOrderStatus.CANCELLED) {
			throw invalidState("cancelled");
		}
		status = ProductionOrderStatus.CANCELLED;
	}

	private void requireStatus(ProductionOrderStatus expectedStatus, String operation) {
		if (status != expectedStatus) {
			throw invalidState(operation);
		}
	}

	private InvalidProductionOrderStateException invalidState(String operation) {
		return new InvalidProductionOrderStateException(id, status, operation);
	}

	private static Product requireProduct(Product product) {
		if (product == null) {
			throw new InvalidProductionOrderException("Production order product is required");
		}
		return product;
	}

	private static BigDecimal requirePositiveQuantity(BigDecimal quantity) {
		if (quantity == null || quantity.signum() <= 0) {
			throw new InvalidProductionOrderException(
					"Quantity to produce must be greater than zero");
		}
		validateQuantityPrecision(quantity, "Quantity to produce");
		return quantity;
	}

	static BigDecimal requirePositiveTraceQuantity(BigDecimal quantity, String fieldName) {
		if (quantity == null || quantity.signum() <= 0) {
			throw new InvalidProductionOrderException(fieldName + " must be greater than zero");
		}
		validateQuantityPrecision(quantity, fieldName);
		return quantity;
	}

	private static void validateQuantityPrecision(BigDecimal quantity, String fieldName) {
		int integerDigits = quantity.precision() - quantity.scale();
		if (integerDigits > QUANTITY_MAX_INTEGER_DIGITS
				|| quantity.scale() > QUANTITY_MAX_FRACTION_DIGITS) {
			throw new InvalidProductionOrderException(fieldName + " must fit NUMERIC(19, 6)");
		}
	}

	private static Instant requireInstant(Instant value, String message) {
		if (value == null) {
			throw new InvalidProductionOrderException(message);
		}
		return value;
	}

	public Long getId() {
		return id;
	}

	public Product getProduct() {
		return product;
	}

	public BigDecimal getQuantityToProduce() {
		return quantityToProduce;
	}

	public ProductionOrderStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}

	public long getVersion() {
		return version;
	}
}
