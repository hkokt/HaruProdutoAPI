package com.haru.product.product.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Collection;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;

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

	@Test
	void alignsSkuPrechecksWithTheLowercaseDatabaseIndex() throws NoSuchMethodException {
		Method createCheck = ProductRepository.class.getMethod(
				"existsBySkuIgnoreCase",
				String.class);
		Method updateCheck = ProductRepository.class.getMethod(
				"existsBySkuIgnoreCaseAndIdNot",
				String.class,
				Long.class);

		assertThat(createCheck.getAnnotation(Query.class).value())
				.contains("lower(product.sku) = lower(:sku)");
		assertThat(updateCheck.getAnnotation(Query.class).value())
				.contains("lower(product.sku) = lower(:sku)");
	}
}
