package com.haru.product.production.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.EntityGraph;

class ProductionRepositoryFetchPlanTests {

	@Test
	void fetchesTheOrderProduct() throws NoSuchMethodException {
		Method method = ProductionOrderRepository.class.getMethod("findById", Long.class);

		assertThat(method.getAnnotation(EntityGraph.class).attributePaths())
				.containsExactly("product");
	}

	@Test
	void fetchesConsumptionTraceAssociations() throws NoSuchMethodException {
		Method method = ProductionConsumptionRepository.class.getMethod(
				"findAllByProductionOrderIdOrderByIdAsc",
				Long.class);

		assertThat(method.getAnnotation(EntityGraph.class).attributePaths())
				.containsExactly("componentProduct", "consumedLot");
	}

	@Test
	void fetchesProducedLotTraceAssociations() throws NoSuchMethodException {
		Method method = ProducedLotRepository.class.getMethod(
				"findByProductionOrderId",
				Long.class);

		assertThat(method.getAnnotation(EntityGraph.class).attributePaths())
				.containsExactly(
						"inventoryLot",
						"productionOrder",
						"productionOrder.product");
	}
}
