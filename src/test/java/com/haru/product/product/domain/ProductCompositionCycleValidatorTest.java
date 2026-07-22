package com.haru.product.product.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.haru.product.product.domain.exception.ProductCompositionCycleException;

class ProductCompositionCycleValidatorTest {

	private final ProductCompositionCycleValidator cycleValidator =
			new ProductCompositionCycleValidator();

	@Test
	void rejectsAnIndirectCompositionCycle() {
		Product perfume = product("Perfume Sakura", "PERF-SAKURA");
		Product base = product("Sakura Base", "BASE-SAKURA");
		Product essence = product("Sakura Essence", "ESS-SAKURA");
		perfume.addComponent(base, BigDecimal.ONE, MeasurementUnit.LITER, cycleValidator);
		base.addComponent(essence, BigDecimal.ONE, MeasurementUnit.LITER, cycleValidator);

		assertThatExceptionOfType(ProductCompositionCycleException.class)
				.isThrownBy(() -> essence.addComponent(
						perfume,
						BigDecimal.ONE,
						MeasurementUnit.LITER,
						cycleValidator))
				.withMessageContaining("composition cycle");
		assertThat(essence.getComponents()).isEmpty();
	}

	@Test
	void acceptsAnAcyclicGraphWithASharedComponent() {
		Product perfume = product("Perfume Sakura", "PERF-SAKURA");
		Product kit = product("Sakura Kit", "KIT-SAKURA");
		Product essence = product("Sakura Essence", "ESS-SAKURA");
		perfume.addComponent(essence, BigDecimal.ONE, MeasurementUnit.LITER, cycleValidator);
		kit.addComponent(essence, BigDecimal.ONE, MeasurementUnit.LITER, cycleValidator);

		perfume.addComponent(kit, BigDecimal.ONE, MeasurementUnit.UNIT, cycleValidator);

		assertThat(perfume.getComponents()).hasSize(2);
		assertThat(kit.getComponents()).hasSize(1);
	}

	@Test
	void validatesADeepGraphWithoutRecursiveStackGrowth() {
		List<Product> products = new ArrayList<>();
		for (int index = 0; index < 5_000; index++) {
			products.add(product("Component " + index, "COMP-" + index));
		}
		for (int index = 0; index < products.size() - 1; index++) {
			products.get(index).addComponent(
					products.get(index + 1),
					BigDecimal.ONE,
					MeasurementUnit.UNIT,
					cycleValidator);
		}

		Product unrelatedParent = product("Unrelated", "UNRELATED");
		cycleValidator.validate(unrelatedParent, products.getFirst());

		assertThat(products.getFirst().getComponents()).hasSize(1);
	}

	private static Product product(String name, String sku) {
		return Product.create(
				name,
				null,
				sku,
				ProductType.COMPONENT,
				MeasurementUnit.UNIT);
	}
}
