package com.haru.product.production.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import com.haru.product.inventory.domain.InventoryLot;
import com.haru.product.product.domain.MeasurementUnit;
import com.haru.product.product.domain.Product;
import com.haru.product.product.domain.ProductType;
import com.haru.product.production.domain.exception.InvalidProductionOrderException;

class ProductionTraceabilityTest {

	private static final Instant CREATED_AT = Instant.parse("2026-07-22T11:00:00Z");
	private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 7, 22);

	@Test
	void recordsTheExactComponentLotConsumedByAnOrder() {
		ProductionOrder order = order();
		Product essence = rawMaterial("Sakura Essence", "ESS-SAKURA");
		InventoryLot consumedLot = inventoryLot(essence, "ESS-001", new BigDecimal("100"));

		ProductionConsumption consumption = ProductionConsumption.create(
				order,
				essence,
				consumedLot,
				new BigDecimal("50.000000"),
				MeasurementUnit.MILLILITER,
				CREATED_AT);

		assertThat(consumption.getProductionOrder()).isSameAs(order);
		assertThat(consumption.getComponentProduct()).isSameAs(essence);
		assertThat(consumption.getConsumedLot()).isSameAs(consumedLot);
		assertThat(consumption.getConsumedQuantity()).isEqualByComparingTo("50");
		assertThat(consumption.getMeasurementUnit()).isEqualTo(MeasurementUnit.MILLILITER);
		assertThat(consumption.getCreatedAt()).isEqualTo(CREATED_AT);
	}

	@Test
	void linksTheProducedInventoryLotToItsOrderWithoutDuplicatingLotData() {
		ProductionOrder order = order();
		InventoryLot finalLot = inventoryLot(
				order.getProduct(),
				"PERF-SAKURA-001",
				BigDecimal.TEN);

		ProducedLot producedLot = ProducedLot.create(
				order,
				finalLot,
				new BigDecimal("10.000000"),
				CREATED_AT);

		assertThat(producedLot.getProductionOrder()).isSameAs(order);
		assertThat(producedLot.getInventoryLot()).isSameAs(finalLot);
		assertThat(producedLot.getProducedQuantity()).isEqualByComparingTo("10");
		assertThat(producedLot.getCreatedAt()).isEqualTo(CREATED_AT);
		assertThat(Arrays.stream(ProducedLot.class.getDeclaredFields()).map(field -> field.getName()))
				.doesNotContain("lotNumber", "manufactureDate", "expirationDate", "unitCost");
	}

	@Test
	void validatesEveryProductionConsumptionInvariant() {
		ProductionOrder order = order();
		Product essence = rawMaterial("Sakura Essence", "ESS-SAKURA");
		Product alcohol = rawMaterial("Cereal Alcohol", "ALCOHOL-CEREAL");
		InventoryLot essenceLot = inventoryLot(essence, "ESS-001", BigDecimal.TEN);

		assertInvalidConsumption(null, essence, essenceLot, BigDecimal.ONE,
				MeasurementUnit.MILLILITER, CREATED_AT);
		assertInvalidConsumption(order, null, essenceLot, BigDecimal.ONE,
				MeasurementUnit.MILLILITER, CREATED_AT);
		assertInvalidConsumption(order, essence, null, BigDecimal.ONE,
				MeasurementUnit.MILLILITER, CREATED_AT);
		assertInvalidConsumption(order, alcohol, essenceLot, BigDecimal.ONE,
				MeasurementUnit.MILLILITER, CREATED_AT);
		assertInvalidConsumption(order, essence, essenceLot, BigDecimal.ZERO,
				MeasurementUnit.MILLILITER, CREATED_AT);
		assertInvalidConsumption(order, essence, essenceLot, new BigDecimal("0.0000001"),
				MeasurementUnit.MILLILITER, CREATED_AT);
		assertInvalidConsumption(order, essence, essenceLot, BigDecimal.ONE, null, CREATED_AT);
		assertInvalidConsumption(order, essence, essenceLot, BigDecimal.ONE,
				MeasurementUnit.MILLILITER, null);
	}

	@Test
	void validatesEveryProducedLotInvariant() {
		ProductionOrder order = order();
		InventoryLot correctLot = inventoryLot(
				order.getProduct(),
				"PERF-SAKURA-001",
				BigDecimal.ONE);
		InventoryLot wrongProductLot = inventoryLot(
				rawMaterial("Sakura Essence", "ESS-SAKURA"),
				"ESS-001",
				BigDecimal.ONE);

		assertInvalidProducedLot(null, correctLot, BigDecimal.ONE, CREATED_AT);
		assertInvalidProducedLot(order, null, BigDecimal.ONE, CREATED_AT);
		assertInvalidProducedLot(order, wrongProductLot, BigDecimal.ONE, CREATED_AT);
		assertInvalidProducedLot(order, correctLot, BigDecimal.ZERO, CREATED_AT);
		assertInvalidProducedLot(order, correctLot, new BigDecimal("10000000000000"), CREATED_AT);
		assertInvalidProducedLot(order, correctLot, BigDecimal.ONE, null);
	}

	@Test
	void exposesNoMutationOperationsForTraceabilityRecords() {
		assertThat(Arrays.stream(ProductionConsumption.class.getDeclaredMethods()))
				.noneMatch(ProductionTraceabilityTest::isMutationMethod);
		assertThat(Arrays.stream(ProducedLot.class.getDeclaredMethods()))
				.noneMatch(ProductionTraceabilityTest::isMutationMethod);
	}

	private static boolean isMutationMethod(java.lang.reflect.Method method) {
		return method.getName().startsWith("set")
				|| method.getName().startsWith("update")
				|| method.getName().startsWith("delete");
	}

	private static void assertInvalidConsumption(
			ProductionOrder order,
			Product component,
			InventoryLot lot,
			BigDecimal quantity,
			MeasurementUnit unit,
			Instant createdAt) {
		assertThatExceptionOfType(InvalidProductionOrderException.class)
				.isThrownBy(() -> ProductionConsumption.create(
						order,
						component,
						lot,
						quantity,
						unit,
						createdAt));
	}

	private static void assertInvalidProducedLot(
			ProductionOrder order,
			InventoryLot lot,
			BigDecimal quantity,
			Instant createdAt) {
		assertThatExceptionOfType(InvalidProductionOrderException.class)
				.isThrownBy(() -> ProducedLot.create(order, lot, quantity, createdAt));
	}

	private static ProductionOrder order() {
		return ProductionOrder.create(
				Product.create(
						"Sakura Perfume 100 ml",
						null,
						"PERF-SAKURA-100ML",
						ProductType.FINISHED_PRODUCT,
						MeasurementUnit.UNIT),
				BigDecimal.TEN,
				CREATED_AT);
	}

	private static Product rawMaterial(String name, String sku) {
		return Product.create(
				name,
				null,
				sku,
				ProductType.RAW_MATERIAL,
				MeasurementUnit.MILLILITER);
	}

	private static InventoryLot inventoryLot(
			Product product,
			String lotNumber,
			BigDecimal quantity) {
		return InventoryLot.create(
				product,
				lotNumber,
				REFERENCE_DATE,
				REFERENCE_DATE.plusMonths(12),
				quantity,
				BigDecimal.ZERO,
				REFERENCE_DATE);
	}
}
