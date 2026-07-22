package com.haru.product.production.domain;

import java.math.BigDecimal;
import java.time.Instant;

import org.hibernate.annotations.Immutable;

import com.haru.product.inventory.domain.InventoryLot;
import com.haru.product.product.domain.Product;
import com.haru.product.production.domain.exception.InvalidProductionOrderException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Immutable
@Table(
		name = "produced_lots",
		uniqueConstraints = {
				@UniqueConstraint(
						name = "uk_produced_lot_order",
						columnNames = "production_order_id"),
				@UniqueConstraint(
						name = "uk_produced_lot_inventory_lot",
						columnNames = "inventory_lot_id")
		})
public class ProducedLot {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "production_order_id", nullable = false, unique = true, updatable = false)
	private ProductionOrder productionOrder;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "inventory_lot_id", nullable = false, unique = true, updatable = false)
	private InventoryLot inventoryLot;

	@Column(name = "produced_quantity", nullable = false, precision = 19, scale = 6,
			updatable = false)
	private BigDecimal producedQuantity;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected ProducedLot() {
	}

	private ProducedLot(
			ProductionOrder productionOrder,
			InventoryLot inventoryLot,
			BigDecimal producedQuantity,
			Instant createdAt) {
		this.productionOrder = requireValue(productionOrder, "Production order is required");
		this.inventoryLot = requireValue(inventoryLot, "Produced inventory lot is required");
		if (!sameProduct(inventoryLot.getProduct(), productionOrder.getProduct())) {
			throw new InvalidProductionOrderException(
					"The produced inventory lot must belong to the production order product");
		}
		this.producedQuantity = ProductionOrder.requirePositiveTraceQuantity(
				producedQuantity,
				"Produced quantity");
		this.createdAt = requireValue(createdAt, "Produced lot creation time is required");
	}

	public static ProducedLot create(
			ProductionOrder productionOrder,
			InventoryLot inventoryLot,
			BigDecimal producedQuantity,
			Instant createdAt) {
		return new ProducedLot(
				productionOrder,
				inventoryLot,
				producedQuantity,
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

	public InventoryLot getInventoryLot() {
		return inventoryLot;
	}

	public BigDecimal getProducedQuantity() {
		return producedQuantity;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
