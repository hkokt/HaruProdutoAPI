package com.haru.product.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

import com.haru.product.inventory.application.dto.AdjustInventoryRequest;
import com.haru.product.inventory.application.dto.ConsumeInventoryRequest;
import com.haru.product.inventory.application.dto.CreateInventoryLotRequest;
import com.haru.product.inventory.domain.InventoryLot;
import com.haru.product.inventory.domain.InventoryLotStatus;
import com.haru.product.inventory.domain.InventoryMovement;
import com.haru.product.inventory.domain.InventoryMovementType;
import com.haru.product.inventory.domain.exception.InsufficientInventoryException;
import com.haru.product.inventory.domain.exception.InvalidInventoryAdjustmentException;
import com.haru.product.inventory.infrastructure.persistence.InventoryLotRepository;
import com.haru.product.inventory.infrastructure.persistence.InventoryMovementRepository;
import com.haru.product.product.domain.MeasurementUnit;
import com.haru.product.product.domain.Product;
import com.haru.product.product.domain.ProductType;
import com.haru.product.product.infrastructure.persistence.ProductRepository;
import com.haru.product.shared.pagination.OffsetLimitPageable;

class InventoryServiceTests {

	private static final Long PRODUCT_ID = 1L;
	private static final Long LOT_ID = 10L;
	private static final Instant FIXED_INSTANT = Instant.parse("2026-07-22T12:00:00Z");
	private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 7, 22);
	private static final String ACTOR = "inventory-admin@example.com";

	private final InventoryLotRepository inventoryLotRepository =
			mock(InventoryLotRepository.class);
	private final InventoryMovementRepository inventoryMovementRepository =
			mock(InventoryMovementRepository.class);
	private final ProductRepository productRepository = mock(ProductRepository.class);
	private final InventoryService service = new InventoryService(
			inventoryLotRepository,
			inventoryMovementRepository,
			productRepository,
			Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC));

	@Test
	void createsLotAndRecordsEntryWithResultingBalanceAndActor() {
		Product product = product();
		when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
		when(inventoryLotRepository.saveAndFlush(any(InventoryLot.class)))
				.thenAnswer(invocation -> withLotId(
						invocation.getArgument(0, InventoryLot.class), LOT_ID));
		when(inventoryMovementRepository.save(any(InventoryMovement.class)))
				.thenAnswer(invocation -> withMovementId(
						invocation.getArgument(0, InventoryMovement.class), 100L));

		var response = service.createLot(new CreateInventoryLotRequest(
				PRODUCT_ID,
				"  ESS-001  ",
				LocalDate.of(2026, 7, 1),
				LocalDate.of(2026, 8, 10),
				new BigDecimal("100.000000"),
				new BigDecimal("0.5000")), ACTOR);

		ArgumentCaptor<InventoryMovement> movementCaptor =
				ArgumentCaptor.forClass(InventoryMovement.class);
		verify(inventoryMovementRepository).save(movementCaptor.capture());
		InventoryMovement movement = movementCaptor.getValue();
		assertThat(response.id()).isEqualTo(LOT_ID);
		assertThat(response.lotNumber()).isEqualTo("ESS-001");
		assertThat(response.availableQuantity()).isEqualByComparingTo("100");
		assertThat(response.status()).isEqualTo(InventoryLotStatus.AVAILABLE);
		assertThat(movement.getType()).isEqualTo(InventoryMovementType.ENTRY);
		assertThat(movement.getQuantity()).isEqualByComparingTo("100");
		assertThat(movement.getResultingQuantity()).isEqualByComparingTo("100");
		assertThat(movement.getOccurredAt()).isEqualTo(FIXED_INSTANT);
		assertThat(movement.getCreatedBy()).isEqualTo(ACTOR);
	}

	@Test
	void rejectsInsufficientInventoryBeforeUpdatingLotsOrRecordingMovements() {
		Product product = product();
		when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
		when(inventoryLotRepository.sumAvailableQuantity(PRODUCT_ID, REFERENCE_DATE))
				.thenReturn(new BigDecimal("40"));

		assertThatExceptionOfType(InsufficientInventoryException.class)
				.isThrownBy(() -> service.consume(
						PRODUCT_ID,
						consumptionRequest("50"),
						ACTOR));

		verify(inventoryLotRepository, never()).decreaseForConsumption(
				any(), any(), any(), any(), any(), any());
		verify(inventoryLotRepository, never()).findAvailableLotsForFefo(
				any(), any(), any(Pageable.class));
		verify(inventoryMovementRepository, never()).save(any(InventoryMovement.class));
	}

	@Test
	void reportsInsufficientInventoryWhenAtomicConsumptionUpdatesNoRows() {
		Product product = product();
		InventoryLot lot = lot(product, LOT_ID, "50");
		when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
		when(inventoryLotRepository.sumAvailableQuantity(PRODUCT_ID, REFERENCE_DATE))
				.thenReturn(new BigDecimal("50"))
				.thenReturn(BigDecimal.TEN);
		when(inventoryLotRepository.findAvailableLotsForFefo(
				PRODUCT_ID, REFERENCE_DATE, PageRequest.of(0, 100)))
				.thenReturn(List.of(lot));
		when(inventoryLotRepository.decreaseForConsumption(
				LOT_ID,
				new BigDecimal("40"),
				REFERENCE_DATE,
				FIXED_INSTANT,
				InventoryLotStatus.AVAILABLE,
				InventoryLotStatus.DEPLETED))
				.thenReturn(0);
		assertThatExceptionOfType(InsufficientInventoryException.class)
				.isThrownBy(() -> service.consume(
						PRODUCT_ID,
						consumptionRequest("40"),
						ACTOR))
				.withMessageContaining("available 10");

		verify(inventoryMovementRepository, never()).save(any(InventoryMovement.class));
	}

	@Test
	void rejectsConsumptionQuantityWithMoreThanSixDecimalPlacesAtServiceBoundary() {
		when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product()));

		assertThatExceptionOfType(InvalidInventoryAdjustmentException.class)
				.isThrownBy(() -> service.consume(
						PRODUCT_ID,
						consumptionRequest("1.0000001"),
						ACTOR))
				.withMessageContaining("NUMERIC(19, 6)");

		verify(inventoryLotRepository, never()).findAvailableLotsForFefo(
				any(), any(), any(Pageable.class));
	}

	@Test
	void rejectsConsumptionQuantityWithMoreThanThirteenIntegerDigitsAtServiceBoundary() {
		when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product()));

		assertThatExceptionOfType(InvalidInventoryAdjustmentException.class)
				.isThrownBy(() -> service.consume(
						PRODUCT_ID,
						consumptionRequest("10000000000000"),
						ACTOR))
				.withMessageContaining("NUMERIC(19, 6)");

		verify(inventoryLotRepository, never()).findAvailableLotsForFefo(
				any(), any(), any(Pageable.class));
	}

	@Test
	void rejectsSystemReferenceTypesBeforeReadingOrChangingInventory() {
		when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product()));

		assertThatExceptionOfType(InvalidInventoryAdjustmentException.class)
				.isThrownBy(() -> service.consume(
						PRODUCT_ID,
						new ConsumeInventoryRequest(
								BigDecimal.ONE,
								"production_order",
								99L,
								"Forged system reference"),
						ACTOR))
				.withMessageContaining("System inventory reference type");

		verify(inventoryLotRepository, never()).sumAvailableQuantity(any(), any());
		verify(inventoryLotRepository, never()).decreaseForConsumption(
				any(), any(), any(), any(), any(), any());
	}

	@Test
	void appliesAnArbitraryInventoryLotOffsetAndMapsEntitiesInsideTheTransaction() {
		Product product = product();
		InventoryLot inventoryLot = lot(product, LOT_ID, "100");
		OffsetLimitPageable pageable = OffsetLimitPageable.of(15, 20);
		when(productRepository.findById(PRODUCT_ID)).thenReturn(Optional.of(product));
		when(inventoryLotRepository.findAllByProductIdOrderByIdAsc(
				PRODUCT_ID,
				pageable))
				.thenReturn(new PageImpl<>(List.of(inventoryLot), pageable, 36));

		var response = service.getProductLots(PRODUCT_ID, 15, 20);

		assertThat(response.offset()).isEqualTo(15);
		assertThat(response.limit()).isEqualTo(20);
		assertThat(response.totalElements()).isEqualTo(36);
		assertThat(response.hasPrevious()).isTrue();
		assertThat(response.hasNext()).isTrue();
		assertThat(response.content())
				.singleElement()
				.satisfies(lot -> assertThat(lot.id()).isEqualTo(LOT_ID));
		verify(inventoryLotRepository)
				.findAllByProductIdOrderByIdAsc(PRODUCT_ID, pageable);
	}

	@Test
	void recordsAdjustmentInTypeJustificationAndActor() {
		Product product = product();
		InventoryLot currentLot = lot(product, LOT_ID, "100");
		InventoryLot updatedLot = lot(product, LOT_ID, "100");
		updatedLot.increaseAvailableQuantity(BigDecimal.TEN, REFERENCE_DATE);
		when(inventoryLotRepository.findById(LOT_ID))
				.thenReturn(Optional.of(currentLot))
				.thenReturn(Optional.of(updatedLot));
		when(inventoryLotRepository.increaseForAdjustment(
				LOT_ID,
				BigDecimal.TEN,
				REFERENCE_DATE,
				FIXED_INSTANT,
				InventoryLotStatus.AVAILABLE,
				InventoryLotStatus.EXPIRED,
				InventoryLotStatus.BLOCKED))
				.thenReturn(1);
		when(inventoryMovementRepository.save(any(InventoryMovement.class)))
				.thenAnswer(invocation -> invocation.getArgument(0, InventoryMovement.class));

		service.adjustIn(
				LOT_ID,
				new AdjustInventoryRequest(BigDecimal.TEN, "Cycle count correction"),
				ACTOR);

		assertAdjustmentMovement(
				InventoryMovementType.ADJUSTMENT_IN,
				"110",
				"Cycle count correction");
	}

	@Test
	void recordsAdjustmentOutTypeJustificationAndActor() {
		Product product = product();
		InventoryLot currentLot = lot(product, LOT_ID, "100");
		InventoryLot updatedLot = lot(product, LOT_ID, "100");
		updatedLot.decreaseAvailableQuantity(BigDecimal.TEN, REFERENCE_DATE);
		when(inventoryLotRepository.findById(LOT_ID))
				.thenReturn(Optional.of(currentLot))
				.thenReturn(Optional.of(updatedLot));
		when(inventoryLotRepository.decreaseForAdjustment(
				LOT_ID,
				BigDecimal.TEN,
				FIXED_INSTANT,
				InventoryLotStatus.DEPLETED))
				.thenReturn(1);
		when(inventoryMovementRepository.save(any(InventoryMovement.class)))
				.thenAnswer(invocation -> invocation.getArgument(0, InventoryMovement.class));

		service.adjustOut(
				LOT_ID,
				new AdjustInventoryRequest(BigDecimal.TEN, "Damaged material correction"),
				ACTOR);

		assertAdjustmentMovement(
				InventoryMovementType.ADJUSTMENT_OUT,
				"90",
				"Damaged material correction");
	}

	private void assertAdjustmentMovement(
			InventoryMovementType expectedType,
			String expectedResultingQuantity,
			String expectedJustification) {
		ArgumentCaptor<InventoryMovement> movementCaptor =
				ArgumentCaptor.forClass(InventoryMovement.class);
		verify(inventoryMovementRepository).save(movementCaptor.capture());
		InventoryMovement movement = movementCaptor.getValue();
		assertThat(movement.getType()).isEqualTo(expectedType);
		assertThat(movement.getQuantity()).isEqualByComparingTo("10");
		assertThat(movement.getResultingQuantity())
				.isEqualByComparingTo(expectedResultingQuantity);
		assertThat(movement.getReferenceType()).isEqualTo("INVENTORY_ADJUSTMENT");
		assertThat(movement.getReferenceId()).isEqualTo(LOT_ID);
		assertThat(movement.getDescription()).isEqualTo(expectedJustification);
		assertThat(movement.getOccurredAt()).isEqualTo(FIXED_INSTANT);
		assertThat(movement.getCreatedBy()).isEqualTo(ACTOR);
	}

	private static ConsumeInventoryRequest consumptionRequest(String quantity) {
		return new ConsumeInventoryRequest(
				new BigDecimal(quantity),
				"UNIT_TEST",
				99L,
				"Unit test consumption");
	}

	private static Product product() {
		Product product = Product.create(
				"Sakura Essence",
				null,
				"ESS-SAKURA",
				ProductType.RAW_MATERIAL,
				MeasurementUnit.MILLILITER);
		ReflectionTestUtils.setField(product, "id", PRODUCT_ID);
		return product;
	}

	private static InventoryLot lot(Product product, Long id, String quantity) {
		InventoryLot lot = InventoryLot.create(
				product,
				"ESS-" + id,
				LocalDate.of(2026, 7, 1),
				LocalDate.of(2026, 8, 10),
				new BigDecimal(quantity),
				new BigDecimal("0.5000"),
				REFERENCE_DATE);
		ReflectionTestUtils.setField(lot, "id", id);
		return lot;
	}

	private static InventoryLot withLotId(InventoryLot lot, Long id) {
		ReflectionTestUtils.setField(lot, "id", id);
		return lot;
	}

	private static InventoryMovement withMovementId(InventoryMovement movement, Long id) {
		ReflectionTestUtils.setField(movement, "id", id);
		return movement;
	}
}
