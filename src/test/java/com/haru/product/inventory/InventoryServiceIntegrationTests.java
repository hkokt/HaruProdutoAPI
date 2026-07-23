package com.haru.product.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

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
import com.haru.product.inventory.application.dto.ConsumeInventoryRequest;
import com.haru.product.inventory.application.dto.CreateInventoryLotRequest;
import com.haru.product.inventory.application.dto.InventoryConsumptionResponse;
import com.haru.product.inventory.application.dto.InventoryLotResponse;
import com.haru.product.inventory.domain.InventoryLot;
import com.haru.product.inventory.domain.InventoryLotStatus;
import com.haru.product.inventory.domain.InventoryMovement;
import com.haru.product.inventory.domain.InventoryMovementType;
import com.haru.product.inventory.domain.exception.DuplicateInventoryLotException;
import com.haru.product.inventory.domain.exception.InsufficientInventoryException;
import com.haru.product.inventory.domain.exception.InvalidInventoryLotException;
import com.haru.product.inventory.infrastructure.persistence.InventoryLotRepository;
import com.haru.product.inventory.infrastructure.persistence.InventoryMovementRepository;
import com.haru.product.product.domain.MeasurementUnit;
import com.haru.product.product.domain.Product;
import com.haru.product.product.domain.ProductType;
import com.haru.product.product.infrastructure.persistence.ProductRepository;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@Import(InventoryServiceIntegrationTests.FixedClockConfiguration.class)
class InventoryServiceIntegrationTests {

	private static final Instant FIXED_INSTANT = Instant.parse("2026-07-22T12:00:00Z");
	private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 7, 22);
	private static final String ACTOR = "inventory-test@example.com";

	@Container
	private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
			.withDatabaseName("haru_product_test")
			.withUsername("haru_test")
			.withPassword("haru_test_password");

	@Autowired
	private InventoryService inventoryService;

	@Autowired
	private InventoryLotRepository inventoryLotRepository;

	@Autowired
	private InventoryMovementRepository inventoryMovementRepository;

	@Autowired
	private ProductRepository productRepository;

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
				TRUNCATE TABLE inventory_movements, inventory_lots, product_compositions, products
				RESTART IDENTITY CASCADE
				""");
	}

	@Test
	void createsLotWithAvailableBalanceAndEntryMovement() {
		Product product = persistEssence();

		InventoryLotResponse created = inventoryService.createLot(
				lotRequest(
						product.getId(),
						"ESS-001",
						LocalDate.of(2026, 7, 1),
						LocalDate.of(2026, 8, 10),
						"100"),
				ACTOR);

		assertThat(created.availableQuantity()).isEqualByComparingTo("100");
		assertThat(created.status()).isEqualTo(InventoryLotStatus.AVAILABLE);
		assertThat(movementsOfType(created.id(), InventoryMovementType.ENTRY))
				.singleElement()
				.satisfies(movement -> {
					assertThat(movement.getQuantity()).isEqualByComparingTo("100");
					assertThat(movement.getResultingQuantity()).isEqualByComparingTo("100");
					assertThat(movement.getOccurredAt()).isEqualTo(FIXED_INSTANT);
					assertThat(movement.getCreatedBy()).isEqualTo(ACTOR);
				});
	}

	@Test
	void rejectsExpirationBeforeManufactureWithoutPersistingAnything() {
		Product product = persistEssence();

		assertThatExceptionOfType(InvalidInventoryLotException.class)
				.isThrownBy(() -> inventoryService.createLot(
						lotRequest(
								product.getId(),
								"ESS-INVALID-DATES",
								LocalDate.of(2026, 8, 10),
								LocalDate.of(2026, 8, 1),
								"100"),
						ACTOR));

		assertThat(inventoryLotRepository.count()).isZero();
		assertThat(inventoryMovementRepository.count()).isZero();
	}

	@Test
	void rejectsDuplicateLotNumberForTheSameProduct() {
		Product product = persistEssence();
		CreateInventoryLotRequest request = lotRequest(
				product.getId(),
				"ESS-001",
				LocalDate.of(2026, 7, 1),
				LocalDate.of(2026, 8, 10),
				"100");
		inventoryService.createLot(request, ACTOR);

		assertThatExceptionOfType(DuplicateInventoryLotException.class)
				.isThrownBy(() -> inventoryService.createLot(request, ACTOR));

		assertThat(inventoryLotRepository.count()).isOne();
		assertThat(inventoryMovementRepository.count()).isOne();
	}

	@Test
	void pagesProductLotsAtAnArbitraryDatabaseOffset() {
		Product product = persistEssence();
		createLot(product, "ESS-001", "10", LocalDate.of(2026, 8, 10));
		InventoryLotResponse second = createLot(
				product, "ESS-002", "20", LocalDate.of(2026, 8, 20));
		InventoryLotResponse third = createLot(
				product, "ESS-003", "30", LocalDate.of(2026, 8, 30));

		var response = inventoryService.getProductLots(product.getId(), 1, 2);

		assertThat(response.content()).extracting(InventoryLotResponse::id)
				.containsExactly(second.id(), third.id());
		assertThat(response.offset()).isEqualTo(1);
		assertThat(response.limit()).isEqualTo(2);
		assertThat(response.totalElements()).isEqualTo(3);
		assertThat(response.hasPrevious()).isTrue();
		assertThat(response.hasNext()).isFalse();
	}

	@Test
	void aggregatesAvailableBalancesForAProductPageInOneRepositoryQuery() {
		Product product = persistEssence();
		createLot(product, "ESS-AVAILABLE", "100", LocalDate.of(2026, 8, 10));
		createLot(product, "ESS-EXPIRED", "50", LocalDate.of(2026, 7, 21));

		var summaries = inventoryLotRepository.summarizeByProductIds(
				List.of(product.getId()),
				REFERENCE_DATE);

		assertThat(summaries).singleElement().satisfies(summary -> {
			assertThat(summary.getProductId()).isEqualTo(product.getId());
			assertThat(summary.getAvailableQuantity()).isEqualByComparingTo("100");
			assertThat(summary.getLotCount()).isEqualTo(2);
		});
	}

	@Test
	void consumesByFefoAcrossLotsAndLeavesNonExpiringLotUntouched() {
		Product product = persistEssence();
		InventoryLotResponse first = createLot(product, "ESS-001", "30", LocalDate.of(2026, 8, 10));
		InventoryLotResponse second = createLot(product, "ESS-002", "40", LocalDate.of(2026, 8, 20));
		InventoryLotResponse withoutExpiration = createLot(product, "ESS-003", "100", null);

		InventoryConsumptionResponse consumption = inventoryService.consume(
				product.getId(),
				consumptionRequest("50"),
				ACTOR);

		assertThat(consumption.consumedQuantity()).isEqualByComparingTo("50");
		assertThat(consumption.lots())
				.extracting(InventoryConsumptionResponse.LotConsumption::lotNumber)
				.containsExactly("ESS-001", "ESS-002");
		assertLot(first.id(), "0", InventoryLotStatus.DEPLETED);
		assertLot(second.id(), "20", InventoryLotStatus.AVAILABLE);
		assertLot(withoutExpiration.id(), "100", InventoryLotStatus.AVAILABLE);
		assertThat(movementsOfType(first.id(), InventoryMovementType.EXIT)).hasSize(1);
		assertThat(movementsOfType(second.id(), InventoryMovementType.EXIT)).hasSize(1);
		assertThat(movementsOfType(withoutExpiration.id(), InventoryMovementType.EXIT)).isEmpty();
	}

	@Test
	void ignoresExpiredLotDuringFefoConsumption() {
		Product product = persistEssence();
		InventoryLotResponse expired = createLot(
				product, "ESS-EXPIRED", "200", REFERENCE_DATE.minusDays(1));
		InventoryLotResponse valid = createLot(
				product, "ESS-VALID", "60", REFERENCE_DATE.plusDays(10));

		InventoryConsumptionResponse consumption = inventoryService.consume(
				product.getId(),
				consumptionRequest("50"),
				ACTOR);

		assertThat(consumption.lots())
				.extracting(InventoryConsumptionResponse.LotConsumption::lotNumber)
				.containsExactly("ESS-VALID");
		assertLot(expired.id(), "200", InventoryLotStatus.EXPIRED);
		assertLot(valid.id(), "10", InventoryLotStatus.AVAILABLE);
		assertThat(movementsOfType(expired.id(), InventoryMovementType.EXIT)).isEmpty();
		assertThat(movementsOfType(valid.id(), InventoryMovementType.EXIT)).hasSize(1);
	}

	@Test
	void rollsBackCompletelyWhenTotalInventoryIsInsufficient() {
		Product product = persistEssence();
		InventoryLotResponse first = createLot(
				product, "ESS-001", "20", REFERENCE_DATE.plusDays(10));
		InventoryLotResponse second = createLot(
				product, "ESS-002", "20", REFERENCE_DATE.plusDays(20));
		long movementsBeforeConsumption = inventoryMovementRepository.count();

		assertThatExceptionOfType(InsufficientInventoryException.class)
				.isThrownBy(() -> inventoryService.consume(
						product.getId(),
						consumptionRequest("50"),
						ACTOR));

		assertLot(first.id(), "20", InventoryLotStatus.AVAILABLE);
		assertLot(second.id(), "20", InventoryLotStatus.AVAILABLE);
		assertThat(inventoryMovementRepository.count()).isEqualTo(movementsBeforeConsumption);
		assertThat(movementsOfType(first.id(), InventoryMovementType.EXIT)).isEmpty();
		assertThat(movementsOfType(second.id(), InventoryMovementType.EXIT)).isEmpty();
	}

	@Test
	void allowsOnlyOneOfTwoConcurrentFortyUnitConsumptions() throws Exception {
		Product product = persistEssence();
		InventoryLotResponse lot = createLot(
				product, "ESS-CONCURRENT", "50", REFERENCE_DATE.plusDays(10));
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		try {
			Future<ConsumptionAttempt> first = executor.submit(
					() -> consumeConcurrently(product.getId(), "concurrent-a", ready, start));
			Future<ConsumptionAttempt> second = executor.submit(
					() -> consumeConcurrently(product.getId(), "concurrent-b", ready, start));
			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			List<ConsumptionAttempt> attempts = List.of(
					first.get(20, TimeUnit.SECONDS),
					second.get(20, TimeUnit.SECONDS));

			assertThat(attempts).filteredOn(ConsumptionAttempt::succeeded).hasSize(1);
			assertThat(attempts)
					.filteredOn(attempt -> !attempt.succeeded())
					.singleElement()
					.satisfies(attempt -> assertThat(attempt.failure())
							.isInstanceOf(InsufficientInventoryException.class));
		} finally {
			start.countDown();
			executor.shutdownNow();
			assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
		}

		InventoryLot persisted = inventoryLotRepository.findById(lot.id()).orElseThrow();
		assertThat(persisted.getAvailableQuantity()).isEqualByComparingTo("10");
		assertThat(persisted.getAvailableQuantity()).isNotNegative();
		assertThat(movementsOfType(lot.id(), InventoryMovementType.EXIT))
				.singleElement()
				.satisfies(movement -> {
					assertThat(movement.getQuantity()).isEqualByComparingTo("40");
					assertThat(movement.getResultingQuantity()).isEqualByComparingTo("10");
				});
	}

	private ConsumptionAttempt consumeConcurrently(
			Long productId,
			String actor,
			CountDownLatch ready,
			CountDownLatch start) throws InterruptedException {
		ready.countDown();
		if (!start.await(10, TimeUnit.SECONDS)) {
			return ConsumptionAttempt.failed(new IllegalStateException("Concurrent start timed out"));
		}
		try {
			inventoryService.consume(productId, consumptionRequest("40"), actor);
			return ConsumptionAttempt.successful();
		} catch (RuntimeException exception) {
			return ConsumptionAttempt.failed(exception);
		}
	}

	private Product persistEssence() {
		return productRepository.saveAndFlush(Product.create(
				"Sakura Essence",
				null,
				"ESS-SAKURA",
				ProductType.RAW_MATERIAL,
				MeasurementUnit.MILLILITER));
	}

	private InventoryLotResponse createLot(
			Product product,
			String lotNumber,
			String quantity,
			LocalDate expirationDate) {
		return inventoryService.createLot(
				lotRequest(
						product.getId(),
						lotNumber,
						LocalDate.of(2026, 7, 1),
						expirationDate,
						quantity),
				ACTOR);
	}

	private static CreateInventoryLotRequest lotRequest(
			Long productId,
			String lotNumber,
			LocalDate manufactureDate,
			LocalDate expirationDate,
			String quantity) {
		return new CreateInventoryLotRequest(
				productId,
				lotNumber,
				manufactureDate,
				expirationDate,
				new BigDecimal(quantity),
				new BigDecimal("0.5000"));
	}

	private static ConsumeInventoryRequest consumptionRequest(String quantity) {
		return new ConsumeInventoryRequest(
				new BigDecimal(quantity),
				"INTEGRATION_TEST",
				99L,
				"Inventory integration test consumption");
	}

	private void assertLot(Long lotId, String expectedQuantity, InventoryLotStatus expectedStatus) {
		InventoryLot lot = inventoryLotRepository.findById(lotId).orElseThrow();
		assertThat(lot.getAvailableQuantity()).isEqualByComparingTo(expectedQuantity);
		assertThat(lot.getStatus()).isEqualTo(expectedStatus);
	}

	private List<InventoryMovement> movementsOfType(
			Long lotId,
			InventoryMovementType type) {
		return inventoryMovementRepository
				.findAllByInventoryLotIdOrderByOccurredAtAscIdAsc(lotId)
				.stream()
				.filter(movement -> movement.getType() == type)
				.toList();
	}

	private record ConsumptionAttempt(boolean succeeded, RuntimeException failure) {

		private static ConsumptionAttempt successful() {
			return new ConsumptionAttempt(true, null);
		}

		private static ConsumptionAttempt failed(RuntimeException failure) {
			return new ConsumptionAttempt(false, failure);
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
