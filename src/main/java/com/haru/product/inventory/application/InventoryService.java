package com.haru.product.inventory.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.haru.product.inventory.application.dto.AdjustInventoryRequest;
import com.haru.product.inventory.application.dto.ConsumeInventoryRequest;
import com.haru.product.inventory.application.dto.CreateInventoryLotRequest;
import com.haru.product.inventory.application.dto.InventoryAvailabilityResponse;
import com.haru.product.inventory.application.dto.InventoryConsumptionResponse;
import com.haru.product.inventory.application.dto.InventoryLotResponse;
import com.haru.product.inventory.application.dto.InventoryMovementResponse;
import com.haru.product.inventory.domain.InventoryLot;
import com.haru.product.inventory.domain.InventoryLotStatus;
import com.haru.product.inventory.domain.InventoryMovement;
import com.haru.product.inventory.domain.InventoryMovementType;
import com.haru.product.inventory.domain.exception.DuplicateInventoryLotException;
import com.haru.product.inventory.domain.exception.InsufficientInventoryException;
import com.haru.product.inventory.domain.exception.InvalidInventoryAdjustmentException;
import com.haru.product.inventory.domain.exception.InventoryLotNotFoundException;
import com.haru.product.inventory.infrastructure.persistence.InventoryLotRepository;
import com.haru.product.inventory.infrastructure.persistence.InventoryMovementRepository;
import com.haru.product.product.domain.Product;
import com.haru.product.product.domain.exception.ProductNotFoundException;
import com.haru.product.product.infrastructure.persistence.ProductRepository;
import com.haru.product.shared.pagination.OffsetLimitPageable;
import com.haru.product.shared.pagination.OffsetPageResponse;

@Service
@Transactional(readOnly = true)
public class InventoryService {

	private static final String LOT_REFERENCE = "INVENTORY_LOT";
	private static final String ADJUSTMENT_REFERENCE = "INVENTORY_ADJUSTMENT";
	private static final String DIRECT_CONSUMPTION_REFERENCE = "DIRECT_CONSUMPTION";
	private static final Set<String> SYSTEM_REFERENCE_TYPES = Set.of(
			LOT_REFERENCE,
			ADJUSTMENT_REFERENCE,
			"PRODUCTION_ORDER");
	private static final int FEFO_BATCH_SIZE = 100;

	private final InventoryLotRepository inventoryLotRepository;
	private final InventoryMovementRepository inventoryMovementRepository;
	private final ProductRepository productRepository;
	private final Clock clock;

	public InventoryService(
			InventoryLotRepository inventoryLotRepository,
			InventoryMovementRepository inventoryMovementRepository,
			ProductRepository productRepository,
			Clock clock) {
		this.inventoryLotRepository = inventoryLotRepository;
		this.inventoryMovementRepository = inventoryMovementRepository;
		this.productRepository = productRepository;
		this.clock = clock;
	}

	@Transactional
	public InventoryLotResponse createLot(CreateInventoryLotRequest request, String createdBy) {
		Product product = requireProduct(request.productId());
		String lotNumber = InventoryLot.normalizeLotNumber(request.lotNumber());
		if (inventoryLotRepository.existsByProductIdAndLotNumber(product.getId(), lotNumber)) {
			throw new DuplicateInventoryLotException(product.getId(), lotNumber);
		}

		LocalDate referenceDate = referenceDate();
		InventoryLot lot = InventoryLot.create(
				product,
				lotNumber,
				request.manufactureDate(),
				request.expirationDate(),
				request.initialQuantity(),
				request.unitCost(),
				referenceDate);
		InventoryLot savedLot = inventoryLotRepository.saveAndFlush(lot);
		InventoryMovement entry = InventoryMovement.create(
				savedLot,
				InventoryMovementType.ENTRY,
				savedLot.getInitialQuantity(),
				savedLot.getAvailableQuantity(),
				LOT_REFERENCE,
				savedLot.getId(),
				"Initial inventory lot entry",
				clock.instant(),
				createdBy);
		inventoryMovementRepository.save(entry);
		return toLotResponse(savedLot, referenceDate);
	}

	public InventoryLotResponse getLot(Long id) {
		return toLotResponse(requireLot(id), referenceDate());
	}

	public OffsetPageResponse<InventoryLotResponse> getProductLots(
			Long productId,
			long offset,
			int limit) {
		requireProduct(productId);
		LocalDate referenceDate = referenceDate();
		var page = inventoryLotRepository
				.findAllByProductIdOrderByIdAsc(
						productId,
						OffsetLimitPageable.of(offset, limit))
				.map(lot -> toLotResponse(lot, referenceDate));
		return OffsetPageResponse.from(page, offset, limit);
	}

	public InventoryAvailabilityResponse getAvailability(Long productId) {
		Product product = requireProduct(productId);
		LocalDate referenceDate = referenceDate();
		BigDecimal available = inventoryLotRepository.sumAvailableQuantity(productId, referenceDate);
		return new InventoryAvailabilityResponse(
				product.getId(),
				product.getName(),
				product.getSku(),
				product.getDefaultMeasurementUnit(),
				available == null ? BigDecimal.ZERO : available,
				referenceDate);
	}

	public OffsetPageResponse<InventoryMovementResponse> getProductMovements(
			Long productId,
			long offset,
			int limit) {
		requireProduct(productId);
		var page = inventoryMovementRepository
				.findAllByInventoryLotProductIdOrderByOccurredAtDescIdDesc(
						productId,
						OffsetLimitPageable.of(offset, limit))
				.map(InventoryService::toMovementResponse);
		return OffsetPageResponse.from(page, offset, limit);
	}

	@Transactional
	public InventoryLotResponse adjustIn(
			Long lotId,
			AdjustInventoryRequest request,
			String createdBy) {
		InventoryLot currentLot = requireLot(lotId);
		validateAdjustment(request);
		requireOperationQuantity(
				currentLot.getAvailableQuantity().add(request.quantity()),
				"Resulting inventory quantity");
		LocalDate referenceDate = referenceDate();
		Instant occurredAt = clock.instant();
		int updated = inventoryLotRepository.increaseForAdjustment(
				lotId,
				request.quantity(),
				referenceDate,
				occurredAt,
				InventoryLotStatus.AVAILABLE,
				InventoryLotStatus.EXPIRED,
				InventoryLotStatus.BLOCKED);
		if (updated == 0) {
			throw optimisticConflict(InventoryLot.class, currentLot.getId());
		}

		InventoryLot updatedLot = requireLot(lotId);
		recordMovement(
				updatedLot,
				InventoryMovementType.ADJUSTMENT_IN,
				request.quantity(),
				ADJUSTMENT_REFERENCE,
				lotId,
				request.justification(),
				occurredAt,
				createdBy);
		return toLotResponse(updatedLot, referenceDate);
	}

	@Transactional
	public InventoryLotResponse adjustOut(
			Long lotId,
			AdjustInventoryRequest request,
			String createdBy) {
		InventoryLot currentLot = requireLot(lotId);
		validateAdjustment(request);
		if (currentLot.getAvailableQuantity().compareTo(request.quantity()) < 0) {
			throw new InsufficientInventoryException(
					request.quantity(), currentLot.getAvailableQuantity());
		}

		Instant occurredAt = clock.instant();
		int updated = inventoryLotRepository.decreaseForAdjustment(
				lotId,
				request.quantity(),
				occurredAt,
				InventoryLotStatus.DEPLETED);
		if (updated == 0) {
			InventoryLot latestLot = requireLot(lotId);
			throw new InsufficientInventoryException(
					request.quantity(), latestLot.getAvailableQuantity());
		}

		InventoryLot updatedLot = requireLot(lotId);
		recordMovement(
				updatedLot,
				InventoryMovementType.ADJUSTMENT_OUT,
				request.quantity(),
				ADJUSTMENT_REFERENCE,
				lotId,
				request.justification(),
				occurredAt,
				createdBy);
		return toLotResponse(updatedLot, referenceDate());
	}

	@Transactional
	public InventoryConsumptionResponse consume(
			Long productId,
			ConsumeInventoryRequest request,
			String createdBy) {
		Product product = requireProduct(productId);
		BigDecimal requestedQuantity = requireConsumptionQuantity(request.quantity());
		String referenceType = normalizeReferenceType(request.referenceType());
		String description = normalizeConsumptionDescription(request.description());
		LocalDate referenceDate = referenceDate();
		Instant occurredAt = clock.instant();
		BigDecimal available = inventoryLotRepository
				.sumAvailableQuantity(productId, referenceDate);
		if (available == null) {
			available = BigDecimal.ZERO;
		}
		if (available.compareTo(requestedQuantity) < 0) {
			throw new InsufficientInventoryException(productId, requestedQuantity, available);
		}

		BigDecimal remaining = requestedQuantity;
		List<InventoryConsumptionResponse.LotConsumption> allocations = new ArrayList<>();
		while (remaining.signum() > 0) {
			List<InventoryLot> candidates = inventoryLotRepository
					.findAvailableLotsForFefo(
							productId,
							referenceDate,
							PageRequest.of(0, FEFO_BATCH_SIZE));
			if (candidates.isEmpty()) {
				throw insufficientAfterConcurrentChange(
						productId, requestedQuantity, referenceDate);
			}

			for (InventoryLot candidate : candidates) {
				if (remaining.signum() == 0) {
					break;
				}
				BigDecimal allocation = candidate.getAvailableQuantity().min(remaining);
				int updated = inventoryLotRepository.decreaseForConsumption(
						candidate.getId(),
						allocation,
						referenceDate,
						occurredAt,
						InventoryLotStatus.AVAILABLE,
						InventoryLotStatus.DEPLETED);
				if (updated == 0) {
					throw insufficientAfterConcurrentChange(
							productId, requestedQuantity, referenceDate);
				}

				InventoryLot updatedLot = requireLot(candidate.getId());
				InventoryMovement movement = recordMovement(
						updatedLot,
						InventoryMovementType.EXIT,
						allocation,
						referenceType,
						request.referenceId(),
						description,
						occurredAt,
						createdBy);
				allocations.add(new InventoryConsumptionResponse.LotConsumption(
						updatedLot.getId(),
						updatedLot.getLotNumber(),
						updatedLot.getExpirationDate(),
						allocation,
						updatedLot.getAvailableQuantity(),
						effectiveStatus(updatedLot, referenceDate),
						movement.getId()));
				remaining = remaining.subtract(allocation);
			}
		}

		return new InventoryConsumptionResponse(
				product.getId(),
				product.getName(),
				product.getSku(),
				requestedQuantity,
				requestedQuantity,
				product.getDefaultMeasurementUnit(),
				referenceDate,
				allocations);
	}

	private InventoryMovement recordMovement(
			InventoryLot lot,
			InventoryMovementType type,
			BigDecimal quantity,
			String referenceType,
			Long referenceId,
			String description,
			Instant occurredAt,
			String createdBy) {
		InventoryMovement movement = InventoryMovement.create(
				lot,
				type,
				quantity,
				lot.getAvailableQuantity(),
				referenceType,
				referenceId,
				description,
				occurredAt,
				createdBy);
		return inventoryMovementRepository.save(movement);
	}

	private Product requireProduct(Long id) {
		return productRepository.findById(id)
				.orElseThrow(() -> new ProductNotFoundException(id));
	}

	private InventoryLot requireLot(Long id) {
		return inventoryLotRepository.findById(id)
				.orElseThrow(() -> new InventoryLotNotFoundException(id));
	}

	private LocalDate referenceDate() {
		return LocalDate.now(clock);
	}

	private static void validateAdjustment(AdjustInventoryRequest request) {
		requireOperationQuantity(request.quantity(), "Inventory adjustment quantity");
		if (request.justification() == null || request.justification().isBlank()) {
			throw new InvalidInventoryAdjustmentException(
					"Inventory adjustment justification is required");
		}
	}

	private static BigDecimal requireConsumptionQuantity(BigDecimal quantity) {
		return requireOperationQuantity(quantity, "Inventory consumption quantity");
	}

	private static BigDecimal requireOperationQuantity(BigDecimal quantity, String fieldName) {
		if (quantity == null || quantity.signum() <= 0) {
			throw new InvalidInventoryAdjustmentException(
					fieldName + " must be greater than zero");
		}
		int integerDigits = quantity.precision() - quantity.scale();
		if (integerDigits > 13 || quantity.scale() > 6) {
			throw new InvalidInventoryAdjustmentException(
					fieldName + " must fit NUMERIC(19, 6)");
		}
		return quantity;
	}

	private static String normalizeReferenceType(String referenceType) {
		if (referenceType == null || referenceType.isBlank()) {
			return DIRECT_CONSUMPTION_REFERENCE;
		}
		String normalized = referenceType.strip();
		if (SYSTEM_REFERENCE_TYPES.stream().anyMatch(
				type -> type.equalsIgnoreCase(normalized))) {
			throw new InvalidInventoryAdjustmentException(
					"System inventory reference type cannot be supplied by a direct consumption request");
		}
		return normalized;
	}

	private static String normalizeConsumptionDescription(String description) {
		return description == null || description.isBlank()
				? "Direct inventory consumption"
				: description.strip();
	}

	private static InventoryLotResponse toLotResponse(
			InventoryLot lot,
			LocalDate referenceDate) {
		Product product = lot.getProduct();
		return new InventoryLotResponse(
				lot.getId(),
				product.getId(),
				product.getName(),
				product.getSku(),
				lot.getLotNumber(),
				lot.getManufactureDate(),
				lot.getExpirationDate(),
				lot.getInitialQuantity(),
				lot.getAvailableQuantity(),
				lot.getUnitCost(),
				effectiveStatus(lot, referenceDate),
				lot.getCreatedAt(),
				lot.getUpdatedAt(),
				lot.getVersion());
	}

	private static InventoryMovementResponse toMovementResponse(InventoryMovement movement) {
		InventoryLot lot = movement.getInventoryLot();
		return new InventoryMovementResponse(
				movement.getId(),
				lot.getId(),
				lot.getLotNumber(),
				lot.getProduct().getId(),
				movement.getType(),
				movement.getQuantity(),
				movement.getResultingQuantity(),
				movement.getReferenceType(),
				movement.getReferenceId(),
				movement.getDescription(),
				movement.getOccurredAt(),
				movement.getCreatedBy());
	}

	private static InventoryLotStatus effectiveStatus(
			InventoryLot lot,
			LocalDate referenceDate) {
		return lot.getStatus() == InventoryLotStatus.AVAILABLE && lot.isExpired(referenceDate)
				? InventoryLotStatus.EXPIRED
				: lot.getStatus();
	}

	private InsufficientInventoryException insufficientAfterConcurrentChange(
			Long productId,
			BigDecimal requestedQuantity,
			LocalDate referenceDate) {
		BigDecimal latestAvailable = inventoryLotRepository
				.sumAvailableQuantity(productId, referenceDate);
		return new InsufficientInventoryException(
				productId,
				requestedQuantity,
				latestAvailable == null ? BigDecimal.ZERO : latestAvailable);
	}

	private static ObjectOptimisticLockingFailureException optimisticConflict(
			Class<?> entityType,
			Object id) {
		return new ObjectOptimisticLockingFailureException(entityType, id);
	}
}
