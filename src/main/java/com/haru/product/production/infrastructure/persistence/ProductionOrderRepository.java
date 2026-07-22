package com.haru.product.production.infrastructure.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.haru.product.production.domain.ProductionOrder;

public interface ProductionOrderRepository extends JpaRepository<ProductionOrder, Long> {

	@Override
	@EntityGraph(attributePaths = "product")
	Optional<ProductionOrder> findById(Long id);
}
