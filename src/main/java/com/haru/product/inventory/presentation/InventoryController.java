package com.haru.product.inventory.presentation;

import io.swagger.v3.oas.annotations.tags.Tag;

import java.security.Principal;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.haru.product.inventory.application.InventoryService;
import com.haru.product.inventory.application.dto.AdjustInventoryRequest;
import com.haru.product.inventory.application.dto.ConsumeInventoryRequest;
import com.haru.product.inventory.application.dto.CreateInventoryLotRequest;
import com.haru.product.inventory.application.dto.InventoryAvailabilityResponse;
import com.haru.product.inventory.application.dto.InventoryConsumptionResponse;
import com.haru.product.inventory.application.dto.InventoryLotResponse;
import com.haru.product.inventory.application.dto.InventoryMovementResponse;

import jakarta.validation.Valid;

@Tag(name = "Inventory", description = "Inventory lots, availability, movements, and FEFO consumption")
@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

	private final InventoryService inventoryService;

	public InventoryController(InventoryService inventoryService) {
		this.inventoryService = inventoryService;
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

	@GetMapping("/products/{productId}/lots")
	public Page<InventoryLotResponse> getProductLots(
			@PathVariable Long productId,
			@PageableDefault(size = 50) Pageable pageable) {
		return inventoryService.getProductLots(productId, pageable);
	}

	@GetMapping("/products/{productId}/availability")
	public InventoryAvailabilityResponse getAvailability(@PathVariable Long productId) {
		return inventoryService.getAvailability(productId);
	}

	@GetMapping("/products/{productId}/movements")
	public Page<InventoryMovementResponse> getMovements(
			@PathVariable Long productId,
			@PageableDefault(size = 50) Pageable pageable) {
		return inventoryService.getProductMovements(productId, pageable);
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
