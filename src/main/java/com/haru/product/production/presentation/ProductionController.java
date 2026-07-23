package com.haru.product.production.presentation;

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

import com.haru.product.production.application.ProductionService;
import com.haru.product.production.application.dto.CompleteProductionRequest;
import com.haru.product.production.application.dto.CreateProductionOrderRequest;
import com.haru.product.production.application.dto.ProductionOrderResponse;
import com.haru.product.production.application.dto.ProductionResultResponse;
import com.haru.product.production.domain.ProductionOrderStatus;
import com.haru.product.shared.pagination.OffsetPageResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

@Tag(name = "Production Orders", description = "Production-order lifecycle and traceability")
@Validated
@RestController
@RequestMapping("/api/production-orders")
public class ProductionController {

	private final ProductionService productionService;

	public ProductionController(ProductionService productionService) {
		this.productionService = productionService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ProductionOrderResponse create(
			@Valid @RequestBody CreateProductionOrderRequest request) {
		return productionService.create(request);
	}

	@GetMapping("/search")
	public OffsetPageResponse<ProductionOrderResponse> search(
			@RequestParam(defaultValue = "") @Size(max = 150) String q,
			@RequestParam(required = false) ProductionOrderStatus status,
			@RequestParam(defaultValue = "0") @PositiveOrZero @Max(2_147_483_647L) long offset,
			@RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
		return productionService.search(q, status, offset, limit);
	}

	@GetMapping("/{id}")
	public ProductionResultResponse getById(@PathVariable Long id) {
		return productionService.getById(id);
	}

	@PostMapping("/{id}/start")
	public ProductionOrderResponse start(@PathVariable Long id) {
		return productionService.start(id);
	}

	@PostMapping("/{id}/complete")
	public ProductionResultResponse complete(
			@PathVariable Long id,
			@Valid @RequestBody CompleteProductionRequest request,
			Principal principal) {
		return productionService.complete(id, request, principal.getName());
	}

	@PostMapping("/{id}/cancel")
	public ProductionOrderResponse cancel(@PathVariable Long id) {
		return productionService.cancel(id);
	}
}
