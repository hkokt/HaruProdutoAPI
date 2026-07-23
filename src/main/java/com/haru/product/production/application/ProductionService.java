package com.haru.product.production.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.haru.product.inventory.domain.InventoryLot;
import com.haru.product.inventory.domain.InventoryLotStatus;
import com.haru.product.inventory.domain.InventoryMovement;
import com.haru.product.inventory.domain.InventoryMovementType;
import com.haru.product.inventory.domain.exception.InsufficientInventoryException;
import com.haru.product.inventory.domain.exception.InventoryLotNotFoundException;
import com.haru.product.inventory.infrastructure.persistence.InventoryLotRepository;
import com.haru.product.inventory.infrastructure.persistence.InventoryMovementRepository;
import com.haru.product.product.domain.Product;
import com.haru.product.product.domain.ProductComposition;
import com.haru.product.product.domain.exception.ProductNotFoundException;
import com.haru.product.product.infrastructure.persistence.ProductCompositionRepository;
import com.haru.product.product.infrastructure.persistence.ProductRepository;
import com.haru.product.production.application.dto.CompleteProductionRequest;
import com.haru.product.production.application.dto.CreateProductionOrderRequest;
import com.haru.product.production.application.dto.ProductionConsumptionResponse;
import com.haru.product.production.application.dto.ProductionOrderResponse;
import com.haru.product.production.application.dto.ProductionResultResponse;
import com.haru.product.production.domain.ProducedLot;
import com.haru.product.production.domain.ProductionConsumption;
import com.haru.product.production.domain.ProductionOrder;
import com.haru.product.production.domain.ProductionOrderStatus;
import com.haru.product.production.domain.exception.DuplicateProducedLotException;
import com.haru.product.production.domain.exception.InvalidProductionOrderException;
import com.haru.product.production.domain.exception.InvalidProductionOrderStateException;
import com.haru.product.production.domain.exception.ProductWithoutBomException;
import com.haru.product.production.domain.exception.ProductionOrderNotFoundException;
import com.haru.product.production.infrastructure.persistence.ProducedLotRepository;
import com.haru.product.production.infrastructure.persistence.ProductionConsumptionRepository;
import com.haru.product.production.infrastructure.persistence.ProductionOrderRepository;
import com.haru.product.shared.pagination.OffsetLimitPageable;
import com.haru.product.shared.pagination.OffsetPageResponse;

@Service
@Transactional(readOnly = true)
public class ProductionService {

	private static final String PRODUCTION_ORDER_REFERENCE = "PRODUCTION_ORDER";
	private static final int QUANTITY_INTEGER_DIGITS = 13;
	private static final int QUANTITY_FRACTION_DIGITS = 6;
	private static final int COST_INTEGER_DIGITS = 15;
	private static final int COST_FRACTION_DIGITS = 4;
	private static final int FEFO_BATCH_SIZE = 100;

	private final ProductionOrderRepository productionOrderRepository;
	private final ProductionConsumptionRepository productionConsumptionRepository;
	private final ProducedLotRepository producedLotRepository;
	private final ProductRepository productRepository;
	private final ProductCompositionRepository productCompositionRepository;
	private final InventoryLotRepository inventoryLotRepository;
	private final InventoryMovementRepository inventoryMovementRepository;
	private final Clock clock;

	public ProductionService(
			ProductionOrderRepository productionOrderRepository,
			ProductionConsumptionRepository productionConsumptionRepository,
			ProducedLotRepository producedLotRepository,
			ProductRepository productRepository,
			ProductCompositionRepository productCompositionRepository,
			InventoryLotRepository inventoryLotRepository,
			InventoryMovementRepository inventoryMovementRepository,
			Clock clock) {
		this.productionOrderRepository = productionOrderRepository;
		this.productionConsumptionRepository = productionConsumptionRepository;
		this.producedLotRepository = producedLotRepository;
		this.productRepository = productRepository;
		this.productCompositionRepository = productCompositionRepository;
		this.inventoryLotRepository = inventoryLotRepository;
		this.inventoryMovementRepository = inventoryMovementRepository;
		this.clock = clock;
	}

	@Transactional
	public ProductionOrderResponse create(CreateProductionOrderRequest request) {
		Product product = requireProduct(request.productId());
		requireBom(product);
		ProductionOrder order = ProductionOrder.create(
				product,
				request.quantityToProduce(),
				clock.instant());
		return toOrderResponse(productionOrderRepository.saveAndFlush(order));
	}

	public ProductionResultResponse getById(Long id) {
		return toResultResponse(requireOrder(id));
	}

	public OffsetPageResponse<ProductionOrderResponse> search(
			String query,
			ProductionOrderStatus status,
			long offset,
			int limit) {
		String normalizedQuery = query == null ? "" : query.strip();
		Long numericQuery = parsePositiveLong(normalizedQuery);
		var page = productionOrderRepository.search(
				normalizedQuery,
				numericQuery,
				status,
				OffsetLimitPageable.of(offset, limit))
				.map(ProductionService::toOrderResponse);
		return OffsetPageResponse.from(page, offset, limit);
	}

	@Transactional
	public ProductionOrderResponse start(Long id) {
		ProductionOrder order = requireOrder(id);
		order.start(clock.instant());
		return toOrderResponse(productionOrderRepository.saveAndFlush(order));
	}

	@Transactional
	public ProductionOrderResponse cancel(Long id) {
		ProductionOrder order = requireOrder(id);
		order.cancel();
		return toOrderResponse(productionOrderRepository.saveAndFlush(order));
	}

	@Transactional
	public ProductionResultResponse complete(
			Long id,
			CompleteProductionRequest request,
			String createdBy) {
		ProductionOrder order = requireOrder(id);
		requireInProgress(order);
		String producedLotNumber = validateCompletionRequest(request);
		Product finishedProduct = order.getProduct();
		if (producedLotRepository.existsByProductionOrderId(order.getId())) {
			throw new DuplicateProducedLotException(producedLotNumber);
		}
		if (inventoryLotRepository.existsByProductIdAndLotNumber(
				finishedProduct.getId(), producedLotNumber)) {
			throw new DuplicateProducedLotException(
					finishedProduct.getId(), producedLotNumber);
		}

		List<ProductComposition> bom = requireBom(finishedProduct);
		LocalDate referenceDate = LocalDate.now(clock);
		Instant occurredAt = clock.instant();
		List<ComponentRequirement> requirements = prepareRequirements(
				bom,
				order.getQuantityToProduce(),
				referenceDate);

		for (ComponentRequirement requirement : requirements) {
			consumeRequirement(order, requirement, referenceDate, occurredAt, createdBy);
		}

		InventoryLot producedInventoryLot = InventoryLot.create(
				finishedProduct,
				producedLotNumber,
				request.manufactureDate(),
				request.expirationDate(),
				order.getQuantityToProduce(),
				request.producedUnitCost(),
				referenceDate);
		InventoryLot savedInventoryLot = inventoryLotRepository.saveAndFlush(producedInventoryLot);
		recordInventoryMovement(
				savedInventoryLot,
				InventoryMovementType.PRODUCTION_ENTRY,
				order.getQuantityToProduce(),
				order.getId(),
				"Production order output",
				occurredAt,
				createdBy);

		ProducedLot producedLot = ProducedLot.create(
				order,
				savedInventoryLot,
				order.getQuantityToProduce(),
				occurredAt);
		producedLotRepository.saveAndFlush(producedLot);

		order.complete(occurredAt);
		ProductionOrder completedOrder = productionOrderRepository.saveAndFlush(order);
		return toResultResponse(completedOrder);
	}

	private List<ComponentRequirement> prepareRequirements(
			List<ProductComposition> bom,
			BigDecimal quantityToProduce,
			LocalDate referenceDate) {
		List<ComponentRequirement> requirements = new ArrayList<>(bom.size());
		for (ProductComposition composition : bom) {
			Product component = composition.getComponentProduct();
			BigDecimal requiredQuantity = multiplyBomQuantity(
					composition.getQuantity(), quantityToProduce, component.getSku());
			BigDecimal availableQuantity = inventoryLotRepository
					.sumAvailableQuantity(component.getId(), referenceDate);
			if (availableQuantity == null) {
				availableQuantity = BigDecimal.ZERO;
			}
			if (availableQuantity.compareTo(requiredQuantity) < 0) {
				throw new InsufficientInventoryException(
						component.getId(), requiredQuantity, availableQuantity);
			}
			requirements.add(new ComponentRequirement(
					component,
					requiredQuantity,
					composition.getMeasurementUnit()));
		}
		return requirements;
	}

	private void consumeRequirement(
			ProductionOrder order,
			ComponentRequirement requirement,
			LocalDate referenceDate,
			Instant occurredAt,
			String createdBy) {
		BigDecimal remaining = requirement.requiredQuantity();
		while (remaining.signum() > 0) {
			List<InventoryLot> candidates = inventoryLotRepository
					.findAvailableLotsForFefo(
							requirement.componentProduct().getId(),
							referenceDate,
							PageRequest.of(0, FEFO_BATCH_SIZE));
			if (candidates.isEmpty()) {
				throw insufficientAfterConcurrentChange(requirement, referenceDate);
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
					throw insufficientAfterConcurrentChange(requirement, referenceDate);
				}

				InventoryLot consumedLot = inventoryLotRepository.findById(candidate.getId())
						.orElseThrow(() -> new InventoryLotNotFoundException(candidate.getId()));
				recordInventoryMovement(
						consumedLot,
						InventoryMovementType.PRODUCTION_CONSUMPTION,
						allocation,
						order.getId(),
						"Component consumed by production order",
						occurredAt,
						createdBy);
				ProductionConsumption consumption = ProductionConsumption.create(
						order,
						requirement.componentProduct(),
						consumedLot,
						allocation,
						requirement.measurementUnit(),
						occurredAt);
				productionConsumptionRepository.save(consumption);
				remaining = remaining.subtract(allocation);
			}
		}
	}

	private InventoryMovement recordInventoryMovement(
			InventoryLot lot,
			InventoryMovementType type,
			BigDecimal quantity,
			Long productionOrderId,
			String description,
			Instant occurredAt,
			String createdBy) {
		InventoryMovement movement = InventoryMovement.create(
				lot,
				type,
				quantity,
				lot.getAvailableQuantity(),
				PRODUCTION_ORDER_REFERENCE,
				productionOrderId,
				description,
				occurredAt,
				createdBy);
		return inventoryMovementRepository.save(movement);
	}

	private InsufficientInventoryException insufficientAfterConcurrentChange(
			ComponentRequirement requirement,
			LocalDate referenceDate) {
		BigDecimal latestAvailable = inventoryLotRepository.sumAvailableQuantity(
				requirement.componentProduct().getId(), referenceDate);
		return new InsufficientInventoryException(
				requirement.componentProduct().getId(),
				requirement.requiredQuantity(),
				latestAvailable == null ? BigDecimal.ZERO : latestAvailable);
	}

	private List<ProductComposition> requireBom(Product product) {
		List<ProductComposition> bom = productCompositionRepository
				.findAllByParentProductId(product.getId())
				.stream()
				.sorted(Comparator.comparing(
						composition -> composition.getComponentProduct().getId()))
				.toList();
		if (bom.isEmpty()) {
			throw new ProductWithoutBomException(product.getId());
		}
		return bom;
	}

	private Product requireProduct(Long id) {
		return productRepository.findById(id)
				.orElseThrow(() -> new ProductNotFoundException(id));
	}

	private ProductionOrder requireOrder(Long id) {
		return productionOrderRepository.findById(id)
				.orElseThrow(() -> new ProductionOrderNotFoundException(id));
	}

	private static void requireInProgress(ProductionOrder order) {
		if (order.getStatus() != ProductionOrderStatus.IN_PROGRESS) {
			throw new InvalidProductionOrderStateException(
					order.getId(), order.getStatus(), "completed");
		}
	}

	private static String validateCompletionRequest(CompleteProductionRequest request) {
		String lotNumber = InventoryLot.normalizeLotNumber(request.producedLotNumber());
		if (request.manufactureDate() != null && request.expirationDate() != null
				&& request.expirationDate().isBefore(request.manufactureDate())) {
			throw new InvalidProductionOrderException(
					"Produced lot expiration date cannot be before its manufacture date");
		}
		BigDecimal unitCost = request.producedUnitCost();
		if (unitCost == null || unitCost.signum() < 0) {
			throw new InvalidProductionOrderException(
					"Produced lot unit cost must be greater than or equal to zero");
		}
		int integerDigits = unitCost.precision() - unitCost.scale();
		if (integerDigits > COST_INTEGER_DIGITS || unitCost.scale() > COST_FRACTION_DIGITS) {
			throw new InvalidProductionOrderException(
					"Produced lot unit cost must fit NUMERIC(19, 4)");
		}
		return lotNumber;
	}

	private static Long parsePositiveLong(String value) {
		try {
			long parsed = Long.parseLong(value);
			return parsed > 0 ? parsed : null;
		} catch (NumberFormatException exception) {
			return null;
		}
	}

	private static BigDecimal multiplyBomQuantity(
			BigDecimal componentQuantity,
			BigDecimal quantityToProduce,
			String componentSku) {
		BigDecimal calculated = componentQuantity.multiply(quantityToProduce);
		BigDecimal required;
		try {
			required = calculated.setScale(QUANTITY_FRACTION_DIGITS, RoundingMode.UNNECESSARY);
		} catch (ArithmeticException exception) {
			throw new InvalidProductionOrderException(
					"Required quantity for component '%s' cannot fit NUMERIC(19, 6)"
							.formatted(componentSku));
		}
		int integerDigits = required.precision() - required.scale();
		if (required.signum() <= 0 || integerDigits > QUANTITY_INTEGER_DIGITS) {
			throw new InvalidProductionOrderException(
					"Required quantity for component '%s' cannot fit NUMERIC(19, 6)"
							.formatted(componentSku));
		}
		return required;
	}

	private ProductionResultResponse toResultResponse(ProductionOrder order) {
		List<ProductionConsumptionResponse> consumptions = productionConsumptionRepository
				.findAllByProductionOrderIdOrderByIdAsc(order.getId())
				.stream()
				.map(ProductionService::toConsumptionResponse)
				.toList();
		ProductionResultResponse.ProducedLotSummary producedLot = producedLotRepository
				.findByProductionOrderId(order.getId())
				.map(this::toProducedLotSummary)
				.orElse(null);
		return new ProductionResultResponse(toOrderResponse(order), producedLot, consumptions);
	}

	private static ProductionOrderResponse toOrderResponse(ProductionOrder order) {
		Product product = order.getProduct();
		return new ProductionOrderResponse(
				order.getId(),
				product.getId(),
				product.getName(),
				product.getSku(),
				order.getQuantityToProduce(),
				product.getDefaultMeasurementUnit(),
				order.getStatus(),
				order.getCreatedAt(),
				order.getStartedAt(),
				order.getCompletedAt(),
				order.getVersion());
	}

	private static ProductionConsumptionResponse toConsumptionResponse(
			ProductionConsumption consumption) {
		Product component = consumption.getComponentProduct();
		InventoryLot consumedLot = consumption.getConsumedLot();
		return new ProductionConsumptionResponse(
				consumption.getId(),
				component.getId(),
				component.getName(),
				component.getSku(),
				consumedLot.getId(),
				consumedLot.getLotNumber(),
				consumption.getConsumedQuantity(),
				consumption.getMeasurementUnit(),
				consumption.getCreatedAt());
	}

	private ProductionResultResponse.ProducedLotSummary toProducedLotSummary(ProducedLot producedLot) {
		InventoryLot inventoryLot = producedLot.getInventoryLot();
		InventoryLotStatus status = inventoryLot.getStatus() == InventoryLotStatus.AVAILABLE
				&& inventoryLot.isExpired(LocalDate.now(clock))
						? InventoryLotStatus.EXPIRED
						: inventoryLot.getStatus();
		return new ProductionResultResponse.ProducedLotSummary(
				producedLot.getId(),
				inventoryLot.getId(),
				inventoryLot.getLotNumber(),
				producedLot.getProducedQuantity(),
				producedLot.getProductionOrder().getProduct().getDefaultMeasurementUnit(),
				status,
				inventoryLot.getManufactureDate(),
				inventoryLot.getExpirationDate(),
				producedLot.getCreatedAt());
	}

	private record ComponentRequirement(
			Product componentProduct,
			BigDecimal requiredQuantity,
			com.haru.product.product.domain.MeasurementUnit measurementUnit) {
	}
}
