package com.haru.product.production;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.haru.product.inventory.application.InventoryService;
import com.haru.product.inventory.application.dto.CreateInventoryLotRequest;
import com.haru.product.inventory.application.dto.InventoryLotResponse;
import com.haru.product.inventory.domain.InventoryLot;
import com.haru.product.inventory.domain.InventoryLotStatus;
import com.haru.product.inventory.domain.InventoryMovement;
import com.haru.product.inventory.domain.InventoryMovementType;
import com.haru.product.inventory.domain.exception.InsufficientInventoryException;
import com.haru.product.inventory.infrastructure.persistence.InventoryLotRepository;
import com.haru.product.inventory.infrastructure.persistence.InventoryMovementRepository;
import com.haru.product.product.application.ProductService;
import com.haru.product.product.application.dto.AddProductComponentRequest;
import com.haru.product.product.domain.MeasurementUnit;
import com.haru.product.product.domain.Product;
import com.haru.product.product.domain.ProductType;
import com.haru.product.product.infrastructure.persistence.ProductRepository;
import com.haru.product.production.application.ProductionService;
import com.haru.product.production.application.dto.CompleteProductionRequest;
import com.haru.product.production.application.dto.CreateProductionOrderRequest;
import com.haru.product.production.application.dto.ProductionConsumptionResponse;
import com.haru.product.production.application.dto.ProductionOrderResponse;
import com.haru.product.production.application.dto.ProductionResultResponse;
import com.haru.product.production.domain.ProductionOrder;
import com.haru.product.production.domain.ProductionOrderStatus;
import com.haru.product.production.domain.exception.InvalidProductionOrderStateException;
import com.haru.product.production.infrastructure.persistence.ProducedLotRepository;
import com.haru.product.production.infrastructure.persistence.ProductionConsumptionRepository;
import com.haru.product.production.infrastructure.persistence.ProductionOrderRepository;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Import(ProductionServiceIntegrationTests.FixedClockConfiguration.class)
class ProductionServiceIntegrationTests {

	private static final Instant FIXED_INSTANT = Instant.parse("2026-07-22T12:00:00Z");
	private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 7, 22);
	private static final String ACTOR = "production-test@example.com";
	private static final String PRODUCTION_ORDER_REFERENCE = "PRODUCTION_ORDER";

	@Container
	private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
			.withDatabaseName("haru_production_test")
			.withUsername("haru_test")
			.withPassword("haru_test_password");

	@Autowired
	private ProductionService productionService;

	@Autowired
	private ProductService productService;

	@Autowired
	private InventoryService inventoryService;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private InventoryLotRepository inventoryLotRepository;

	@Autowired
	private InventoryMovementRepository inventoryMovementRepository;

	@Autowired
	private ProductionOrderRepository productionOrderRepository;

	@Autowired
	private ProductionConsumptionRepository productionConsumptionRepository;

	@Autowired
	private ProducedLotRepository producedLotRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@DynamicPropertySource
	static void registerDatabaseProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
	}

	@BeforeEach
	void cleanDatabase() {
		jdbcTemplate.execute("""
				TRUNCATE TABLE production_consumptions, produced_lots, production_orders,
				inventory_movements, inventory_lots, product_compositions, products
				RESTART IDENTITY CASCADE
				""");
	}

	@Test
	void producesOneUnitWithCompleteInventoryAndLotTraceability() {
		Catalog catalog = createSakuraCatalog();
		StandardStock stock = createStandardStock(catalog, "100", "100", "100");
		ProductionOrderResponse order = createAndStartOrder(catalog.finishedProduct(), "1");

		ProductionResultResponse result = productionService.complete(
				order.id(), completionRequest("PERF-SAKURA-001"), ACTOR);

		assertThat(result.order().status()).isEqualTo(ProductionOrderStatus.COMPLETED);
		assertThat(result.order().completedAt()).isEqualTo(FIXED_INSTANT);
		assertThat(result.consumptions())
				.extracting(
						ProductionConsumptionResponse::componentProductSku,
						ProductionConsumptionResponse::consumedLotNumber,
						ProductionConsumptionResponse::consumedQuantity)
				.containsExactlyInAnyOrder(
						tuple("ESS-SAKURA", "ESS-001", new BigDecimal("50.000000")),
						tuple("ALCOHOL-CEREALS", "ALC-001", new BigDecimal("40.000000")),
						tuple("DEMINERALIZED-WATER", "WATER-001", new BigDecimal("10.000000")));
		assertLot(stock.essenceLot().id(), "50", InventoryLotStatus.AVAILABLE);
		assertLot(stock.alcoholLot().id(), "60", InventoryLotStatus.AVAILABLE);
		assertLot(stock.waterLot().id(), "90", InventoryLotStatus.AVAILABLE);
		assertThat(productionConsumptionRepository
				.findAllByProductionOrderIdOrderByIdAsc(order.id())).hasSize(3);
		assertThat(productionMovements(order.id(), InventoryMovementType.PRODUCTION_CONSUMPTION))
				.hasSize(3);

		assertThat(result.producedLot()).isNotNull();
		assertThat(result.producedLot().lotNumber()).isEqualTo("PERF-SAKURA-001");
		assertThat(result.producedLot().producedQuantity()).isEqualByComparingTo("1");
		assertThat(result.producedLot().status()).isEqualTo(InventoryLotStatus.AVAILABLE);
		assertThat(producedLotRepository.findByProductionOrderId(order.id())).isPresent();
		assertLot(result.producedLot().inventoryLotId(), "1", InventoryLotStatus.AVAILABLE);
		assertThat(productionMovements(order.id(), InventoryMovementType.PRODUCTION_ENTRY))
				.singleElement()
				.satisfies(movement -> {
					assertThat(movement.getInventoryLot().getId())
							.isEqualTo(result.producedLot().inventoryLotId());
					assertThat(movement.getQuantity()).isEqualByComparingTo("1");
					assertThat(movement.getResultingQuantity()).isEqualByComparingTo("1");
					assertThat(movement.getCreatedBy()).isEqualTo(ACTOR);
				});
	}

	@Test
	void searchesAndFiltersOrdersAtAnArbitraryDatabaseOffset() {
		Catalog catalog = createSakuraCatalog();
		ProductionOrderResponse first = productionService.create(
				new CreateProductionOrderRequest(catalog.finishedProduct().getId(), BigDecimal.ONE));
		ProductionOrderResponse second = productionService.create(
				new CreateProductionOrderRequest(catalog.finishedProduct().getId(), BigDecimal.TEN));
		ProductionOrderResponse third = productionService.create(
				new CreateProductionOrderRequest(catalog.finishedProduct().getId(), new BigDecimal("20")));

		var response = productionService.search(
				"sakura",
				ProductionOrderStatus.CREATED,
				1,
				2);

		assertThat(response.content()).extracting(ProductionOrderResponse::id)
				.containsExactly(second.id(), first.id());
		assertThat(response.content()).extracting(ProductionOrderResponse::status)
				.containsOnly(ProductionOrderStatus.CREATED);
		assertThat(response.offset()).isEqualTo(1);
		assertThat(response.limit()).isEqualTo(2);
		assertThat(response.totalElements()).isEqualTo(3);
		assertThat(response.hasPrevious()).isTrue();
		assertThat(response.hasNext()).isFalse();

		var exactId = productionService.search(String.valueOf(third.id()), null, 0, 20);
		assertThat(exactId.content()).extracting(ProductionOrderResponse::id)
				.containsExactly(third.id());
	}

	@Test
	void multipliesTheUnitBomExactlyForTenProducedUnits() {
		Catalog catalog = createSakuraCatalog();
		StandardStock stock = createStandardStock(catalog, "600", "500", "200");
		ProductionOrderResponse order = createAndStartOrder(catalog.finishedProduct(), "10");

		ProductionResultResponse result = productionService.complete(
				order.id(), completionRequest("PERF-SAKURA-010"), ACTOR);

		assertThat(result.consumptions())
				.extracting(
						ProductionConsumptionResponse::componentProductSku,
						ProductionConsumptionResponse::consumedQuantity)
				.containsExactlyInAnyOrder(
						tuple("ESS-SAKURA", new BigDecimal("500.000000")),
						tuple("ALCOHOL-CEREALS", new BigDecimal("400.000000")),
						tuple("DEMINERALIZED-WATER", new BigDecimal("100.000000")));
		assertLot(stock.essenceLot().id(), "100", InventoryLotStatus.AVAILABLE);
		assertLot(stock.alcoholLot().id(), "100", InventoryLotStatus.AVAILABLE);
		assertLot(stock.waterLot().id(), "100", InventoryLotStatus.AVAILABLE);
		assertThat(result.producedLot().producedQuantity()).isEqualByComparingTo("10");
		assertLot(result.producedLot().inventoryLotId(), "10", InventoryLotStatus.AVAILABLE);
	}

	@Test
	void consumesMultipleEssenceLotsInFefoOrder() {
		Catalog catalog = createSakuraCatalog();
		InventoryLotResponse firstEssenceLot = createLot(
				catalog.essence(), "ESS-001", "300", LocalDate.of(2026, 8, 10));
		InventoryLotResponse secondEssenceLot = createLot(
				catalog.essence(), "ESS-002", "400", LocalDate.of(2026, 8, 20));
		createLot(catalog.alcohol(), "ALC-001", "500", LocalDate.of(2026, 8, 30));
		createLot(catalog.water(), "WATER-001", "200", LocalDate.of(2026, 9, 10));
		ProductionOrderResponse order = createAndStartOrder(catalog.finishedProduct(), "10");

		ProductionResultResponse result = productionService.complete(
				order.id(), completionRequest("PERF-SAKURA-FEFO"), ACTOR);

		assertThat(result.consumptions().stream()
				.filter(consumption -> consumption.componentProductSku().equals("ESS-SAKURA"))
				.toList())
				.extracting(
						ProductionConsumptionResponse::consumedLotNumber,
						ProductionConsumptionResponse::consumedQuantity)
				.containsExactly(
						tuple("ESS-001", new BigDecimal("300.000000")),
						tuple("ESS-002", new BigDecimal("200.000000")));
		assertLot(firstEssenceLot.id(), "0", InventoryLotStatus.DEPLETED);
		assertLot(secondEssenceLot.id(), "200", InventoryLotStatus.AVAILABLE);
		assertThat(productionMovementsForLot(
				firstEssenceLot.id(), InventoryMovementType.PRODUCTION_CONSUMPTION)).hasSize(1);
		assertThat(productionMovementsForLot(
				secondEssenceLot.id(), InventoryMovementType.PRODUCTION_CONSUMPTION)).hasSize(1);
		assertThat(productionConsumptionRepository
				.findAllByProductionOrderIdOrderByIdAsc(order.id())).hasSize(4);
	}

	@Test
	void rollsBackEveryProductionWriteWhenAComponentIsInsufficient() {
		Catalog catalog = createSakuraCatalog();
		StandardStock stock = createStandardStock(catalog, "40", "100", "100");
		ProductionOrderResponse order = createAndStartOrder(catalog.finishedProduct(), "1");
		long movementCountBeforeCompletion = inventoryMovementRepository.count();

		assertThatExceptionOfType(InsufficientInventoryException.class)
				.isThrownBy(() -> productionService.complete(
						order.id(), completionRequest("PERF-SAKURA-INSUFFICIENT"), ACTOR));

		assertLot(stock.essenceLot().id(), "40", InventoryLotStatus.AVAILABLE);
		assertLot(stock.alcoholLot().id(), "100", InventoryLotStatus.AVAILABLE);
		assertLot(stock.waterLot().id(), "100", InventoryLotStatus.AVAILABLE);
		assertThat(inventoryMovementRepository.count()).isEqualTo(movementCountBeforeCompletion);
		assertThat(productionMovements(order.id(), InventoryMovementType.PRODUCTION_CONSUMPTION))
				.isEmpty();
		assertThat(productionMovements(order.id(), InventoryMovementType.PRODUCTION_ENTRY)).isEmpty();
		assertThat(productionConsumptionRepository
				.findAllByProductionOrderIdOrderByIdAsc(order.id())).isEmpty();
		assertThat(producedLotRepository.findByProductionOrderId(order.id())).isEmpty();
		assertThat(inventoryLotRepository
				.findAllByProductIdOrderByIdAsc(catalog.finishedProduct().getId())).isEmpty();
		assertThat(requireOrder(order.id()).getStatus()).isEqualTo(ProductionOrderStatus.IN_PROGRESS);
	}

	@Test
	void ignoresExpiredComponentLotAndConsumesOnlyAValidLot() {
		Catalog catalog = createSakuraCatalog();
		InventoryLotResponse expired = createLot(
				catalog.essence(), "ESS-EXPIRED", "100", REFERENCE_DATE.minusDays(1));
		InventoryLotResponse valid = createLot(
				catalog.essence(), "ESS-VALID", "100", REFERENCE_DATE.plusDays(10));
		createLot(catalog.alcohol(), "ALC-001", "100", REFERENCE_DATE.plusDays(20));
		createLot(catalog.water(), "WATER-001", "100", REFERENCE_DATE.plusDays(30));
		ProductionOrderResponse order = createAndStartOrder(catalog.finishedProduct(), "1");

		ProductionResultResponse result = productionService.complete(
				order.id(), completionRequest("PERF-SAKURA-VALID-STOCK"), ACTOR);

		assertThat(result.consumptions().stream()
				.filter(consumption -> consumption.componentProductSku().equals("ESS-SAKURA"))
				.toList())
				.singleElement()
				.satisfies(consumption -> {
					assertThat(consumption.consumedLotId()).isEqualTo(valid.id());
					assertThat(consumption.consumedLotNumber()).isEqualTo("ESS-VALID");
					assertThat(consumption.consumedQuantity()).isEqualByComparingTo("50");
				});
		assertLot(expired.id(), "100", InventoryLotStatus.EXPIRED);
		assertLot(valid.id(), "50", InventoryLotStatus.AVAILABLE);
		assertThat(productionMovementsForLot(
				expired.id(), InventoryMovementType.PRODUCTION_CONSUMPTION)).isEmpty();
		assertThat(productionMovementsForLot(
				valid.id(), InventoryMovementType.PRODUCTION_CONSUMPTION)).hasSize(1);
	}

	@Test
	void rejectsInvalidProductionOrderStateTransitions() {
		Catalog catalog = createSakuraCatalog();
		createStandardStock(catalog, "200", "200", "200");

		ProductionOrderResponse created = productionService.create(
				new CreateProductionOrderRequest(catalog.finishedProduct().getId(), BigDecimal.ONE));
		assertThatExceptionOfType(InvalidProductionOrderStateException.class)
				.isThrownBy(() -> productionService.complete(
						created.id(), completionRequest("CREATED-CANNOT-COMPLETE"), ACTOR));

		productionService.start(created.id());
		assertThatExceptionOfType(InvalidProductionOrderStateException.class)
				.isThrownBy(() -> productionService.start(created.id()));

		ProductionOrderResponse cancelled = productionService.create(
				new CreateProductionOrderRequest(catalog.finishedProduct().getId(), BigDecimal.ONE));
		productionService.cancel(cancelled.id());
		assertThatExceptionOfType(InvalidProductionOrderStateException.class)
				.isThrownBy(() -> productionService.complete(
						cancelled.id(), completionRequest("CANCELLED-CANNOT-COMPLETE"), ACTOR));

		ProductionResultResponse completed = productionService.complete(
				created.id(), completionRequest("PERF-SAKURA-COMPLETED"), ACTOR);
		assertThat(completed.order().status()).isEqualTo(ProductionOrderStatus.COMPLETED);
		assertThatExceptionOfType(InvalidProductionOrderStateException.class)
				.isThrownBy(() -> productionService.cancel(created.id()));
		assertThatExceptionOfType(InvalidProductionOrderStateException.class)
				.isThrownBy(() -> productionService.complete(
						created.id(), completionRequest("PERF-SAKURA-SECOND-COMPLETION"), ACTOR));
		assertThat(producedLotRepository.findByProductionOrderId(created.id())).isPresent();
		assertThat(producedLotRepository.findByProductionOrderId(cancelled.id())).isEmpty();
	}

	@Test
	void allowsOnlyOneConcurrentOrderToConsumeTheLastAvailableBom() throws Exception {
		Catalog catalog = createSakuraCatalog();
		StandardStock stock = createStandardStock(catalog, "50", "40", "10");
		ProductionOrderResponse firstOrder = createAndStartOrder(catalog.finishedProduct(), "1");
		ProductionOrderResponse secondOrder = createAndStartOrder(catalog.finishedProduct(), "1");
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		List<CompletionAttempt> attempts;
		try {
			Future<CompletionAttempt> first = executor.submit(() -> completeConcurrently(
					firstOrder.id(), "PERF-SAKURA-CONCURRENT-A", ready, start));
			Future<CompletionAttempt> second = executor.submit(() -> completeConcurrently(
					secondOrder.id(), "PERF-SAKURA-CONCURRENT-B", ready, start));
			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			attempts = List.of(
					first.get(30, TimeUnit.SECONDS),
					second.get(30, TimeUnit.SECONDS));
		} finally {
			start.countDown();
			executor.shutdownNow();
			assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
		}

		assertThat(attempts).filteredOn(CompletionAttempt::succeeded).hasSize(1);
		assertThat(attempts)
				.filteredOn(attempt -> !attempt.succeeded())
				.singleElement()
				.satisfies(attempt -> assertThat(attempt.failure())
						.isInstanceOf(InsufficientInventoryException.class));
		CompletionAttempt winner = attempts.stream()
				.filter(CompletionAttempt::succeeded)
				.findFirst()
				.orElseThrow();
		CompletionAttempt rejected = attempts.stream()
				.filter(attempt -> !attempt.succeeded())
				.findFirst()
				.orElseThrow();

		assertThat(requireOrder(winner.orderId()).getStatus())
				.isEqualTo(ProductionOrderStatus.COMPLETED);
		assertThat(requireOrder(rejected.orderId()).getStatus())
				.isEqualTo(ProductionOrderStatus.IN_PROGRESS);
		assertLot(stock.essenceLot().id(), "0", InventoryLotStatus.DEPLETED);
		assertLot(stock.alcoholLot().id(), "0", InventoryLotStatus.DEPLETED);
		assertLot(stock.waterLot().id(), "0", InventoryLotStatus.DEPLETED);
		assertThat(productionConsumptionRepository
				.findAllByProductionOrderIdOrderByIdAsc(winner.orderId())).hasSize(3);
		assertThat(productionConsumptionRepository
				.findAllByProductionOrderIdOrderByIdAsc(rejected.orderId())).isEmpty();
		assertThat(productionMovements(
				winner.orderId(), InventoryMovementType.PRODUCTION_CONSUMPTION)).hasSize(3);
		assertThat(productionMovements(
				rejected.orderId(), InventoryMovementType.PRODUCTION_CONSUMPTION)).isEmpty();
		assertThat(productionMovements(
				winner.orderId(), InventoryMovementType.PRODUCTION_ENTRY)).hasSize(1);
		assertThat(productionMovements(
				rejected.orderId(), InventoryMovementType.PRODUCTION_ENTRY)).isEmpty();
		assertThat(producedLotRepository.findByProductionOrderId(winner.orderId())).isPresent();
		assertThat(producedLotRepository.findByProductionOrderId(rejected.orderId())).isEmpty();
		assertThat(producedLotRepository.count()).isOne();
		assertThat(inventoryLotRepository
				.findAllByProductIdOrderByIdAsc(catalog.finishedProduct().getId())).hasSize(1);
	}

	private CompletionAttempt completeConcurrently(
			Long orderId,
			String producedLotNumber,
			CountDownLatch ready,
			CountDownLatch start) throws InterruptedException {
		ready.countDown();
		if (!start.await(10, TimeUnit.SECONDS)) {
			return CompletionAttempt.failed(
					orderId, new IllegalStateException("Concurrent production start timed out"));
		}
		try {
			ProductionResultResponse result = productionService.complete(
					orderId, completionRequest(producedLotNumber), ACTOR);
			return CompletionAttempt.successful(orderId, result);
		} catch (RuntimeException exception) {
			return CompletionAttempt.failed(orderId, exception);
		}
	}

	private Catalog createSakuraCatalog() {
		Product finishedProduct = persistProduct(
				"Sakura Perfume 100 ml", "PERF-SAKURA-100ML",
				ProductType.FINISHED_PRODUCT, MeasurementUnit.UNIT);
		Product essence = persistProduct(
				"Sakura Essence", "ESS-SAKURA",
				ProductType.RAW_MATERIAL, MeasurementUnit.MILLILITER);
		Product alcohol = persistProduct(
				"Cereal Alcohol", "ALCOHOL-CEREALS",
				ProductType.RAW_MATERIAL, MeasurementUnit.MILLILITER);
		Product water = persistProduct(
				"Demineralized Water", "DEMINERALIZED-WATER",
				ProductType.RAW_MATERIAL, MeasurementUnit.MILLILITER);
		addComponent(finishedProduct, essence, "50");
		addComponent(finishedProduct, alcohol, "40");
		addComponent(finishedProduct, water, "10");
		return new Catalog(finishedProduct, essence, alcohol, water);
	}

	private Product persistProduct(
			String name,
			String sku,
			ProductType type,
			MeasurementUnit measurementUnit) {
		return productRepository.saveAndFlush(
				Product.create(name, null, sku, type, measurementUnit));
	}

	private void addComponent(Product parent, Product component, String quantity) {
		productService.addComponent(
				parent.getId(),
				new AddProductComponentRequest(
						component.getId(),
						new BigDecimal(quantity),
						MeasurementUnit.MILLILITER));
	}

	private StandardStock createStandardStock(
			Catalog catalog,
			String essenceQuantity,
			String alcoholQuantity,
			String waterQuantity) {
		return new StandardStock(
				createLot(
						catalog.essence(), "ESS-001", essenceQuantity,
						REFERENCE_DATE.plusDays(10)),
				createLot(
						catalog.alcohol(), "ALC-001", alcoholQuantity,
						REFERENCE_DATE.plusDays(20)),
				createLot(
						catalog.water(), "WATER-001", waterQuantity,
						REFERENCE_DATE.plusDays(30)));
	}

	private InventoryLotResponse createLot(
			Product product,
			String lotNumber,
			String quantity,
			LocalDate expirationDate) {
		return inventoryService.createLot(
				new CreateInventoryLotRequest(
						product.getId(),
						lotNumber,
						LocalDate.of(2026, 7, 1),
						expirationDate,
						new BigDecimal(quantity),
						new BigDecimal("0.5000")),
				ACTOR);
	}

	private ProductionOrderResponse createAndStartOrder(Product product, String quantity) {
		ProductionOrderResponse created = productionService.create(
				new CreateProductionOrderRequest(product.getId(), new BigDecimal(quantity)));
		return productionService.start(created.id());
	}

	private static CompleteProductionRequest completionRequest(String producedLotNumber) {
		return new CompleteProductionRequest(
				producedLotNumber,
				REFERENCE_DATE,
				REFERENCE_DATE.plusYears(1),
				new BigDecimal("12.5000"));
	}

	private ProductionOrder requireOrder(Long id) {
		return productionOrderRepository.findById(id).orElseThrow();
	}

	private void assertLot(Long lotId, String expectedQuantity, InventoryLotStatus expectedStatus) {
		InventoryLot lot = inventoryLotRepository.findById(lotId).orElseThrow();
		assertThat(lot.getAvailableQuantity()).isEqualByComparingTo(expectedQuantity);
		assertThat(lot.getAvailableQuantity()).isNotNegative();
		assertThat(lot.getStatus()).isEqualTo(expectedStatus);
	}

	private List<InventoryMovement> productionMovements(
			Long orderId,
			InventoryMovementType movementType) {
		return inventoryMovementRepository
				.findAllByReferenceTypeAndReferenceIdOrderByOccurredAtAscIdAsc(
						PRODUCTION_ORDER_REFERENCE, orderId)
				.stream()
				.filter(movement -> movement.getType() == movementType)
				.toList();
	}

	private List<InventoryMovement> productionMovementsForLot(
			Long lotId,
			InventoryMovementType movementType) {
		return inventoryMovementRepository
				.findAllByInventoryLotIdOrderByOccurredAtAscIdAsc(lotId)
				.stream()
				.filter(movement -> movement.getType() == movementType)
				.toList();
	}

	private record Catalog(
			Product finishedProduct,
			Product essence,
			Product alcohol,
			Product water) {
	}

	private record StandardStock(
			InventoryLotResponse essenceLot,
			InventoryLotResponse alcoholLot,
			InventoryLotResponse waterLot) {
	}

	private record CompletionAttempt(
			Long orderId,
			boolean succeeded,
			ProductionResultResponse result,
			RuntimeException failure) {

		private static CompletionAttempt successful(
				Long orderId,
				ProductionResultResponse result) {
			return new CompletionAttempt(orderId, true, result, null);
		}

		private static CompletionAttempt failed(Long orderId, RuntimeException failure) {
			return new CompletionAttempt(orderId, false, null, failure);
		}
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FixedClockConfiguration {

		@Bean
		@Primary
		Clock fixedClock() {
			return Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
		}
	}
}
