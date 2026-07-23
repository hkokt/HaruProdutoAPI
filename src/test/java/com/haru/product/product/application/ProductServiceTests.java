package com.haru.product.product.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import com.haru.product.product.application.dto.AddProductComponentRequest;
import com.haru.product.product.application.dto.CreateProductRequest;
import com.haru.product.product.application.dto.ProductCompositionTreeResponse;
import com.haru.product.product.application.dto.UpdateProductRequest;
import com.haru.product.product.domain.MeasurementUnit;
import com.haru.product.product.domain.Product;
import com.haru.product.product.domain.ProductComposition;
import com.haru.product.product.domain.ProductCompositionCycleValidator;
import com.haru.product.product.domain.ProductType;
import com.haru.product.product.domain.exception.DuplicateProductComponentException;
import com.haru.product.product.domain.exception.InvalidProductCompositionException;
import com.haru.product.product.infrastructure.persistence.ProductCompositionRepository;
import com.haru.product.product.infrastructure.persistence.ProductCompositionTopologyLock;
import com.haru.product.product.infrastructure.persistence.PostgreSqlProductSkuGenerator;
import com.haru.product.product.infrastructure.persistence.ProductRepository;

class ProductServiceTests {

	private final ProductRepository productRepository = mock(ProductRepository.class);
	private final ProductCompositionRepository compositionRepository =
			mock(ProductCompositionRepository.class);
	private final ProductCompositionTopologyLock topologyLock =
			mock(ProductCompositionTopologyLock.class);
	private final PostgreSqlProductSkuGenerator skuGenerator =
			mock(PostgreSqlProductSkuGenerator.class);
	private final ProductService service = new ProductService(
			productRepository,
			compositionRepository,
			topologyLock,
			new ProductCompositionCycleValidator(),
			skuGenerator);

	@Test
	void createsAnActiveProductWithAnAutomaticallyGeneratedSku() {
		when(skuGenerator.nextSku()).thenReturn("PRD-0000000001");
		when(productRepository.saveAndFlush(any(Product.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		var response = service.create(new CreateProductRequest(
				"Sakura Essence",
				null,
				ProductType.RAW_MATERIAL,
				MeasurementUnit.MILLILITER,
				null));

		assertThat(response.sku()).isEqualTo("PRD-0000000001");
		assertThat(response.active()).isTrue();
		verify(skuGenerator).nextSku();
	}

	@Test
	void preservesTheSkuWhenUpdatingAProduct() {
		Product product = product(
				1L,
				"Sakura Essence",
				"PRD-0000000042",
				ProductType.RAW_MATERIAL);
		when(productRepository.findById(1L)).thenReturn(Optional.of(product));
		when(productRepository.saveAndFlush(product)).thenReturn(product);

		var response = service.update(
				1L,
				new UpdateProductRequest(
						"Updated Sakura Essence",
						null,
						ProductType.RAW_MATERIAL,
						MeasurementUnit.MILLILITER,
						true));

		assertThat(response.sku()).isEqualTo("PRD-0000000042");
		assertThat(response.name()).isEqualTo("Updated Sakura Essence");
		verifyNoInteractions(skuGenerator);
	}

	@Test
	void rejectsAComponentAlreadyStoredForTheSameParent() {
		Product perfume = product(1L, "Perfume Sakura", "PERF-SAKURA", ProductType.FINISHED_PRODUCT);
		Product essence = product(2L, "Sakura Essence", "ESS-SAKURA", ProductType.RAW_MATERIAL);
		when(productRepository.findById(1L)).thenReturn(Optional.of(perfume));
		when(productRepository.findById(2L)).thenReturn(Optional.of(essence));
		when(compositionRepository.existsByParentProductIdAndComponentProductId(1L, 2L))
				.thenReturn(true);

		assertThatExceptionOfType(DuplicateProductComponentException.class)
				.isThrownBy(() -> service.addComponent(
						1L,
						new AddProductComponentRequest(
								2L,
								BigDecimal.ONE,
								MeasurementUnit.MILLILITER)));
		verify(compositionRepository, never()).saveAndFlush(any());
		InOrder lockBeforeRead = inOrder(topologyLock, productRepository);
		lockBeforeRead.verify(topologyLock).acquire();
		lockBeforeRead.verify(productRepository).findById(1L);
	}

	@Test
	void buildsTheNestedCompositionTree() {
		Product perfume = product(1L, "Perfume Sakura", "PERF-SAKURA", ProductType.FINISHED_PRODUCT);
		Product base = product(2L, "Sakura Base", "BASE-SAKURA", ProductType.INTERMEDIATE_PRODUCT);
		Product essence = product(3L, "Sakura Essence", "ESS-SAKURA", ProductType.RAW_MATERIAL);
		ProductCompositionCycleValidator validator = new ProductCompositionCycleValidator();
		perfume.addComponent(base, new BigDecimal("100"), MeasurementUnit.MILLILITER, validator);
		base.addComponent(essence, new BigDecimal("50"), MeasurementUnit.MILLILITER, validator);
		when(productRepository.findById(1L)).thenReturn(Optional.of(perfume));
		stubCompositionGraph(perfume, base, essence);

		ProductCompositionTreeResponse tree = service.getComposition(1L);

		assertThat(tree.productId()).isEqualTo(1L);
		assertThat(tree.components()).singleElement().satisfies(component -> {
			assertThat(component.productId()).isEqualTo(2L);
			assertThat(component.components()).singleElement().satisfies(nested -> {
				assertThat(nested.productId()).isEqualTo(3L);
				assertThat(nested.quantity()).isEqualByComparingTo("50");
			});
		});
	}

	@Test
	void mapsDirectComponentsFromTheRepositoryFetchPlan() {
		Product perfume = product(1L, "Perfume Sakura", "PERF-SAKURA", ProductType.FINISHED_PRODUCT);
		Product essence = product(2L, "Sakura Essence", "ESS-SAKURA", ProductType.RAW_MATERIAL);
		var composition = perfume.addComponent(
				essence,
				new BigDecimal("50"),
				MeasurementUnit.MILLILITER,
				new ProductCompositionCycleValidator());
		when(productRepository.findById(1L)).thenReturn(Optional.of(perfume));
		when(compositionRepository.findAllByParentProductId(1L))
				.thenReturn(List.of(composition));

		var response = service.getById(1L);

		assertThat(response.components()).singleElement().satisfies(component -> {
			assertThat(component.componentProductId()).isEqualTo(2L);
			assertThat(component.componentProductSku()).isEqualTo("ESS-SAKURA");
		});
		verify(compositionRepository).findAllByParentProductId(1L);
	}

	@Test
	void rejectsACompositionTreeBeyondTheTechnicalDepthBudget() {
		List<Product> products = new ArrayList<>();
		for (long id = 1; id <= 34; id++) {
			products.add(product(
					id,
					"Component " + id,
					"COMP-" + id,
					ProductType.COMPONENT));
		}
		ProductCompositionCycleValidator validator = new ProductCompositionCycleValidator();
		for (int index = 0; index < products.size() - 1; index++) {
			products.get(index).addComponent(
					products.get(index + 1),
					BigDecimal.ONE,
					MeasurementUnit.UNIT,
					validator);
		}
		when(productRepository.findById(1L)).thenReturn(Optional.of(products.getFirst()));
		stubCompositionGraph(products.toArray(Product[]::new));

		assertThatExceptionOfType(InvalidProductCompositionException.class)
				.isThrownBy(() -> service.getComposition(1L))
				.withMessageContaining("maximum depth of 32");
	}

	@Test
	void removesOnlyTheCompositionAndKeepsTheComponentProduct() {
		Product perfume = product(1L, "Perfume Sakura", "PERF-SAKURA", ProductType.FINISHED_PRODUCT);
		Product essence = product(2L, "Sakura Essence", "ESS-SAKURA", ProductType.RAW_MATERIAL);
		var composition = perfume.addComponent(
				essence,
				BigDecimal.ONE,
				MeasurementUnit.MILLILITER,
				new ProductCompositionCycleValidator());
		when(productRepository.findById(1L)).thenReturn(Optional.of(perfume));

		service.removeComponent(1L, 2L);

		verify(compositionRepository).delete(composition);
		verify(compositionRepository).flush();
		verify(productRepository, never()).delete(essence);
		assertThat(essence.isActive()).isTrue();
	}

	@Test
	void returnsANewerSearchVersionWhenDeletingAProduct() {
		Product product = product(1L, "Perfume Sakura", "PERF-SAKURA", ProductType.FINISHED_PRODUCT);
		ReflectionTestUtils.setField(product, "version", 4L);
		when(productRepository.findById(1L)).thenReturn(Optional.of(product));

		var deletion = service.delete(1L);

		assertThat(deletion.id()).isEqualTo(1L);
		assertThat(deletion.databaseVersion()).isEqualTo(5L);
		assertThat(deletion.deletedAt()).isNotNull();
		verify(productRepository).delete(product);
		verify(productRepository).flush();
	}

	private void stubCompositionGraph(Product... products) {
		when(compositionRepository.findAllByParentProductIdIn(
				anyCollection(),
				any(Pageable.class)))
				.thenAnswer(invocation -> {
					Collection<Long> parentIds = invocation.getArgument(0);
					Pageable pageable = invocation.getArgument(1);
					List<ProductComposition> matches = List.of(products).stream()
							.filter(product -> parentIds.contains(product.getId()))
							.flatMap(product -> product.getComponents().stream())
							.toList();
					int start = Math.toIntExact(Math.min(pageable.getOffset(), matches.size()));
					int end = Math.min(start + pageable.getPageSize(), matches.size());
					return matches.subList(start, end);
				});
	}

	private static Product product(Long id, String name, String sku, ProductType type) {
		Product product = Product.create(name, null, sku, type, MeasurementUnit.MILLILITER);
		ReflectionTestUtils.setField(product, "id", id);
		return product;
	}
}
