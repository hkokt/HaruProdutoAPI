package com.haru.product.product.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.haru.product.product.application.dto.AddProductComponentRequest;
import com.haru.product.product.application.dto.CreateProductRequest;
import com.haru.product.product.application.dto.ProductCompositionResponse;
import com.haru.product.product.application.dto.ProductDeletionResult;
import com.haru.product.product.application.dto.ProductResponse;
import com.haru.product.product.application.dto.UpdateProductComponentRequest;
import com.haru.product.product.application.dto.UpdateProductRequest;
import com.haru.product.product.application.search.ProductSearchDocument;
import com.haru.product.product.application.search.ProductSearchGateway;

@Service
@Transactional(propagation = Propagation.NEVER)
public class ProductCommandFacade {

	private static final Logger LOGGER = LoggerFactory.getLogger(ProductCommandFacade.class);

	private final ProductService productService;
	private final ProductSearchGateway productSearchGateway;

	public ProductCommandFacade(ProductService productService, ProductSearchGateway productSearchGateway) {
		this.productService = productService;
		this.productSearchGateway = productSearchGateway;
	}

	public ProductResponse create(CreateProductRequest request) {
		ProductResponse product = productService.create(request);
		putAfterCommit(ProductSearchDocument.from(product));
		return product;
	}

	public ProductResponse update(Long id, UpdateProductRequest request) {
		ProductResponse product = productService.update(id, request);
		putAfterCommit(ProductSearchDocument.from(product));
		return product;
	}

	public void delete(Long id) {
		ProductDeletionResult deletion = productService.delete(id);
		putAfterCommit(ProductSearchDocument.tombstone(deletion));
	}

	public ProductCompositionResponse addComponent(Long productId, AddProductComponentRequest request) {
		ProductCompositionResponse composition = productService.addComponent(productId, request);
		synchronizeCurrentProductAfterCommit(productId);
		return composition;
	}

	public ProductCompositionResponse updateComponent(
			Long productId,
			Long componentId,
			UpdateProductComponentRequest request) {
		ProductCompositionResponse composition = productService.updateComponent(
				productId,
				componentId,
				request);
		synchronizeCurrentProductAfterCommit(productId);
		return composition;
	}

	public void removeComponent(Long productId, Long componentId) {
		productService.removeComponent(productId, componentId);
		synchronizeCurrentProductAfterCommit(productId);
	}

	private void putAfterCommit(ProductSearchDocument document) {
		try {
			productSearchGateway.put(document);
		}
		catch (RuntimeException exception) {
			LOGGER.error(
					"Product {} was committed to PostgreSQL but could not be synchronized to the search index",
					document.id(),
					exception);
		}
	}

	private void synchronizeCurrentProductAfterCommit(Long productId) {
		try {
			ProductResponse product = productService.getById(productId);
			productSearchGateway.put(ProductSearchDocument.from(product));
		}
		catch (RuntimeException exception) {
			LOGGER.error(
					"Product {} composition was committed to PostgreSQL but its search document could not be synchronized",
					productId,
					exception);
		}
	}
}
