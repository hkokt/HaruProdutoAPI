package com.haru.product.inventory.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.regex.Pattern;

import com.haru.product.inventory.domain.exception.BlockedInventoryLotException;
import com.haru.product.inventory.domain.exception.ExpiredInventoryLotException;
import com.haru.product.inventory.domain.exception.InsufficientInventoryException;
import com.haru.product.inventory.domain.exception.InvalidInventoryLotException;
import com.haru.product.product.domain.Product;

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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

@Entity
@Table(
		name = "inventory_lots",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_inventory_lot_product_number",
				columnNames = { "product_id", "lot_number" }))
public class InventoryLot {

	private static final int MAX_LOT_NUMBER_LENGTH = 80;
	private static final int QUANTITY_MAX_INTEGER_DIGITS = 13;
	private static final int QUANTITY_MAX_FRACTION_DIGITS = 6;
	private static final int COST_MAX_INTEGER_DIGITS = 15;
	private static final int COST_MAX_FRACTION_DIGITS = 4;
	private static final Pattern WHITESPACE = Pattern.compile("\\s+");

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "product_id", nullable = false)
	private Product product;

	@Column(name = "lot_number", nullable = false, length = MAX_LOT_NUMBER_LENGTH)
	private String lotNumber;

	@Column(name = "manufacture_date")
	private LocalDate manufactureDate;

	@Column(name = "expiration_date")
	private LocalDate expirationDate;

	@Column(name = "initial_quantity", nullable = false, precision = 19, scale = 6)
	private BigDecimal initialQuantity;

	@Column(name = "available_quantity", nullable = false, precision = 19, scale = 6)
	private BigDecimal availableQuantity;

	@Column(name = "unit_cost", nullable = false, precision = 19, scale = 4)
	private BigDecimal unitCost;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private InventoryLotStatus status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(nullable = false)
	private long version;

	protected InventoryLot() {
	}

	private InventoryLot(
			Product product,
			String lotNumber,
			LocalDate manufactureDate,
			LocalDate expirationDate,
			BigDecimal initialQuantity,
			BigDecimal unitCost,
			LocalDate referenceDate) {
		this.product = requireProduct(product);
		this.lotNumber = normalizeLotNumber(lotNumber);
		validateDates(manufactureDate, expirationDate);
		this.manufactureDate = manufactureDate;
		this.expirationDate = expirationDate;
		this.initialQuantity = requirePositiveQuantity(initialQuantity, "Initial quantity");
		this.availableQuantity = this.initialQuantity;
		this.unitCost = requireNonNegativeCost(unitCost);
		LocalDate effectiveReferenceDate = Objects.requireNonNull(
				referenceDate,
				"Reference date is required");
		this.status = expiresBefore(effectiveReferenceDate)
				? InventoryLotStatus.EXPIRED
				: InventoryLotStatus.AVAILABLE;
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	public static InventoryLot create(
			Product product,
			String lotNumber,
			LocalDate manufactureDate,
			LocalDate expirationDate,
			BigDecimal initialQuantity,
			BigDecimal unitCost,
			LocalDate referenceDate) {
		return new InventoryLot(
				product,
				lotNumber,
				manufactureDate,
				expirationDate,
				initialQuantity,
				unitCost,
				referenceDate);
	}

	public static InventoryLot create(
			Product product,
			String lotNumber,
			LocalDate manufactureDate,
			LocalDate expirationDate,
			BigDecimal initialQuantity,
			BigDecimal unitCost) {
		return create(
				product,
				lotNumber,
				manufactureDate,
				expirationDate,
				initialQuantity,
				unitCost,
				LocalDate.now());
	}

	public boolean isExpired(LocalDate referenceDate) {
		Objects.requireNonNull(referenceDate, "Reference date is required");
		return status == InventoryLotStatus.EXPIRED || expiresBefore(referenceDate);
	}

	public boolean isAvailable(LocalDate referenceDate) {
		return status == InventoryLotStatus.AVAILABLE
				&& availableQuantity.signum() > 0
				&& !isExpired(referenceDate);
	}

	public void decreaseAvailableQuantity(BigDecimal quantity) {
		decreaseAvailableQuantity(quantity, LocalDate.now());
	}

	public void decreaseAvailableQuantity(BigDecimal quantity, LocalDate referenceDate) {
		BigDecimal validatedQuantity = requirePositiveQuantity(quantity, "Decrease quantity");
		Objects.requireNonNull(referenceDate, "Reference date is required");
		if (status == InventoryLotStatus.BLOCKED) {
			throw new BlockedInventoryLotException(id, lotNumber);
		}
		if (isExpired(referenceDate)) {
			throw new ExpiredInventoryLotException(id, lotNumber);
		}
		if (status == InventoryLotStatus.DEPLETED
				|| availableQuantity.compareTo(validatedQuantity) < 0) {
			throw new InsufficientInventoryException(validatedQuantity, availableQuantity);
		}

		availableQuantity = availableQuantity.subtract(validatedQuantity);
		if (availableQuantity.signum() == 0) {
			markAsDepleted();
		} else {
			touch();
		}
	}

	public void increaseAvailableQuantity(BigDecimal quantity) {
		increaseAvailableQuantity(quantity, LocalDate.now());
	}

	public void increaseAvailableQuantity(BigDecimal quantity, LocalDate referenceDate) {
		BigDecimal validatedQuantity = requirePositiveQuantity(quantity, "Increase quantity");
		Objects.requireNonNull(referenceDate, "Reference date is required");
		BigDecimal resultingQuantity = availableQuantity.add(validatedQuantity);
		validateNumeric(resultingQuantity, QUANTITY_MAX_INTEGER_DIGITS,
				QUANTITY_MAX_FRACTION_DIGITS, "Resulting quantity");
		availableQuantity = resultingQuantity;
		if (status == InventoryLotStatus.DEPLETED) {
			status = expiresBefore(referenceDate)
					? InventoryLotStatus.EXPIRED
					: InventoryLotStatus.AVAILABLE;
		}
		touch();
	}

	public void block() {
		if (status != InventoryLotStatus.BLOCKED) {
			status = InventoryLotStatus.BLOCKED;
			touch();
		}
	}

	public void markAsDepleted() {
		if (availableQuantity.signum() != 0) {
			throw new InvalidInventoryLotException(
					"A depleted inventory lot must have zero available quantity");
		}
		if (status != InventoryLotStatus.DEPLETED) {
			status = InventoryLotStatus.DEPLETED;
			touch();
		}
	}

	public void markAsExpired(LocalDate referenceDate) {
		Objects.requireNonNull(referenceDate, "Reference date is required");
		if (!expiresBefore(referenceDate)) {
			throw new InvalidInventoryLotException(
					"An inventory lot cannot be marked as expired before its expiration date");
		}
		if (status != InventoryLotStatus.EXPIRED) {
			status = InventoryLotStatus.EXPIRED;
			touch();
		}
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

	public static String normalizeLotNumber(String lotNumber) {
		if (lotNumber == null || lotNumber.isBlank()) {
			throw new InvalidInventoryLotException("Inventory lot number is required");
		}
		String normalized = WHITESPACE.matcher(lotNumber.strip()).replaceAll(" ");
		if (normalized.length() > MAX_LOT_NUMBER_LENGTH) {
			throw new InvalidInventoryLotException(
					"Inventory lot number must have at most "
							+ MAX_LOT_NUMBER_LENGTH + " characters");
		}
		return normalized;
	}

	private boolean expiresBefore(LocalDate referenceDate) {
		return expirationDate != null && expirationDate.isBefore(referenceDate);
	}

	private void touch() {
		updatedAt = Instant.now();
	}

	private static Product requireProduct(Product product) {
		if (product == null) {
			throw new InvalidInventoryLotException("Inventory lot product is required");
		}
		return product;
	}

	private static void validateDates(LocalDate manufactureDate, LocalDate expirationDate) {
		if (manufactureDate != null && expirationDate != null
				&& expirationDate.isBefore(manufactureDate)) {
			throw new InvalidInventoryLotException(
					"Inventory lot expiration date cannot be before its manufacture date");
		}
	}

	private static BigDecimal requirePositiveQuantity(BigDecimal quantity, String fieldName) {
		if (quantity == null || quantity.signum() <= 0) {
			throw new InvalidInventoryLotException(fieldName + " must be greater than zero");
		}
		validateNumeric(quantity, QUANTITY_MAX_INTEGER_DIGITS,
				QUANTITY_MAX_FRACTION_DIGITS, fieldName);
		return quantity;
	}

	private static BigDecimal requireNonNegativeCost(BigDecimal unitCost) {
		if (unitCost == null || unitCost.signum() < 0) {
			throw new InvalidInventoryLotException("Unit cost must be greater than or equal to zero");
		}
		validateNumeric(unitCost, COST_MAX_INTEGER_DIGITS,
				COST_MAX_FRACTION_DIGITS, "Unit cost");
		return unitCost;
	}

	private static void validateNumeric(
			BigDecimal value,
			int maximumIntegerDigits,
			int maximumFractionDigits,
			String fieldName) {
		int integerDigits = value.precision() - value.scale();
		if (integerDigits > maximumIntegerDigits || value.scale() > maximumFractionDigits) {
			throw new InvalidInventoryLotException(
					fieldName + " exceeds its supported precision or scale");
		}
	}

	public Long getId() {
		return id;
	}

	public Product getProduct() {
		return product;
	}

	public String getLotNumber() {
		return lotNumber;
	}

	public LocalDate getManufactureDate() {
		return manufactureDate;
	}

	public LocalDate getExpirationDate() {
		return expirationDate;
	}

	public BigDecimal getInitialQuantity() {
		return initialQuantity;
	}

	public BigDecimal getAvailableQuantity() {
		return availableQuantity;
	}

	public BigDecimal getUnitCost() {
		return unitCost;
	}

	public InventoryLotStatus getStatus() {
		return status;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public long getVersion() {
		return version;
	}
}
