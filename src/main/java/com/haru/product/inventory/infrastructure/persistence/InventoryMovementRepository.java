package com.haru.product.inventory.infrastructure.persistence;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.haru.product.inventory.domain.InventoryMovement;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {

	@EntityGraph(attributePaths = { "inventoryLot", "inventoryLot.product" })
	Page<InventoryMovement> findAllByInventoryLotProductIdOrderByOccurredAtDescIdDesc(
			Long productId,
			Pageable pageable);

	List<InventoryMovement> findAllByInventoryLotIdOrderByOccurredAtAscIdAsc(Long inventoryLotId);

	List<InventoryMovement> findAllByReferenceTypeAndReferenceIdOrderByOccurredAtAscIdAsc(
			String referenceType,
			Long referenceId);
}
