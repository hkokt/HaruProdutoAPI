package com.haru.product.product.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Collection;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;

class ProductRepositoryFetchPlanTests {

	@Test
	void fetchesComponentProductsForDirectBomMapping() throws NoSuchMethodException {
		Method method = ProductCompositionRepository.class.getMethod(
				"findAllByParentProductId",
				Long.class);

		assertThat(method.getAnnotation(EntityGraph.class).attributePaths())
				.containsExactly("componentProduct");
	}

	@Test
	void fetchesBothSidesWhenLoadingBomLevelsInBatches() throws NoSuchMethodException {
		Method method = ProductCompositionRepository.class.getMethod(
				"findAllByParentProductIdIn",
				Collection.class,
				Pageable.class);

		assertThat(method.getAnnotation(EntityGraph.class).attributePaths())
				.containsExactly("parentProduct", "componentProduct");
	}

}
