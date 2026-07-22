package com.haru.product.production.infrastructure.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.haru.product.production.domain.ProductionConsumption;

public interface ProductionConsumptionRepository extends JpaRepository<ProductionConsumption, Long> {

	@EntityGraph(attributePaths = { "componentProduct", "consumedLot" })
	List<ProductionConsumption> findAllByProductionOrderIdOrderByIdAsc(Long productionOrderId);
}
