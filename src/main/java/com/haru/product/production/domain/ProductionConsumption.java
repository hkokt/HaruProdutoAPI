package com.haru.product.production.domain;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.Immutable;

import com.haru.product.inventory.domain.InventoryLot;
import com.haru.product.product.domain.MeasurementUnit;
import com.haru.product.product.domain.Product;
import com.haru.product.production.domain.exception.InvalidProductionOrderException;

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

@Entity
@Immutable
@Table(name = "production_consumptions")
public class ProductionConsumption {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "production_order_id", nullable = false, updatable = false)
	private ProductionOrder productionOrder;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "component_product_id", nullable = false, updatable = false)
	private Product componentProduct;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "consumed_lot_id", nullable = false, updatable = false)
	private InventoryLot consumedLot;

	@Column(name = "consumed_quantity", nullable = false, precision = 19, scale = 6,
			updatable = false)
	private BigDecimal consumedQuantity;

	@Enumerated(EnumType.STRING)
	@Column(name = "measurement_unit", nullable = false, length = 40, updatable = false)
	private MeasurementUnit measurementUnit;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected ProductionConsumption() {
	}

	private ProductionConsumption(
			ProductionOrder productionOrder,
			Product componentProduct,
			InventoryLot consumedLot,
			BigDecimal consumedQuantity,
			MeasurementUnit measurementUnit,
			Instant createdAt) {
		this.productionOrder = requireValue(
				productionOrder,
				"Production consumption order is required");
		this.componentProduct = requireValue(
				componentProduct,
				"Production consumption component product is required");
		this.consumedLot = requireValue(
				consumedLot,
				"Production consumption inventory lot is required");
		if (!sameProduct(consumedLot.getProduct(), componentProduct)) {
			throw new InvalidProductionOrderException(
					"The consumed inventory lot must belong to the component product");
		}
		this.consumedQuantity = ProductionOrder.requirePositiveTraceQuantity(
				consumedQuantity,
				"Consumed quantity");
		this.measurementUnit = requireValue(
				measurementUnit,
				"Production consumption measurement unit is required");
		this.createdAt = requireValue(
				createdAt,
				"Production consumption creation time is required");
	}

	public static ProductionConsumption create(
			ProductionOrder productionOrder,
			Product componentProduct,
			InventoryLot consumedLot,
			BigDecimal consumedQuantity,
			MeasurementUnit measurementUnit,
			Instant createdAt) {
		return new ProductionConsumption(
				productionOrder,
				componentProduct,
				consumedLot,
				consumedQuantity,
				measurementUnit,
				createdAt);
	}

	private static <T> T requireValue(T value, String message) {
		if (value == null) {
			throw new InvalidProductionOrderException(message);
		}
		return value;
	}

	private static boolean sameProduct(Product first, Product second) {
		return first == second
				|| first != null && second != null
				&& first.getId() != null && first.getId().equals(second.getId());
	}

	public Long getId() {
		return id;
	}

	public ProductionOrder getProductionOrder() {
		return productionOrder;
	}

	public Product getComponentProduct() {
		return componentProduct;
	}

	public InventoryLot getConsumedLot() {
		return consumedLot;
	}

	public BigDecimal getConsumedQuantity() {
		return consumedQuantity;
	}

	public MeasurementUnit getMeasurementUnit() {
		return measurementUnit;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
