package com.haru.product.product.infrastructure.persistence;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.haru.product.product.domain.ProductComposition;

public interface ProductCompositionRepository extends JpaRepository<ProductComposition, Long> {

	boolean existsByParentProductIdAndComponentProductId(Long parentProductId, Long componentProductId);

	@EntityGraph(attributePaths = "componentProduct")
	List<ProductComposition> findAllByParentProductId(Long parentProductId);

	@EntityGraph(attributePaths = { "parentProduct", "componentProduct" })
	List<ProductComposition> findAllByParentProductIdIn(
			Collection<Long> parentProductIds,
			Pageable pageable);
}
