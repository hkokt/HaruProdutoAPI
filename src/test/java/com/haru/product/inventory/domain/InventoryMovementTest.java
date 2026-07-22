package com.haru.product.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.haru.product.inventory.domain.exception.InvalidInventoryAdjustmentException;
import com.haru.product.inventory.domain.exception.InvalidInventoryLotException;
import com.haru.product.product.domain.MeasurementUnit;
import com.haru.product.product.domain.Product;
import com.haru.product.product.domain.ProductType;

class InventoryMovementTest {

	@Test
	void createsAnImmutableEntryMovementWithTheResultingBalance() {
		InventoryLot lot = lot();
		Instant occurredAt = Instant.parse("2026-07-01T10:15:30Z");

		InventoryMovement movement = InventoryMovement.create(
				lot,
				InventoryMovementType.ENTRY,
				new BigDecimal("100.000000"),
				new BigDecimal("100.000000"),
				"PURCHASE_ORDER",
				42L,
				"Initial Sakura essence receipt",
				occurredAt,
				"inventory-admin");

		assertThat(movement.getInventoryLot()).isSameAs(lot);
		assertThat(movement.getType()).isEqualTo(InventoryMovementType.ENTRY);
		assertThat(movement.getQuantity()).isEqualByComparingTo("100");
		assertThat(movement.getResultingQuantity()).isEqualByComparingTo("100");
		assertThat(movement.getReferenceType()).isEqualTo("PURCHASE_ORDER");
		assertThat(movement.getReferenceId()).isEqualTo(42L);
		assertThat(movement.getDescription()).isEqualTo("Initial Sakura essence receipt");
		assertThat(movement.getOccurredAt()).isEqualTo(occurredAt);
		assertThat(movement.getCreatedBy()).isEqualTo("inventory-admin");
	}

	@Test
	void requiresPositiveQuantityAndANonNegativeResultingBalance() {
		assertThatExceptionOfType(InvalidInventoryLotException.class)
				.isThrownBy(() -> movement(
						InventoryMovementType.EXIT,
						BigDecimal.ZERO,
						BigDecimal.ZERO,
						"Consumption"));
		assertThatExceptionOfType(InvalidInventoryLotException.class)
				.isThrownBy(() -> movement(
						InventoryMovementType.EXIT,
						BigDecimal.ONE,
						new BigDecimal("-1"),
						"Consumption"));
		assertThatExceptionOfType(InvalidInventoryLotException.class)
				.isThrownBy(() -> movement(
						InventoryMovementType.EXIT,
						new BigDecimal("0.0000001"),
						BigDecimal.ZERO,
						"Consumption"));
	}

	@Test
	void requiresAJustificationForEveryAdjustment() {
		assertThatExceptionOfType(InvalidInventoryAdjustmentException.class)
				.isThrownBy(() -> movement(
						InventoryMovementType.ADJUSTMENT_IN,
						BigDecimal.ONE,
						new BigDecimal("101"),
						"  "));
		assertThatExceptionOfType(InvalidInventoryAdjustmentException.class)
				.isThrownBy(() -> movement(
						InventoryMovementType.ADJUSTMENT_OUT,
						BigDecimal.ONE,
						new BigDecimal("99"),
						null));
	}

	@Test
	void exposesNoMovementMutationMethods() {
		assertThat(Arrays.stream(InventoryMovement.class.getDeclaredMethods()))
				.noneMatch(method -> method.getName().startsWith("set")
						|| method.getName().startsWith("update")
						|| method.getName().startsWith("delete"));
	}

	private static InventoryMovement movement(
			InventoryMovementType type,
			BigDecimal quantity,
			BigDecimal resultingQuantity,
			String description) {
		return InventoryMovement.create(
				lot(),
				type,
				quantity,
				resultingQuantity,
				null,
				null,
				description,
				Instant.parse("2026-07-01T10:15:30Z"),
				"inventory-admin");
	}

	private static InventoryLot lot() {
		Product product = Product.create(
				"Sakura Essence",
				null,
				"ESS-SAKURA",
				ProductType.RAW_MATERIAL,
				MeasurementUnit.MILLILITER);
		return InventoryLot.create(
				product,
				"ESS-001",
				LocalDate.of(2026, 7, 1),
				LocalDate.of(2026, 8, 10),
				new BigDecimal("100"),
				new BigDecimal("0.50"),
				LocalDate.of(2026, 7, 1));
	}
}
