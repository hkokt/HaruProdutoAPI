package com.haru.product.product.presentation;

import io.swagger.v3.oas.annotations.tags.Tag;

import com.haru.product.product.application.ProductService;
import com.haru.product.product.application.dto.AddProductComponentRequest;
import com.haru.product.product.application.dto.CreateProductRequest;
import com.haru.product.product.application.dto.ProductCompositionResponse;
import com.haru.product.product.application.dto.ProductCompositionTreeResponse;
import com.haru.product.product.application.dto.ProductResponse;
import com.haru.product.product.application.dto.UpdateProductComponentRequest;
import com.haru.product.product.application.dto.UpdateProductRequest;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Products", description = "Product catalog and bill of materials operations")
@RestController
@RequestMapping("/api/products")
public class ProductController {

	private final ProductService productService;

	public ProductController(ProductService productService) {
		this.productService = productService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ProductResponse create(@Valid @RequestBody CreateProductRequest request) {
		return productService.create(request);
	}

	@GetMapping("/{id}")
	public ProductResponse getById(@PathVariable Long id) {
		return productService.getById(id);
	}

	@PutMapping("/{id}")
	public ProductResponse update(@PathVariable Long id, @Valid @RequestBody UpdateProductRequest request) {
		return productService.update(id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		productService.delete(id);
	}

	@PostMapping("/{id}/components")
	@ResponseStatus(HttpStatus.CREATED)
	public ProductCompositionResponse addComponent(
			@PathVariable Long id,
			@Valid @RequestBody AddProductComponentRequest request) {
		return productService.addComponent(id, request);
	}

	@PutMapping("/{id}/components/{componentId}")
	public ProductCompositionResponse updateComponent(
			@PathVariable Long id,
			@PathVariable Long componentId,
			@Valid @RequestBody UpdateProductComponentRequest request) {
		return productService.updateComponent(id, componentId, request);
	}

	@DeleteMapping("/{id}/components/{componentId}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void removeComponent(@PathVariable Long id, @PathVariable Long componentId) {
		productService.removeComponent(id, componentId);
	}

	@GetMapping("/{id}/composition")
	public ProductCompositionTreeResponse getComposition(@PathVariable Long id) {
		return productService.getComposition(id);
	}
}
