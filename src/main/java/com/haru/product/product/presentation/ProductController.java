package com.haru.product.product.presentation;

import io.swagger.v3.oas.annotations.tags.Tag;

import com.haru.product.product.application.ProductCommandFacade;
import com.haru.product.product.application.ProductSearchService;
import com.haru.product.product.application.ProductService;
import com.haru.product.product.application.dto.AddProductComponentRequest;
import com.haru.product.product.application.dto.CreateProductRequest;
import com.haru.product.product.application.dto.ProductCompositionResponse;
import com.haru.product.product.application.dto.ProductCompositionTreeResponse;
import com.haru.product.product.application.dto.ProductResponse;
import com.haru.product.product.application.dto.ProductSearchResultResponse;
import com.haru.product.product.application.dto.UpdateProductComponentRequest;
import com.haru.product.product.application.dto.UpdateProductRequest;
import com.haru.product.shared.pagination.OffsetPageResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Products", description = "Product catalog and bill of materials operations")
@Validated
@RestController
@RequestMapping("/api/products")
public class ProductController {

	private final ProductService productService;
	private final ProductCommandFacade productCommandFacade;
	private final ProductSearchService productSearchService;

	public ProductController(
			ProductService productService,
			ProductCommandFacade productCommandFacade,
			ProductSearchService productSearchService) {
		this.productService = productService;
		this.productCommandFacade = productCommandFacade;
		this.productSearchService = productSearchService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ProductResponse create(@Valid @RequestBody CreateProductRequest request) {
		return productCommandFacade.create(request);
	}

	@GetMapping("/search")
	public OffsetPageResponse<ProductSearchResultResponse> search(
			@RequestParam(defaultValue = "") @Size(max = 150) String q,
			@RequestParam(defaultValue = "0") @PositiveOrZero long offset,
			@RequestParam(defaultValue = "20") @Min(1) @Max(50) int limit) {
		return productSearchService.search(q, offset, limit);
	}

	@GetMapping("/{id}")
	public ProductResponse getById(@PathVariable Long id) {
		return productService.getById(id);
	}

	@PutMapping("/{id}")
	public ProductResponse update(@PathVariable Long id, @Valid @RequestBody UpdateProductRequest request) {
		return productCommandFacade.update(id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		productCommandFacade.delete(id);
	}

	@PostMapping("/{id}/components")
	@ResponseStatus(HttpStatus.CREATED)
	public ProductCompositionResponse addComponent(
			@PathVariable Long id,
			@Valid @RequestBody AddProductComponentRequest request) {
		return productCommandFacade.addComponent(id, request);
	}

	@PutMapping("/{id}/components/{componentId}")
	public ProductCompositionResponse updateComponent(
			@PathVariable Long id,
			@PathVariable Long componentId,
			@Valid @RequestBody UpdateProductComponentRequest request) {
		return productCommandFacade.updateComponent(id, componentId, request);
	}

	@DeleteMapping("/{id}/components/{componentId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void removeComponent(@PathVariable Long id, @PathVariable Long componentId) {
		productCommandFacade.removeComponent(id, componentId);
	}

	@GetMapping("/{id}/composition")
	public ProductCompositionTreeResponse getComposition(@PathVariable Long id) {
		return productService.getComposition(id);
	}
}
