package com.haru.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockReset;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.haru.product.inventory.application.InventoryService;
import com.haru.product.inventory.application.dto.CreateInventoryLotRequest;
import com.haru.product.inventory.application.dto.InventoryLotResponse;
import com.haru.product.inventory.infrastructure.persistence.InventoryLotRepository;
import com.haru.product.product.application.ProductService;
import com.haru.product.product.application.dto.CreateProductRequest;
import com.haru.product.product.application.dto.ProductResponse;
import com.haru.product.product.application.dto.UpdateProductRequest;
import com.haru.product.product.domain.MeasurementUnit;
import com.haru.product.product.domain.Product;
import com.haru.product.product.domain.ProductType;
import com.haru.product.product.infrastructure.persistence.ProductRepository;
import com.haru.product.production.application.ProductionService;
import com.haru.product.production.domain.ProductionOrder;
import com.haru.product.production.infrastructure.persistence.ProductionOrderRepository;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Import(PersistenceHardeningIntegrationTests.FixedClockConfiguration.class)
class PersistenceHardeningIntegrationTests {

	private static final Instant FIXED_INSTANT = Instant.parse("2026-07-22T12:00:00Z");
	private static final OffsetDateTime FIXED_JDBC_TIMESTAMP = FIXED_INSTANT.atOffset(ZoneOffset.UTC);
	private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 7, 22);
	private static final String ACTOR = "persistence-hardening-test@example.com";

	@Container
	private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
			.withDatabaseName("haru_persistence_hardening_test")
			.withUsername("haru_test")
			.withPassword("haru_test_password");

	@Autowired
	private ProductService productService;

	@Autowired
	private InventoryService inventoryService;

	@Autowired
	private ProductionService productionService;

	@MockitoSpyBean(reset = MockReset.AFTER)
	private ProductRepository productRepository;

	@MockitoSpyBean(reset = MockReset.AFTER)
	private InventoryLotRepository inventoryLotRepository;

	@MockitoSpyBean(reset = MockReset.AFTER)
	private ProductionOrderRepository productionOrderRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EntityManager entityManager;

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
		jdbcTemplate.execute("ALTER SEQUENCE product_sku_sequence RESTART WITH 1");
	}

	@Test
	void rejectsUnknownPersistedEnumValues() {
		Product parent = persistProduct("Parent", "PARENT");
		Product component = persistProduct("Component", "COMPONENT");
		InventoryLotResponse lot = createLot(component, "COMPONENT-LOT");
		Long orderId = insertValidCreatedOrder(parent.getId());

		assertCheckViolation(() -> jdbcTemplate.update("""
				INSERT INTO products (
				    name, sku, product_type, default_measurement_unit,
				    active, version, created_at, updated_at
				) VALUES (?, ?, ?, ?, TRUE, 0, ?, ?)
				""", "Invalid type", "INVALID-TYPE", "UNKNOWN", "UNIT",
				FIXED_JDBC_TIMESTAMP, FIXED_JDBC_TIMESTAMP));

		assertCheckViolation(() -> jdbcTemplate.update("""
				INSERT INTO products (
				    name, sku, product_type, default_measurement_unit,
				    active, version, created_at, updated_at
				) VALUES (?, ?, ?, ?, TRUE, 0, ?, ?)
				""", "Invalid unit", "INVALID-UNIT", "RAW_MATERIAL", "UNKNOWN",
				FIXED_JDBC_TIMESTAMP, FIXED_JDBC_TIMESTAMP));

		assertCheckViolation(() -> jdbcTemplate.update("""
				INSERT INTO product_compositions (
				    parent_product_id, component_product_id, quantity,
				    measurement_unit, created_at, updated_at
				) VALUES (?, ?, 1, ?, ?, ?)
				""", parent.getId(), component.getId(), "UNKNOWN",
				FIXED_JDBC_TIMESTAMP, FIXED_JDBC_TIMESTAMP));

		assertCheckViolation(() -> jdbcTemplate.update("""
				INSERT INTO inventory_lots (
				    product_id, lot_number, initial_quantity, available_quantity,
				    unit_cost, status, version, created_at, updated_at
				) VALUES (?, ?, 1, 1, 0, ?, 0, ?, ?)
				""", component.getId(), "INVALID-STATUS-LOT", "UNKNOWN",
				FIXED_JDBC_TIMESTAMP, FIXED_JDBC_TIMESTAMP));

		assertCheckViolation(() -> jdbcTemplate.update("""
				INSERT INTO inventory_movements (
				    inventory_lot_id, movement_type, quantity,
				    resulting_quantity, occurred_at
				) VALUES (?, ?, 1, 0, ?)
				""", lot.id(), "UNKNOWN", FIXED_JDBC_TIMESTAMP));

		assertCheckViolation(() -> jdbcTemplate.update("""
				INSERT INTO production_orders (
				    product_id, quantity_to_produce, status, version, created_at
				) VALUES (?, 1, ?, 0, ?)
				""", parent.getId(), "UNKNOWN", FIXED_JDBC_TIMESTAMP));

		assertCheckViolation(() -> jdbcTemplate.update("""
				INSERT INTO production_consumptions (
				    production_order_id, component_product_id, consumed_lot_id,
				    consumed_quantity, measurement_unit, created_at
				) VALUES (?, ?, ?, 1, ?, ?)
				""", orderId, component.getId(), lot.id(), "UNKNOWN", FIXED_JDBC_TIMESTAMP));
	}

	@Test
	void rejectsProductionOrderTimestampsThatConflictWithLifecycleStatus() {
		Product product = persistProduct("Finished product", "FINISHED");

		assertInvalidLifecycle(product.getId(), "CREATED", FIXED_INSTANT, null);
		assertInvalidLifecycle(product.getId(), "IN_PROGRESS", null, null);
		assertInvalidLifecycle(product.getId(), "COMPLETED", FIXED_INSTANT, null);
		assertInvalidLifecycle(product.getId(), "CANCELLED", null, FIXED_INSTANT);
	}

	@Test
	void rejectsInventoryMovementUpdatesAndDeletesAtTheDatabaseBoundary() {
		Product product = persistProduct("Essence", "ESSENCE");
		InventoryLotResponse lot = createLot(product, "ESSENCE-LOT");
		Long movementId = jdbcTemplate.queryForObject(
				"SELECT id FROM inventory_movements WHERE inventory_lot_id = ?",
				Long.class,
				lot.id());

		assertThatThrownBy(() -> jdbcTemplate.update(
				"UPDATE inventory_movements SET description = ? WHERE id = ?",
				"Tampered", movementId))
				.isInstanceOf(DataAccessException.class);
		assertThatThrownBy(() -> jdbcTemplate.update(
				"DELETE FROM inventory_movements WHERE id = ?", movementId))
				.isInstanceOf(DataAccessException.class);

		assertThat(jdbcTemplate.queryForObject(
				"SELECT count(*) FROM inventory_movements WHERE id = ?",
				Long.class,
				movementId)).isOne();
	}

	@Test
	void databaseGeneratesDistinctSkusForConcurrentProductCreations() throws Exception {
		List<Attempt> attempts = runConcurrently(
				() -> productService.create(createProductRequest("Concurrent product A")),
				() -> productService.create(createProductRequest("Concurrent product B")));

		assertThat(attempts).allMatch(Attempt::succeeded);
		assertThat(attempts)
				.extracting(attempt -> ((ProductResponse) attempt.result()).sku())
				.containsExactlyInAnyOrder("PRD-0000000001", "PRD-0000000002");
		assertThat(jdbcTemplate.queryForList(
				"SELECT sku FROM products ORDER BY sku",
				String.class))
				.containsExactly("PRD-0000000001", "PRD-0000000002");
	}

	@Test
	void databaseRejectsChangingAnExistingSkuDirectly() {
		Product product = persistProduct("Immutable SKU product", "PRD-0000000042");

		assertCheckViolation(() -> jdbcTemplate.update(
				"UPDATE products SET sku = ? WHERE id = ?",
				"PRD-0000000043",
				product.getId()));
		assertThat(jdbcTemplate.queryForObject(
				"SELECT sku FROM products WHERE id = ?",
				String.class,
				product.getId())).isEqualTo("PRD-0000000042");
	}

	@Test
	void databaseAllowsOnlyOneOfTwoConcurrentLotCreations() throws Exception {
		Product product = persistProduct("Concurrent lot product", "LOT-PRODUCT");
		CreateInventoryLotRequest request = lotRequest(product.getId(), "CONCURRENT-LOT");
		CyclicBarrier afterPrecheck = new CyclicBarrier(2);
		AtomicBoolean coordinate = new AtomicBoolean(true);
		doAnswer(invocation -> {
			boolean result = Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
					SELECT EXISTS (
					    SELECT 1
					      FROM inventory_lots
					     WHERE product_id = ?
					       AND lot_number = ?
					)
					""", Boolean.class,
					invocation.getArgument(0, Long.class),
					invocation.getArgument(1, String.class)));
			if (coordinate.get()) {
				await(afterPrecheck);
			}
			return result;
		}).when(inventoryLotRepository)
				.existsByProductIdAndLotNumber(eq(product.getId()), eq("CONCURRENT-LOT"));

		List<Attempt> attempts = runConcurrently(
				() -> inventoryService.createLot(request, "lot-creator-a"),
				() -> inventoryService.createLot(request, "lot-creator-b"));
		coordinate.set(false);

		assertOneConstraintWinner(attempts);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT count(*) FROM inventory_lots "
						+ "WHERE product_id = ? AND lot_number = ?",
				Long.class,
				product.getId(),
				"CONCURRENT-LOT")).isOne();
		assertThat(jdbcTemplate.queryForObject(
				"SELECT count(*) FROM inventory_movements",
				Long.class)).isOne();
	}

	@Test
	void optimisticVersionRejectsOneOfTwoConcurrentProductUpdates() throws Exception {
		Product product = persistProduct("Original product", "VERSIONED-PRODUCT");
		CyclicBarrier afterLoad = new CyclicBarrier(2);
		AtomicBoolean coordinate = new AtomicBoolean(true);
		doAnswer(invocation -> {
			Product loadedProduct = entityManager.find(
					Product.class,
					invocation.getArgument(0, Long.class));
			if (coordinate.get()) {
				await(afterLoad);
			}
			return Optional.ofNullable(loadedProduct);
		}).when(productRepository).findById(product.getId());

		List<Attempt> attempts = runConcurrently(
				() -> productService.update(product.getId(), updateProductRequest("Update A")),
				() -> productService.update(product.getId(), updateProductRequest("Update B")));
		coordinate.set(false);

		assertOneOptimisticLockWinner(attempts);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT name FROM products WHERE id = ?",
				String.class,
				product.getId())).isIn("Update A", "Update B");
		assertThat(jdbcTemplate.queryForObject(
				"SELECT version FROM products WHERE id = ?",
				Long.class,
				product.getId())).isOne();
	}

	@Test
	void optimisticVersionRejectsOneOfTwoConcurrentProductionOrderStarts() throws Exception {
		Product product = persistProduct("Production product", "PRODUCTION-PRODUCT");
		ProductionOrder order = productionOrderRepository.saveAndFlush(
				ProductionOrder.create(product, BigDecimal.ONE, FIXED_INSTANT));
		CyclicBarrier afterLoad = new CyclicBarrier(2);
		AtomicBoolean coordinate = new AtomicBoolean(true);
		doAnswer(invocation -> {
			ProductionOrder loadedOrder = entityManager.find(
					ProductionOrder.class,
					invocation.getArgument(0, Long.class));
			if (coordinate.get()) {
				await(afterLoad);
			}
			return Optional.ofNullable(loadedOrder);
		}).when(productionOrderRepository).findById(order.getId());

		List<Attempt> attempts = runConcurrently(
				() -> productionService.start(order.getId()),
				() -> productionService.start(order.getId()));
		coordinate.set(false);

		assertOneOptimisticLockWinner(attempts);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT status FROM production_orders WHERE id = ?",
				String.class,
				order.getId())).isEqualTo("IN_PROGRESS");
		assertThat(jdbcTemplate.queryForObject(
				"SELECT version FROM production_orders WHERE id = ?",
				Long.class,
				order.getId())).isOne();
	}

	private Product persistProduct(String name, String sku) {
		return productRepository.saveAndFlush(Product.create(
				name,
				null,
				sku,
				ProductType.RAW_MATERIAL,
				MeasurementUnit.UNIT));
	}

	private InventoryLotResponse createLot(Product product, String lotNumber) {
		return inventoryService.createLot(lotRequest(product.getId(), lotNumber), ACTOR);
	}

	private static CreateInventoryLotRequest lotRequest(Long productId, String lotNumber) {
		return new CreateInventoryLotRequest(
				productId,
				lotNumber,
				REFERENCE_DATE,
				REFERENCE_DATE.plusYears(1),
				new BigDecimal("10.000000"),
				new BigDecimal("1.0000"));
	}

	private static CreateProductRequest createProductRequest(String name) {
		return new CreateProductRequest(
				name,
				null,
				ProductType.RAW_MATERIAL,
				MeasurementUnit.UNIT,
				true);
	}

	private static UpdateProductRequest updateProductRequest(String name) {
		return new UpdateProductRequest(
				name,
				null,
				ProductType.RAW_MATERIAL,
				MeasurementUnit.UNIT,
				true);
	}

	private Long insertValidCreatedOrder(Long productId) {
		return jdbcTemplate.queryForObject("""
				INSERT INTO production_orders (
				    product_id, quantity_to_produce, status, version, created_at
				) VALUES (?, 1, 'CREATED', 0, ?)
				RETURNING id
				""", Long.class, productId, FIXED_JDBC_TIMESTAMP);
	}

	private void assertInvalidLifecycle(
			Long productId,
			String status,
			Instant startedAt,
			Instant completedAt) {
		assertCheckViolation(() -> jdbcTemplate.update("""
				INSERT INTO production_orders (
				    product_id, quantity_to_produce, status, version,
				    created_at, started_at, completed_at
				) VALUES (?, 1, ?, 0, ?, ?, ?)
				""", productId, status, FIXED_JDBC_TIMESTAMP,
				toJdbcTimestamp(startedAt), toJdbcTimestamp(completedAt)));
	}

	private static OffsetDateTime toJdbcTimestamp(Instant instant) {
		return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
	}

	private static void assertCheckViolation(Runnable operation) {
		assertThatExceptionOfType(DataIntegrityViolationException.class)
				.isThrownBy(operation::run);
	}

	private static void assertOneConstraintWinner(List<Attempt> attempts) {
		assertThat(attempts).filteredOn(Attempt::succeeded).hasSize(1);
		assertThat(attempts)
				.filteredOn(attempt -> !attempt.succeeded())
				.singleElement()
				.satisfies(attempt -> assertThat(attempt.failure())
						.isInstanceOf(DataIntegrityViolationException.class));
	}

	private static void assertOneOptimisticLockWinner(List<Attempt> attempts) {
		assertThat(attempts).filteredOn(Attempt::succeeded).hasSize(1);
		assertThat(attempts)
				.filteredOn(attempt -> !attempt.succeeded())
				.singleElement()
				.satisfies(attempt -> assertThat(attempt.failure())
						.isInstanceOf(OptimisticLockingFailureException.class));
	}

	private static List<Attempt> runConcurrently(
			Callable<?> firstOperation,
			Callable<?> secondOperation) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<Attempt> first = executor.submit(() -> attempt(firstOperation));
			Future<Attempt> second = executor.submit(() -> attempt(secondOperation));
			return List.of(
					first.get(30, TimeUnit.SECONDS),
					second.get(30, TimeUnit.SECONDS));
		} finally {
			executor.shutdownNow();
			assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
		}
	}

	private static Attempt attempt(Callable<?> operation) {
		try {
			return Attempt.successful(operation.call());
		} catch (RuntimeException exception) {
			return Attempt.failed(exception);
		} catch (Exception exception) {
			return Attempt.failed(new IllegalStateException(exception));
		}
	}

	private static void await(CyclicBarrier barrier) {
		try {
			barrier.await(10, TimeUnit.SECONDS);
		} catch (Exception exception) {
			throw new IllegalStateException("Concurrent repository barrier failed", exception);
		}
	}

	private record Attempt(boolean succeeded, Object result, RuntimeException failure) {

		private static Attempt successful(Object result) {
			return new Attempt(true, result, null);
		}

		private static Attempt failed(RuntimeException failure) {
			return new Attempt(false, null, failure);
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
