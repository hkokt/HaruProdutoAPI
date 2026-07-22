package com.haru.product.production.infrastructure.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.haru.product.production.domain.ProducedLot;

public interface ProducedLotRepository extends JpaRepository<ProducedLot, Long> {

	@EntityGraph(attributePaths = {
			"inventoryLot",
			"productionOrder",
			"productionOrder.product"
	})
	Optional<ProducedLot> findByProductionOrderId(Long productionOrderId);

	boolean existsByProductionOrderId(Long productionOrderId);

	boolean existsByInventoryLotId(Long inventoryLotId);
}
