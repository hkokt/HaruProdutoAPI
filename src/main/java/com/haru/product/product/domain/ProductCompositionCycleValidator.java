package com.haru.product.product.domain;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.haru.product.product.domain.exception.ProductCompositionCycleException;

@Component
public class ProductCompositionCycleValidator {

	public void validate(Product parentProduct, Product componentProduct) {
		if (parentProduct == null) {
			throw new IllegalArgumentException("Parent product is required");
		}
		if (componentProduct == null) {
			throw new IllegalArgumentException("Component product is required");
		}

		Set<Long> visitedIds = new HashSet<>();
		Set<Product> visitedInstances = Collections.newSetFromMap(new IdentityHashMap<>());
		Deque<Product> pending = new ArrayDeque<>();
		pending.addLast(componentProduct);

		while (!pending.isEmpty()) {
			Product currentProduct = pending.removeLast();
			if (sameProduct(currentProduct, parentProduct)) {
				throw new ProductCompositionCycleException(
						parentProduct.getSku(),
						componentProduct.getSku());
			}
			if (!markVisited(currentProduct, visitedIds, visitedInstances)) {
				continue;
			}

			for (ProductComposition composition : currentProduct.getComponents()) {
				pending.addLast(composition.getComponentProduct());
			}
		}
	}

	private boolean markVisited(
			Product product,
			Set<Long> visitedIds,
			Set<Product> visitedInstances) {
		if (product.getId() != null) {
			return visitedIds.add(product.getId());
		}
		return visitedInstances.add(product);
	}

	private boolean sameProduct(Product first, Product second) {
		return first == second
				|| first.getId() != null && first.getId().equals(second.getId());
	}
}
