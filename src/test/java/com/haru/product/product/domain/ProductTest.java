package com.haru.product.product.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.haru.product.product.domain.exception.DuplicateProductComponentException;
import com.haru.product.product.domain.exception.InvalidProductCompositionException;

class ProductTest {

	private final ProductCompositionCycleValidator cycleValidator =
			new ProductCompositionCycleValidator();

	@Test
	void createsTheSakuraPerfumeBillOfMaterials() {
		Product perfume = product(
				"Perfume Sakura 100 ml",
				"PERF-SAKURA-100ML",
				ProductType.FINISHED_PRODUCT,
				MeasurementUnit.UNIT);
		Product essence = rawMaterial("Sakura Essence", "ESS-SAKURA");
		Product alcohol = rawMaterial("Grain Alcohol", "ALCOHOL-GRAIN");
		Product water = rawMaterial("Demineralized Water", "WATER-DEMINERALIZED");

		perfume.addComponent(essence, new BigDecimal("50"), MeasurementUnit.MILLILITER, cycleValidator);
		perfume.addComponent(alcohol, new BigDecimal("40"), MeasurementUnit.MILLILITER, cycleValidator);
		perfume.addComponent(water, new BigDecimal("10"), MeasurementUnit.MILLILITER, cycleValidator);

		assertThat(perfume.getComponents()).hasSize(3);
		assertThat(quantityOf(perfume, essence)).isEqualByComparingTo("50");
		assertThat(quantityOf(perfume, alcohol)).isEqualByComparingTo("40");
		assertThat(quantityOf(perfume, water)).isEqualByComparingTo("10");
		assertThat(perfume.getComponents())
				.extracting(ProductComposition::getMeasurementUnit)
				.containsOnly(MeasurementUnit.MILLILITER);
		assertThat(perfume.getComponents())
				.extracting(composition -> composition.getComponentProduct().getSku())
				.doesNotHaveDuplicates();
	}

	@Test
	void rejectsADuplicateComponent() {
		Product perfume = finishedProduct("Perfume Sakura", "PERF-SAKURA");
		Product essence = rawMaterial("Sakura Essence", "ESS-SAKURA");
		perfume.addComponent(essence, BigDecimal.ONE, MeasurementUnit.MILLILITER, cycleValidator);

		assertThatExceptionOfType(DuplicateProductComponentException.class)
				.isThrownBy(() -> perfume.addComponent(
						essence,
						BigDecimal.TEN,
						MeasurementUnit.MILLILITER,
						cycleValidator));
	}

	@Test
	void rejectsZeroAndNegativeQuantities() {
		Product perfume = finishedProduct("Perfume Sakura", "PERF-SAKURA");
		Product essence = rawMaterial("Sakura Essence", "ESS-SAKURA");

		assertThatExceptionOfType(InvalidProductCompositionException.class)
				.isThrownBy(() -> perfume.addComponent(
						essence,
						BigDecimal.ZERO,
						MeasurementUnit.MILLILITER,
						cycleValidator));
		assertThatExceptionOfType(InvalidProductCompositionException.class)
				.isThrownBy(() -> perfume.addComponent(
						essence,
						new BigDecimal("-10"),
						MeasurementUnit.MILLILITER,
						cycleValidator));
	}

	@Test
	void rejectsQuantitiesThatDoNotFitTheDatabasePrecisionAndScale() {
		Product perfume = finishedProduct("Perfume Sakura", "PERF-SAKURA");
		Product essence = rawMaterial("Sakura Essence", "ESS-SAKURA");

		assertThatExceptionOfType(InvalidProductCompositionException.class)
				.isThrownBy(() -> perfume.addComponent(
						essence,
						new BigDecimal("0.0000001"),
						MeasurementUnit.MILLILITER,
						cycleValidator));
		assertThatExceptionOfType(InvalidProductCompositionException.class)
				.isThrownBy(() -> perfume.addComponent(
						essence,
						new BigDecimal("10000000000000"),
						MeasurementUnit.MILLILITER,
						cycleValidator));
	}

	@Test
	void rejectsAMissingComponentOrMeasurementUnit() {
		Product perfume = finishedProduct("Perfume Sakura", "PERF-SAKURA");
		Product essence = rawMaterial("Sakura Essence", "ESS-SAKURA");

		assertThatExceptionOfType(InvalidProductCompositionException.class)
				.isThrownBy(() -> perfume.addComponent(
						null,
						BigDecimal.ONE,
						MeasurementUnit.MILLILITER,
						cycleValidator));
		assertThatExceptionOfType(InvalidProductCompositionException.class)
				.isThrownBy(() -> perfume.addComponent(
						essence,
						BigDecimal.ONE,
						null,
						cycleValidator));
	}

	@Test
	void rejectsAProductContainingItself() {
		Product perfume = finishedProduct("Perfume Sakura", "PERF-SAKURA");

		assertThatExceptionOfType(InvalidProductCompositionException.class)
				.isThrownBy(() -> perfume.addComponent(
						perfume,
						BigDecimal.ONE,
						MeasurementUnit.UNIT,
						cycleValidator))
				.withMessageContaining("cannot contain itself");
	}

	@Test
	void rejectsAnInactiveComponent() {
		Product perfume = finishedProduct("Perfume Sakura", "PERF-SAKURA");
		Product essence = rawMaterial("Sakura Essence", "ESS-SAKURA");
		essence.deactivate();

		assertThatExceptionOfType(InvalidProductCompositionException.class)
				.isThrownBy(() -> perfume.addComponent(
						essence,
						BigDecimal.ONE,
						MeasurementUnit.MILLILITER,
						cycleValidator))
				.withMessageContaining("must be active");
	}

	@Test
	void rejectsAPhysicalBillOfMaterialsForAService() {
		Product service = product(
				"Formula Consulting",
				"SERVICE-FORMULA",
				ProductType.SERVICE,
				MeasurementUnit.UNIT);
		Product essence = rawMaterial("Sakura Essence", "ESS-SAKURA");

		assertThatExceptionOfType(InvalidProductCompositionException.class)
				.isThrownBy(() -> service.addComponent(
						essence,
						BigDecimal.ONE,
						MeasurementUnit.MILLILITER,
						cycleValidator))
				.withMessageContaining("service product");
	}

	@Test
	void doesNotAllowChangingAProductWithComponentsIntoAService() {
		Product perfume = finishedProduct("Perfume Sakura", "PERF-SAKURA");
		Product essence = rawMaterial("Sakura Essence", "ESS-SAKURA");
		perfume.addComponent(essence, BigDecimal.ONE, MeasurementUnit.MILLILITER, cycleValidator);

		assertThatExceptionOfType(InvalidProductCompositionException.class)
				.isThrownBy(() -> perfume.update(
						"Perfume Sakura",
						null,
						"PERF-SAKURA",
						ProductType.SERVICE,
						MeasurementUnit.UNIT,
						true));
	}

	@Test
	void normalizesUnnecessarySkuWhitespaceWithoutGeneratingIt() {
		Product product = finishedProduct("Perfume Sakura", "  PERF   SAKURA-100ML  ");
		Product productWithLongRawWhitespace = finishedProduct(
				"Whitespace SKU",
				"SKU" + " ".repeat(100) + "ONE");

		assertThat(product.getSku()).isEqualTo("PERF SAKURA-100ML");
		assertThat(productWithLongRawWhitespace.getSku()).isEqualTo("SKU ONE");
		assertThatExceptionOfType(IllegalArgumentException.class)
				.isThrownBy(() -> finishedProduct("Missing SKU", "   "));
		assertThatExceptionOfType(IllegalArgumentException.class)
				.isThrownBy(() -> finishedProduct("Long SKU", "S".repeat(61)));
	}

	@Test
	void exposesTheCompositionAsAnUnmodifiableCollection() {
		Product perfume = finishedProduct("Perfume Sakura", "PERF-SAKURA");
		Product essence = rawMaterial("Sakura Essence", "ESS-SAKURA");
		ProductComposition composition = perfume.addComponent(
				essence,
				BigDecimal.ONE,
				MeasurementUnit.MILLILITER,
				cycleValidator);

		assertThatExceptionOfType(UnsupportedOperationException.class)
				.isThrownBy(() -> perfume.getComponents().remove(composition));
	}

	@Test
	void removingACompositionKeepsTheComponentProduct() {
		Product perfume = finishedProduct("Perfume Sakura", "PERF-SAKURA");
		Product essence = rawMaterial("Sakura Essence", "ESS-SAKURA");
		ProductComposition composition = perfume.addComponent(
				essence,
				BigDecimal.ONE,
				MeasurementUnit.MILLILITER,
				cycleValidator);

		ProductComposition removed = perfume.removeComponent(essence.getId());

		assertThat(removed).isSameAs(composition);
		assertThat(removed.getComponentProduct()).isSameAs(essence);
		assertThat(perfume.getComponents()).isEmpty();
		assertThat(essence.isActive()).isTrue();
	}

	private static BigDecimal quantityOf(Product parent, Product component) {
		return parent.getComponents().stream()
				.filter(composition -> composition.getComponentProduct() == component)
				.findFirst()
				.orElseThrow()
				.getQuantity();
	}

	private static Product finishedProduct(String name, String sku) {
		return product(name, sku, ProductType.FINISHED_PRODUCT, MeasurementUnit.UNIT);
	}

	private static Product rawMaterial(String name, String sku) {
		return product(name, sku, ProductType.RAW_MATERIAL, MeasurementUnit.MILLILITER);
	}

	private static Product product(
			String name,
			String sku,
			ProductType type,
			MeasurementUnit unit) {
		return Product.create(name, null, sku, type, unit);
	}
}
