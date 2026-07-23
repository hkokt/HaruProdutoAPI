package com.haru.product.production.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import com.haru.product.inventory.domain.InventoryLot;
import com.haru.product.inventory.domain.InventoryLotStatus;
import com.haru.product.inventory.domain.InventoryMovement;
import com.haru.product.inventory.domain.InventoryMovementType;
import com.haru.product.inventory.domain.exception.InsufficientInventoryException;
import com.haru.product.inventory.infrastructure.persistence.InventoryLotRepository;
import com.haru.product.inventory.infrastructure.persistence.InventoryMovementRepository;
import com.haru.product.product.domain.MeasurementUnit;
import com.haru.product.product.domain.Product;
import com.haru.product.product.domain.ProductComposition;
import com.haru.product.product.domain.ProductType;
import com.haru.product.product.infrastructure.persistence.ProductCompositionRepository;
import com.haru.product.product.infrastructure.persistence.ProductRepository;
import com.haru.product.production.application.dto.CompleteProductionRequest;
import com.haru.product.production.application.dto.CreateProductionOrderRequest;
import com.haru.product.production.domain.ProducedLot;
import com.haru.product.production.domain.ProductionConsumption;
import com.haru.product.production.domain.ProductionOrder;
import com.haru.product.production.domain.ProductionOrderStatus;
import com.haru.product.production.domain.exception.InvalidProductionOrderStateException;
import com.haru.product.production.domain.exception.ProductWithoutBomException;
import com.haru.product.production.infrastructure.persistence.ProducedLotRepository;
import com.haru.product.production.infrastructure.persistence.ProductionConsumptionRepository;
import com.haru.product.production.infrastructure.persistence.ProductionOrderRepository;
import com.haru.product.shared.pagination.OffsetLimitPageable;

class ProductionServiceTests {

	private static final Long FINISHED_PRODUCT_ID = 1L;
	private static final Long ESSENCE_ID = 2L;
	private static final Long ALCOHOL_ID = 3L;
	private static final Long WATER_ID = 4L;
	private static final Long ORDER_ID = 100L;
	private static final Long ESSENCE_LOT_ID = 201L;
	private static final Long ALCOHOL_LOT_ID = 301L;
	private static final Long WATER_LOT_ID = 401L;
	private static final Long PRODUCED_INVENTORY_LOT_ID = 900L;
	private static final Instant FIXED_INSTANT = Instant.parse("2026-07-22T12:00:00Z");
	private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 7, 22);
	private static final LocalDate FUTURE_EXPIRATION = LocalDate.of(2027, 7, 22);
	private static final String ACTOR = "production-admin@example.com";

	private final ProductionOrderRepository productionOrderRepository =
			mock(ProductionOrderRepository.class);
	private final ProductionConsumptionRepository productionConsumptionRepository =
			mock(ProductionConsumptionRepository.class);
	private final ProducedLotRepository producedLotRepository =
			mock(ProducedLotRepository.class);
	private final ProductRepository productRepository = mock(ProductRepository.class);
	private final ProductCompositionRepository productCompositionRepository =
			mock(ProductCompositionRepository.class);
	private final InventoryLotRepository inventoryLotRepository =
			mock(InventoryLotRepository.class);
	private final InventoryMovementRepository inventoryMovementRepository =
			mock(InventoryMovementRepository.class);
	private final ProductionService service = new ProductionService(
			productionOrderRepository,
			productionConsumptionRepository,
			producedLotRepository,
			productRepository,
			productCompositionRepository,
			inventoryLotRepository,
			inventoryMovementRepository,
			Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC));

	@Test
	void searchesOrdersWithAnArbitraryOffsetWithoutLoadingTraceDetails() {
		ProductionOrder order = ProductionOrder.create(
				finishedProduct(),
				BigDecimal.TEN,
				FIXED_INSTANT);
		ReflectionTestUtils.setField(order, "id", ORDER_ID);
		OffsetLimitPageable pageable = OffsetLimitPageable.of(15, 20);
		when(productionOrderRepository.search(
				"100",
				ORDER_ID,
				ProductionOrderStatus.CREATED,
				pageable))
				.thenReturn(new PageImpl<>(List.of(order), pageable, 36));

		var response = service.search(" 100 ", ProductionOrderStatus.CREATED, 15, 20);

		assertThat(response.offset()).isEqualTo(15);
		assertThat(response.limit()).isEqualTo(20);
		assertThat(response.totalElements()).isEqualTo(36);
		assertThat(response.hasPrevious()).isTrue();
		assertThat(response.hasNext()).isTrue();
		assertThat(response.content()).singleElement().satisfies(result -> {
			assertThat(result.id()).isEqualTo(ORDER_ID);
			assertThat(result.productName()).isEqualTo("Sakura Perfume 100 ml");
		});
		verify(productionOrderRepository).search(
				"100",
				ORDER_ID,
				ProductionOrderStatus.CREATED,
				pageable);
		verifyNoInteractions(productionConsumptionRepository, producedLotRepository);
	}

	@Test
	void requiresBomWhenCreatingProductionOrder() {
		Product finishedProduct = finishedProduct();
		when(productRepository.findById(FINISHED_PRODUCT_ID))
				.thenReturn(Optional.of(finishedProduct));
		when(productCompositionRepository.findAllByParentProductId(FINISHED_PRODUCT_ID))
				.thenReturn(List.of());

		assertThatExceptionOfType(ProductWithoutBomException.class)
				.isThrownBy(() -> service.create(
						new CreateProductionOrderRequest(FINISHED_PRODUCT_ID, BigDecimal.ONE)));

		verify(productionOrderRepository, never()).saveAndFlush(any(ProductionOrder.class));
	}

	@Test
	void multipliesEveryBomRequirementExactlyForTenUnits() {
		SakuraScenario scenario = sakuraScenario();
		ProductionOrder order = inProgressOrder(scenario.finishedProduct(), "10");
		stubOrderAndBom(order, scenario.compositions());
		stubSuccessfulLot(
				scenario.essence(), ESSENCE_LOT_ID, "ESS-001", "600", "100");
		stubSuccessfulLot(
				scenario.alcohol(), ALCOHOL_LOT_ID, "ALC-001", "500", "100");
		stubSuccessfulLot(
				scenario.water(), WATER_LOT_ID, "WAT-001", "200", "100");
		stubSuccessfulCompletionPersistence();

		service.complete(ORDER_ID, completionRequest("PERF-SAKURA-010"), ACTOR);

		ArgumentCaptor<BigDecimal> quantityCaptor = ArgumentCaptor.forClass(BigDecimal.class);
		verify(inventoryLotRepository, times(3)).decreaseForConsumption(
				anyLong(),
				quantityCaptor.capture(),
				eq(REFERENCE_DATE),
				eq(FIXED_INSTANT),
				eq(InventoryLotStatus.AVAILABLE),
				eq(InventoryLotStatus.DEPLETED));
		assertQuantities(quantityCaptor.getAllValues(), "500", "400", "100");
	}

	@Test
	void prevalidatesAllRequirementsBeforeChangingInventory() {
		SakuraScenario scenario = sakuraScenario();
		ProductionOrder order = inProgressOrder(scenario.finishedProduct(), "1");
		stubOrderAndBom(order, scenario.compositions());
		when(inventoryLotRepository.sumAvailableQuantity(ESSENCE_ID, REFERENCE_DATE))
				.thenReturn(new BigDecimal("100"));
		when(inventoryLotRepository.sumAvailableQuantity(ALCOHOL_ID, REFERENCE_DATE))
				.thenReturn(new BigDecimal("20"));

		assertThatExceptionOfType(InsufficientInventoryException.class)
				.isThrownBy(() -> service.complete(
						ORDER_ID, completionRequest("PERF-SAKURA-001"), ACTOR));

		verify(inventoryLotRepository, never()).decreaseForConsumption(
				any(), any(), any(), any(), any(), any());
		verify(inventoryLotRepository, never()).saveAndFlush(any(InventoryLot.class));
		verifyNoInteractions(inventoryMovementRepository);
		verify(productionConsumptionRepository, never())
				.save(any(ProductionConsumption.class));
		verify(producedLotRepository, never()).saveAndFlush(any(ProducedLot.class));
		verify(productionOrderRepository, never()).saveAndFlush(any(ProductionOrder.class));
		assertThat(order.getStatus()).isEqualTo(ProductionOrderStatus.IN_PROGRESS);
	}

	@Test
	void rejectsCompletionInInvalidStateBeforeReadingInventory() {
		ProductionOrder createdOrder = ProductionOrder.create(
				finishedProduct(), BigDecimal.ONE, FIXED_INSTANT.minusSeconds(60));
		ReflectionTestUtils.setField(createdOrder, "id", ORDER_ID);
		when(productionOrderRepository.findById(ORDER_ID))
				.thenReturn(Optional.of(createdOrder));

		assertThatExceptionOfType(InvalidProductionOrderStateException.class)
				.isThrownBy(() -> service.complete(
						ORDER_ID, completionRequest("PERF-SAKURA-001"), ACTOR));

		verifyNoInteractions(
				productCompositionRepository,
				inventoryLotRepository,
				inventoryMovementRepository,
				productionConsumptionRepository,
				producedLotRepository);
	}

	@Test
	void completesOneUnitWithConsumptionTraceAndProducedLotEntry() {
		SakuraScenario scenario = sakuraScenario();
		ProductionOrder order = inProgressOrder(scenario.finishedProduct(), "1");
		stubOrderAndBom(order, scenario.compositions());
		stubSuccessfulLot(
				scenario.essence(), ESSENCE_LOT_ID, "ESS-001", "100", "50");
		stubSuccessfulLot(
				scenario.alcohol(), ALCOHOL_LOT_ID, "ALC-001", "100", "60");
		stubSuccessfulLot(
				scenario.water(), WATER_LOT_ID, "WAT-001", "100", "90");
		stubSuccessfulCompletionPersistence();

		var response = service.complete(
				ORDER_ID, completionRequest("PERF-SAKURA-001"), ACTOR);

		ArgumentCaptor<InventoryMovement> movementCaptor =
				ArgumentCaptor.forClass(InventoryMovement.class);
		verify(inventoryMovementRepository, times(4))
				.save(movementCaptor.capture());
		List<InventoryMovement> movements = movementCaptor.getAllValues();
		assertThat(movements).extracting(InventoryMovement::getType).containsExactly(
				InventoryMovementType.PRODUCTION_CONSUMPTION,
				InventoryMovementType.PRODUCTION_CONSUMPTION,
				InventoryMovementType.PRODUCTION_CONSUMPTION,
				InventoryMovementType.PRODUCTION_ENTRY);
		assertQuantities(
				movements.stream().map(InventoryMovement::getQuantity).toList(),
				"50", "40", "10", "1");
		assertQuantities(
				movements.stream().map(InventoryMovement::getResultingQuantity).toList(),
				"50", "60", "90", "1");
		assertThat(movements).allSatisfy(movement -> {
			assertThat(movement.getReferenceType()).isEqualTo("PRODUCTION_ORDER");
			assertThat(movement.getReferenceId()).isEqualTo(ORDER_ID);
			assertThat(movement.getCreatedBy()).isEqualTo(ACTOR);
		});

		ArgumentCaptor<ProductionConsumption> consumptionCaptor =
				ArgumentCaptor.forClass(ProductionConsumption.class);
		verify(productionConsumptionRepository, times(3))
				.save(consumptionCaptor.capture());
		assertThat(consumptionCaptor.getAllValues())
				.extracting(consumption -> consumption.getComponentProduct().getId())
				.containsExactly(ESSENCE_ID, ALCOHOL_ID, WATER_ID);
		assertQuantities(
				consumptionCaptor.getAllValues().stream()
						.map(ProductionConsumption::getConsumedQuantity)
						.toList(),
				"50", "40", "10");

		ArgumentCaptor<ProducedLot> producedLotCaptor =
				ArgumentCaptor.forClass(ProducedLot.class);
		verify(producedLotRepository).saveAndFlush(producedLotCaptor.capture());
		ProducedLot producedLot = producedLotCaptor.getValue();
		assertThat(producedLot.getProductionOrder()).isSameAs(order);
		assertThat(producedLot.getInventoryLot().getId())
				.isEqualTo(PRODUCED_INVENTORY_LOT_ID);
		assertThat(producedLot.getInventoryLot().getLotNumber())
				.isEqualTo("PERF-SAKURA-001");
		assertThat(producedLot.getInventoryLot().getStatus())
				.isEqualTo(InventoryLotStatus.AVAILABLE);
		assertThat(producedLot.getProducedQuantity()).isEqualByComparingTo("1");
		assertThat(response.order().status()).isEqualTo(ProductionOrderStatus.COMPLETED);
		assertThat(order.getCompletedAt()).isEqualTo(FIXED_INSTANT);
	}

	@Test
	void consumesMultipleLotsInRepositoryFefoOrder() {
		Product finishedProduct = finishedProduct();
		Product essence = rawMaterial(ESSENCE_ID, "Sakura Essence", "ESS-SAKURA");
		ProductionOrder order = inProgressOrder(finishedProduct, "10");
		ProductComposition composition = composition(
				finishedProduct, essence, "50", MeasurementUnit.MILLILITER);
		stubOrderAndBom(order, List.of(composition));
		InventoryLot firstCandidate = inventoryLot(
				essence,
				ESSENCE_LOT_ID,
				"ESS-001",
				LocalDate.of(2026, 8, 1),
				"300",
				"300");
		InventoryLot secondCandidate = inventoryLot(
				essence,
				ESSENCE_LOT_ID + 1,
				"ESS-002",
				LocalDate.of(2026, 9, 1),
				"400",
				"400");
		when(inventoryLotRepository.sumAvailableQuantity(ESSENCE_ID, REFERENCE_DATE))
				.thenReturn(new BigDecimal("700"));
		when(inventoryLotRepository.findAvailableLotsForFefo(
				ESSENCE_ID, REFERENCE_DATE, PageRequest.of(0, 100)))
				.thenReturn(List.of(firstCandidate, secondCandidate));
		when(inventoryLotRepository.findById(ESSENCE_LOT_ID))
				.thenReturn(Optional.of(inventoryLot(
						essence,
						ESSENCE_LOT_ID,
						"ESS-001",
						LocalDate.of(2026, 8, 1),
						"300",
						"0")));
		when(inventoryLotRepository.findById(ESSENCE_LOT_ID + 1))
				.thenReturn(Optional.of(inventoryLot(
						essence,
						ESSENCE_LOT_ID + 1,
						"ESS-002",
						LocalDate.of(2026, 9, 1),
						"400",
						"200")));
		stubSuccessfulCompletionPersistence();

		service.complete(ORDER_ID, completionRequest("PERF-SAKURA-010"), ACTOR);

		ArgumentCaptor<Long> lotIdCaptor = ArgumentCaptor.forClass(Long.class);
		ArgumentCaptor<BigDecimal> quantityCaptor = ArgumentCaptor.forClass(BigDecimal.class);
		verify(inventoryLotRepository, times(2)).decreaseForConsumption(
				lotIdCaptor.capture(),
				quantityCaptor.capture(),
				eq(REFERENCE_DATE),
				eq(FIXED_INSTANT),
				eq(InventoryLotStatus.AVAILABLE),
				eq(InventoryLotStatus.DEPLETED));
		assertThat(lotIdCaptor.getAllValues())
				.containsExactly(ESSENCE_LOT_ID, ESSENCE_LOT_ID + 1);
		assertQuantities(quantityCaptor.getAllValues(), "300", "200");

		ArgumentCaptor<ProductionConsumption> consumptionCaptor =
				ArgumentCaptor.forClass(ProductionConsumption.class);
		verify(productionConsumptionRepository, times(2))
				.save(consumptionCaptor.capture());
		assertQuantities(
				consumptionCaptor.getAllValues().stream()
						.map(ProductionConsumption::getConsumedQuantity)
						.toList(),
				"300", "200");
	}

	@Test
	void rejectsAtomicConsumptionConflictWithoutCreatingProducedLot() {
		Product finishedProduct = finishedProduct();
		Product essence = rawMaterial(ESSENCE_ID, "Sakura Essence", "ESS-SAKURA");
		ProductionOrder order = inProgressOrder(finishedProduct, "1");
		stubOrderAndBom(order, List.of(composition(
				finishedProduct, essence, "50", MeasurementUnit.MILLILITER)));
		InventoryLot candidate = inventoryLot(
				essence, ESSENCE_LOT_ID, "ESS-001", FUTURE_EXPIRATION, "100", "100");
		when(inventoryLotRepository.findAvailableLotsForFefo(
				ESSENCE_ID, REFERENCE_DATE, PageRequest.of(0, 100)))
				.thenReturn(List.of(candidate));
		when(inventoryLotRepository.decreaseForConsumption(
				ESSENCE_LOT_ID,
				new BigDecimal("50.000000"),
				REFERENCE_DATE,
				FIXED_INSTANT,
				InventoryLotStatus.AVAILABLE,
				InventoryLotStatus.DEPLETED))
				.thenReturn(0);
		when(inventoryLotRepository.sumAvailableQuantity(ESSENCE_ID, REFERENCE_DATE))
				.thenReturn(new BigDecimal("100"))
				.thenReturn(new BigDecimal("20"));

		assertThatExceptionOfType(InsufficientInventoryException.class)
				.isThrownBy(() -> service.complete(
						ORDER_ID, completionRequest("PERF-SAKURA-001"), ACTOR));

		verifyNoInteractions(inventoryMovementRepository);
		verify(productionConsumptionRepository, never())
				.save(any(ProductionConsumption.class));
		verify(inventoryLotRepository, never()).saveAndFlush(any(InventoryLot.class));
		verify(producedLotRepository, never()).saveAndFlush(any(ProducedLot.class));
		verify(productionOrderRepository, never()).saveAndFlush(any(ProductionOrder.class));
		assertThat(order.getStatus()).isEqualTo(ProductionOrderStatus.IN_PROGRESS);
	}

	private void stubOrderAndBom(
			ProductionOrder order,
			List<ProductComposition> compositions) {
		when(productionOrderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
		when(productCompositionRepository.findAllByParentProductId(FINISHED_PRODUCT_ID))
				.thenReturn(compositions);
	}

	private void stubSuccessfulLot(
			Product component,
			Long lotId,
			String lotNumber,
			String initialQuantity,
			String resultingQuantity) {
		InventoryLot candidate = inventoryLot(
				component,
				lotId,
				lotNumber,
				FUTURE_EXPIRATION,
				initialQuantity,
				initialQuantity);
		InventoryLot consumed = inventoryLot(
				component,
				lotId,
				lotNumber,
				FUTURE_EXPIRATION,
				initialQuantity,
				resultingQuantity);
		when(inventoryLotRepository.sumAvailableQuantity(component.getId(), REFERENCE_DATE))
				.thenReturn(new BigDecimal(initialQuantity));
		when(inventoryLotRepository.findAvailableLotsForFefo(
				component.getId(), REFERENCE_DATE, PageRequest.of(0, 100)))
				.thenReturn(List.of(candidate));
		when(inventoryLotRepository.findById(lotId)).thenReturn(Optional.of(consumed));
	}

	private void stubSuccessfulCompletionPersistence() {
		when(inventoryLotRepository.decreaseForConsumption(
				anyLong(),
				any(BigDecimal.class),
				eq(REFERENCE_DATE),
				eq(FIXED_INSTANT),
				eq(InventoryLotStatus.AVAILABLE),
				eq(InventoryLotStatus.DEPLETED)))
				.thenReturn(1);
		when(inventoryLotRepository.saveAndFlush(any(InventoryLot.class)))
				.thenAnswer(invocation -> withId(
						invocation.getArgument(0, InventoryLot.class),
						PRODUCED_INVENTORY_LOT_ID));
		when(productionConsumptionRepository.save(any(ProductionConsumption.class)))
				.thenAnswer(invocation -> invocation.getArgument(0, ProductionConsumption.class));
		when(producedLotRepository.saveAndFlush(any(ProducedLot.class)))
				.thenAnswer(invocation -> invocation.getArgument(0, ProducedLot.class));
		when(productionOrderRepository.saveAndFlush(any(ProductionOrder.class)))
				.thenAnswer(invocation -> invocation.getArgument(0, ProductionOrder.class));
		when(productionConsumptionRepository.findAllByProductionOrderIdOrderByIdAsc(ORDER_ID))
				.thenReturn(List.of());
		when(producedLotRepository.findByProductionOrderId(ORDER_ID))
				.thenReturn(Optional.empty());
	}

	private static SakuraScenario sakuraScenario() {
		Product finishedProduct = finishedProduct();
		Product essence = rawMaterial(ESSENCE_ID, "Sakura Essence", "ESS-SAKURA");
		Product alcohol = rawMaterial(ALCOHOL_ID, "Grain Alcohol", "ALC-GRAIN");
		Product water = rawMaterial(WATER_ID, "Demineralized Water", "WAT-DEMIN");
		return new SakuraScenario(
				finishedProduct,
				essence,
				alcohol,
				water,
				List.of(
						composition(
								finishedProduct,
								water,
								"10",
								MeasurementUnit.MILLILITER),
						composition(
								finishedProduct,
								essence,
								"50",
								MeasurementUnit.MILLILITER),
						composition(
								finishedProduct,
								alcohol,
								"40",
								MeasurementUnit.MILLILITER)));
	}

	private static ProductComposition composition(
			Product parent,
			Product component,
			String quantity,
			MeasurementUnit measurementUnit) {
		ProductComposition composition = mock(ProductComposition.class);
		when(composition.getParentProduct()).thenReturn(parent);
		when(composition.getComponentProduct()).thenReturn(component);
		when(composition.getQuantity()).thenReturn(new BigDecimal(quantity));
		when(composition.getMeasurementUnit()).thenReturn(measurementUnit);
		return composition;
	}

	private static ProductionOrder inProgressOrder(Product product, String quantity) {
		ProductionOrder order = ProductionOrder.create(
				product,
				new BigDecimal(quantity),
				FIXED_INSTANT.minusSeconds(120));
		ReflectionTestUtils.setField(order, "id", ORDER_ID);
		order.start(FIXED_INSTANT.minusSeconds(60));
		return order;
	}

	private static Product finishedProduct() {
		return product(
				FINISHED_PRODUCT_ID,
				"Sakura Perfume 100 ml",
				"PERF-SAKURA-100ML",
				ProductType.FINISHED_PRODUCT,
				MeasurementUnit.UNIT);
	}

	private static Product rawMaterial(Long id, String name, String sku) {
		return product(id, name, sku, ProductType.RAW_MATERIAL, MeasurementUnit.MILLILITER);
	}

	private static Product product(
			Long id,
			String name,
			String sku,
			ProductType type,
			MeasurementUnit unit) {
		Product product = Product.create(name, null, sku, type, unit);
		ReflectionTestUtils.setField(product, "id", id);
		return product;
	}

	private static InventoryLot inventoryLot(
			Product product,
			Long id,
			String lotNumber,
			LocalDate expirationDate,
			String initialQuantity,
			String availableQuantity) {
		InventoryLot lot = InventoryLot.create(
				product,
				lotNumber,
				LocalDate.of(2026, 7, 1),
				expirationDate,
				new BigDecimal(initialQuantity),
				new BigDecimal("1.0000"),
				REFERENCE_DATE);
		ReflectionTestUtils.setField(lot, "id", id);
		ReflectionTestUtils.setField(lot, "availableQuantity", new BigDecimal(availableQuantity));
		ReflectionTestUtils.setField(
				lot,
				"status",
				new BigDecimal(availableQuantity).signum() == 0
						? InventoryLotStatus.DEPLETED
						: InventoryLotStatus.AVAILABLE);
		return lot;
	}

	private static InventoryLot withId(InventoryLot lot, Long id) {
		ReflectionTestUtils.setField(lot, "id", id);
		return lot;
	}

	private static CompleteProductionRequest completionRequest(String lotNumber) {
		return new CompleteProductionRequest(
				lotNumber,
				LocalDate.of(2026, 7, 22),
				LocalDate.of(2027, 7, 22),
				new BigDecimal("25.0000"));
	}

	private static void assertQuantities(
			List<BigDecimal> actual,
			String... expected) {
		assertThat(actual).hasSize(expected.length);
		for (int index = 0; index < expected.length; index++) {
			assertThat(actual.get(index)).isEqualByComparingTo(expected[index]);
		}
	}

	private record SakuraScenario(
			Product finishedProduct,
			Product essence,
			Product alcohol,
			Product water,
			List<ProductComposition> compositions) {
	}
}
