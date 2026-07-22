package com.haru.product.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.haru.product.inventory.domain.exception.BlockedInventoryLotException;
import com.haru.product.inventory.domain.exception.ExpiredInventoryLotException;
import com.haru.product.inventory.domain.exception.InsufficientInventoryException;
import com.haru.product.inventory.domain.exception.InvalidInventoryLotException;
import com.haru.product.product.domain.MeasurementUnit;
import com.haru.product.product.domain.Product;
import com.haru.product.product.domain.ProductType;

class InventoryLotTest {

	private static final LocalDate JULY_FIRST_2026 = LocalDate.of(2026, 7, 1);

	@Test
	void createsTheSakuraEssenceLotWithItsInitialBalance() {
		Product essence = essence();

		InventoryLot lot = InventoryLot.create(
				essence,
				"  ESS-001  ",
				JULY_FIRST_2026,
				LocalDate.of(2026, 8, 10),
				new BigDecimal("100.000000"),
				new BigDecimal("0.5000"),
				JULY_FIRST_2026);

		assertThat(lot.getProduct()).isSameAs(essence);
		assertThat(lot.getLotNumber()).isEqualTo("ESS-001");
		assertThat(lot.getInitialQuantity()).isEqualByComparingTo("100");
		assertThat(lot.getAvailableQuantity()).isEqualByComparingTo("100");
		assertThat(lot.getUnitCost()).isEqualByComparingTo("0.5");
		assertThat(lot.getStatus()).isEqualTo(InventoryLotStatus.AVAILABLE);
		assertThat(lot.isAvailable(JULY_FIRST_2026)).isTrue();
		assertThat(lot.getCreatedAt()).isNotNull();
		assertThat(lot.getUpdatedAt()).isNotNull();
	}

	@Test
	void rejectsAnExpirationDateBeforeManufacture() {
		assertThatExceptionOfType(InvalidInventoryLotException.class)
				.isThrownBy(() -> InventoryLot.create(
						essence(),
						"ESS-001",
						LocalDate.of(2026, 8, 10),
						LocalDate.of(2026, 8, 1),
						new BigDecimal("100"),
						new BigDecimal("0.50"),
						JULY_FIRST_2026))
				.withMessageContaining("cannot be before");
	}

	@Test
	void rejectsMissingOrOversizedLotData() {
		assertThatExceptionOfType(InvalidInventoryLotException.class)
				.isThrownBy(() -> InventoryLot.create(
						null,
						"ESS-001",
						null,
						null,
						BigDecimal.ONE,
						BigDecimal.ZERO,
						JULY_FIRST_2026));
		assertThatExceptionOfType(InvalidInventoryLotException.class)
				.isThrownBy(() -> lot("   ", BigDecimal.ONE, BigDecimal.ZERO));
		assertThatExceptionOfType(InvalidInventoryLotException.class)
				.isThrownBy(() -> lot("L".repeat(81), BigDecimal.ONE, BigDecimal.ZERO));
	}

	@Test
	void rejectsInvalidQuantityAndCostValues() {
		assertThatExceptionOfType(InvalidInventoryLotException.class)
				.isThrownBy(() -> lot("ESS-ZERO", BigDecimal.ZERO, BigDecimal.ZERO));
		assertThatExceptionOfType(InvalidInventoryLotException.class)
				.isThrownBy(() -> lot("ESS-NEGATIVE", BigDecimal.ONE, new BigDecimal("-0.01")));
		assertThatExceptionOfType(InvalidInventoryLotException.class)
				.isThrownBy(() -> lot("ESS-QUANTITY-SCALE", new BigDecimal("0.0000001"), BigDecimal.ZERO));
		assertThatExceptionOfType(InvalidInventoryLotException.class)
				.isThrownBy(() -> lot("ESS-COST-SCALE", BigDecimal.ONE, new BigDecimal("0.00001")));
		assertThatExceptionOfType(InvalidInventoryLotException.class)
				.isThrownBy(() -> lot("ESS-QUANTITY-PRECISION", new BigDecimal("10000000000000"), BigDecimal.ZERO));
		assertThatExceptionOfType(InvalidInventoryLotException.class)
				.isThrownBy(() -> lot("ESS-COST-PRECISION", BigDecimal.ONE, new BigDecimal("1000000000000000")));
	}

	@Test
	void treatsTheLotAsValidThroughItsExpirationDate() {
		InventoryLot lot = InventoryLot.create(
				essence(),
				"ESS-001",
				JULY_FIRST_2026,
				LocalDate.of(2026, 8, 10),
				new BigDecimal("200"),
				new BigDecimal("0.50"),
				JULY_FIRST_2026);

		assertThat(lot.isExpired(LocalDate.of(2026, 8, 10))).isFalse();
		assertThat(lot.isAvailable(LocalDate.of(2026, 8, 10))).isTrue();
		assertThat(lot.isExpired(LocalDate.of(2026, 8, 11))).isTrue();
		assertThat(lot.isAvailable(LocalDate.of(2026, 8, 11))).isFalse();
	}

	@Test
	void createsAnAlreadyExpiredLotWithExpiredStatus() {
		InventoryLot lot = InventoryLot.create(
				essence(),
				"ESS-OLD",
				LocalDate.of(2026, 6, 1),
				LocalDate.of(2026, 6, 30),
				new BigDecimal("200"),
				new BigDecimal("0.50"),
				JULY_FIRST_2026);

		assertThat(lot.getStatus()).isEqualTo(InventoryLotStatus.EXPIRED);
		assertThat(lot.isAvailable(JULY_FIRST_2026)).isFalse();
		assertThatExceptionOfType(ExpiredInventoryLotException.class)
				.isThrownBy(() -> lot.decreaseAvailableQuantity(BigDecimal.ONE, JULY_FIRST_2026));
	}

	@Test
	void decreasesItsBalanceAndMarksItselfAsDepletedAtZero() {
		InventoryLot lot = lot("ESS-001", new BigDecimal("30"), BigDecimal.ZERO);

		lot.decreaseAvailableQuantity(new BigDecimal("10"), JULY_FIRST_2026);

		assertThat(lot.getAvailableQuantity()).isEqualByComparingTo("20");
		assertThat(lot.getStatus()).isEqualTo(InventoryLotStatus.AVAILABLE);

		lot.decreaseAvailableQuantity(new BigDecimal("20"), JULY_FIRST_2026);

		assertThat(lot.getAvailableQuantity()).isZero();
		assertThat(lot.getStatus()).isEqualTo(InventoryLotStatus.DEPLETED);
		assertThat(lot.isAvailable(JULY_FIRST_2026)).isFalse();
	}

	@Test
	void preventsADecreaseThatWouldMakeTheBalanceNegative() {
		InventoryLot lot = lot("ESS-001", new BigDecimal("40"), BigDecimal.ZERO);

		assertThatExceptionOfType(InsufficientInventoryException.class)
				.isThrownBy(() -> lot.decreaseAvailableQuantity(
						new BigDecimal("50"),
						JULY_FIRST_2026));

		assertThat(lot.getAvailableQuantity()).isEqualByComparingTo("40");
		assertThat(lot.getStatus()).isEqualTo(InventoryLotStatus.AVAILABLE);
	}

	@Test
	void preventsConsumptionFromABlockedLot() {
		InventoryLot lot = lot("ESS-001", new BigDecimal("50"), BigDecimal.ZERO);
		lot.block();

		assertThat(lot.getStatus()).isEqualTo(InventoryLotStatus.BLOCKED);
		assertThat(lot.isAvailable(JULY_FIRST_2026)).isFalse();
		assertThatExceptionOfType(BlockedInventoryLotException.class)
				.isThrownBy(() -> lot.decreaseAvailableQuantity(
						BigDecimal.ONE,
						JULY_FIRST_2026));
		assertThat(lot.getAvailableQuantity()).isEqualByComparingTo("50");
	}

	@Test
	void increasesADepletedBalanceThroughAnExplicitDomainOperation() {
		InventoryLot lot = lot("ESS-001", BigDecimal.TEN, BigDecimal.ZERO);
		lot.decreaseAvailableQuantity(BigDecimal.TEN, JULY_FIRST_2026);

		lot.increaseAvailableQuantity(new BigDecimal("5"), JULY_FIRST_2026);

		assertThat(lot.getAvailableQuantity()).isEqualByComparingTo("5");
		assertThat(lot.getStatus()).isEqualTo(InventoryLotStatus.AVAILABLE);
	}

	@Test
	void doesNotAllowADepletedStatusWhileQuantityRemains() {
		InventoryLot lot = lot("ESS-001", BigDecimal.ONE, BigDecimal.ZERO);

		assertThatExceptionOfType(InvalidInventoryLotException.class)
				.isThrownBy(lot::markAsDepleted);
	}

	@Test
	void doesNotExposeAnAvailableQuantitySetter() {
		assertThat(Arrays.stream(InventoryLot.class.getMethods()))
				.noneMatch(method -> method.getName().equals("setAvailableQuantity"));
	}

	private static InventoryLot lot(
			String lotNumber,
			BigDecimal initialQuantity,
			BigDecimal unitCost) {
		return InventoryLot.create(
				essence(),
				lotNumber,
				JULY_FIRST_2026,
				LocalDate.of(2026, 8, 10),
				initialQuantity,
				unitCost,
				JULY_FIRST_2026);
	}

	private static Product essence() {
		return Product.create(
				"Sakura Essence",
				null,
				"ESS-SAKURA",
				ProductType.RAW_MATERIAL,
				MeasurementUnit.MILLILITER);
	}
}
