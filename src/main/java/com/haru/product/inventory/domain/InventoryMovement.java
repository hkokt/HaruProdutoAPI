package com.haru.product.inventory.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

import org.hibernate.annotations.Immutable;

import com.haru.product.inventory.domain.exception.InvalidInventoryAdjustmentException;
import com.haru.product.inventory.domain.exception.InvalidInventoryLotException;

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
@Table(name = "inventory_movements")
public class InventoryMovement {

	private static final int QUANTITY_MAX_INTEGER_DIGITS = 13;
	private static final int QUANTITY_MAX_FRACTION_DIGITS = 6;
	private static final int MAX_REFERENCE_TYPE_LENGTH = 60;
	private static final int MAX_DESCRIPTION_LENGTH = 500;
	private static final int MAX_CREATED_BY_LENGTH = 150;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "inventory_lot_id", nullable = false)
	private InventoryLot inventoryLot;

	@Enumerated(EnumType.STRING)
	@Column(name = "movement_type", nullable = false, length = 40)
	private InventoryMovementType type;

	@Column(nullable = false, precision = 19, scale = 6)
	private BigDecimal quantity;

	@Column(name = "resulting_quantity", nullable = false, precision = 19, scale = 6)
	private BigDecimal resultingQuantity;

	@Column(name = "reference_type", length = MAX_REFERENCE_TYPE_LENGTH)
	private String referenceType;

	@Column(name = "reference_id")
	private Long referenceId;

	@Column(length = MAX_DESCRIPTION_LENGTH)
	private String description;

	@Column(name = "occurred_at", nullable = false, updatable = false)
	private Instant occurredAt;

	@Column(name = "created_by", length = MAX_CREATED_BY_LENGTH, updatable = false)
	private String createdBy;

	protected InventoryMovement() {
	}

	private InventoryMovement(
			InventoryLot inventoryLot,
			InventoryMovementType type,
			BigDecimal quantity,
			BigDecimal resultingQuantity,
			String referenceType,
			Long referenceId,
			String description,
			Instant occurredAt,
			String createdBy) {
		this.inventoryLot = Objects.requireNonNull(inventoryLot, "Inventory lot is required");
		this.type = Objects.requireNonNull(type, "Inventory movement type is required");
		this.quantity = requirePositiveQuantity(quantity);
		this.resultingQuantity = requireNonNegativeResultingQuantity(resultingQuantity);
		this.referenceType = optionalText(
				referenceType,
				"Movement reference type",
				MAX_REFERENCE_TYPE_LENGTH);
		this.referenceId = referenceId;
		this.description = optionalText(
				description,
				"Movement description",
				MAX_DESCRIPTION_LENGTH);
		validateAdjustmentDescription(this.type, this.description);
		this.occurredAt = Objects.requireNonNull(occurredAt, "Movement occurrence time is required");
		this.createdBy = optionalText(createdBy, "Movement creator", MAX_CREATED_BY_LENGTH);
	}

	public static InventoryMovement create(
			InventoryLot inventoryLot,
			InventoryMovementType type,
			BigDecimal quantity,
			BigDecimal resultingQuantity,
			String referenceType,
			Long referenceId,
			String description,
			Instant occurredAt,
			String createdBy) {
		return new InventoryMovement(
				inventoryLot,
				type,
				quantity,
				resultingQuantity,
				referenceType,
				referenceId,
				description,
				occurredAt,
				createdBy);
	}

	private static BigDecimal requirePositiveQuantity(BigDecimal quantity) {
		if (quantity == null || quantity.signum() <= 0) {
			throw new InvalidInventoryLotException(
					"Inventory movement quantity must be greater than zero");
		}
		validateQuantityPrecision(quantity, "Inventory movement quantity");
		return quantity;
	}

	private static BigDecimal requireNonNegativeResultingQuantity(BigDecimal resultingQuantity) {
		if (resultingQuantity == null || resultingQuantity.signum() < 0) {
			throw new InvalidInventoryLotException(
					"Inventory movement resulting quantity cannot be negative");
		}
		validateQuantityPrecision(resultingQuantity, "Inventory movement resulting quantity");
		return resultingQuantity;
	}

	private static void validateQuantityPrecision(BigDecimal quantity, String fieldName) {
		int integerDigits = quantity.precision() - quantity.scale();
		if (integerDigits > QUANTITY_MAX_INTEGER_DIGITS
				|| quantity.scale() > QUANTITY_MAX_FRACTION_DIGITS) {
			throw new InvalidInventoryLotException(
					fieldName + " must fit NUMERIC(19, 6)");
		}
	}

	private static void validateAdjustmentDescription(
			InventoryMovementType type,
			String description) {
		if ((type == InventoryMovementType.ADJUSTMENT_IN
				|| type == InventoryMovementType.ADJUSTMENT_OUT)
				&& description == null) {
			throw new InvalidInventoryAdjustmentException(
					"An inventory adjustment requires a description");
		}
	}

	private static String optionalText(String value, String fieldName, int maximumLength) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String normalized = value.strip();
		if (normalized.length() > maximumLength) {
			throw new IllegalArgumentException(
					fieldName + " must have at most " + maximumLength + " characters");
		}
		return normalized;
	}

	public Long getId() {
		return id;
	}

	public InventoryLot getInventoryLot() {
		return inventoryLot;
	}

	public InventoryMovementType getType() {
		return type;
	}

	public BigDecimal getQuantity() {
		return quantity;
	}

	public BigDecimal getResultingQuantity() {
		return resultingQuantity;
	}

	public String getReferenceType() {
		return referenceType;
	}

	public Long getReferenceId() {
		return referenceId;
	}

	public String getDescription() {
		return description;
	}

	public Instant getOccurredAt() {
		return occurredAt;
	}

	public String getCreatedBy() {
		return createdBy;
	}
}
