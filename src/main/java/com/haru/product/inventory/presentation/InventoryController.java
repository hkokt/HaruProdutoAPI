package com.haru.product.inventory.presentation;

import io.swagger.v3.oas.annotations.tags.Tag;

import java.security.Principal;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.haru.product.inventory.application.InventoryService;
import com.haru.product.inventory.application.InventorySearchService;
import com.haru.product.inventory.application.dto.AdjustInventoryRequest;
import com.haru.product.inventory.application.dto.ConsumeInventoryRequest;
import com.haru.product.inventory.application.dto.CreateInventoryLotRequest;
import com.haru.product.inventory.application.dto.InventoryAvailabilityResponse;
import com.haru.product.inventory.application.dto.InventoryConsumptionResponse;
import com.haru.product.inventory.application.dto.InventoryLotResponse;
import com.haru.product.inventory.application.dto.InventoryMovementResponse;
import com.haru.product.inventory.application.dto.InventoryProductSummaryResponse;
import com.haru.product.shared.pagination.OffsetPageResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@Tag(name = "Inventory", description = "Inventory lots, availability, movements, and FEFO consumption")
@Validated
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

	private final InventoryService inventoryService;
	private final InventorySearchService inventorySearchService;

	public InventoryController(
			InventoryService inventoryService,
			InventorySearchService inventorySearchService) {
		this.inventoryService = inventoryService;
		this.inventorySearchService = inventorySearchService;
	}

	@PostMapping("/lots")
	@ResponseStatus(HttpStatus.CREATED)
	public InventoryLotResponse createLot(
			@Valid @RequestBody CreateInventoryLotRequest request,
			Principal principal) {
		return inventoryService.createLot(request, principal.getName());
	}

	@GetMapping("/lots/{id}")
	public InventoryLotResponse getLot(@PathVariable Long id) {
		return inventoryService.getLot(id);
	}

	@GetMapping("/products/search")
	public OffsetPageResponse<InventoryProductSummaryResponse> searchProducts(
			@RequestParam(defaultValue = "") @Size(max = 150) String q,
			@RequestParam(defaultValue = "0") @PositiveOrZero @Max(2_147_483_647L) long offset,
			@RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
		return inventorySearchService.search(q, offset, limit);
	}

	@GetMapping("/products/{productId}/lots")
	public OffsetPageResponse<InventoryLotResponse> getProductLots(
			@PathVariable Long productId,
			@RequestParam(defaultValue = "0") @PositiveOrZero @Max(2_147_483_647L) long offset,
			@RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
		return inventoryService.getProductLots(productId, offset, limit);
	}

	@GetMapping("/products/{productId}/availability")
	public InventoryAvailabilityResponse getAvailability(@PathVariable Long productId) {
		return inventoryService.getAvailability(productId);
	}

	@GetMapping("/products/{productId}/movements")
	public OffsetPageResponse<InventoryMovementResponse> getMovements(
			@PathVariable Long productId,
			@RequestParam(defaultValue = "0") @PositiveOrZero @Max(2_147_483_647L) long offset,
			@RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
		return inventoryService.getProductMovements(productId, offset, limit);
	}

	@PostMapping("/lots/{lotId}/adjustments/in")
	public InventoryLotResponse adjustIn(
			@PathVariable Long lotId,
			@Valid @RequestBody AdjustInventoryRequest request,
			Principal principal) {
		return inventoryService.adjustIn(lotId, request, principal.getName());
	}

	@PostMapping("/lots/{lotId}/adjustments/out")
	public InventoryLotResponse adjustOut(
			@PathVariable Long lotId,
			@Valid @RequestBody AdjustInventoryRequest request,
			Principal principal) {
		return inventoryService.adjustOut(lotId, request, principal.getName());
	}

	@PostMapping("/products/{productId}/consumption")
	public InventoryConsumptionResponse consume(
			@PathVariable Long productId,
			@Valid @RequestBody ConsumeInventoryRequest request,
			Principal principal) {
		return inventoryService.consume(productId, request, principal.getName());
	}
}
