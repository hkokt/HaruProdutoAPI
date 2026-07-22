package com.haru.product.inventory.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.haru.product.inventory.domain.InventoryLot;
import com.haru.product.inventory.domain.InventoryLotStatus;

public interface InventoryLotRepository extends JpaRepository<InventoryLot, Long> {

	Optional<InventoryLot> findByProductIdAndLotNumber(Long productId, String lotNumber);

	boolean existsByProductIdAndLotNumber(Long productId, String lotNumber);

	List<InventoryLot> findAllByProductIdOrderByIdAsc(Long productId);

	@EntityGraph(attributePaths = "product")
	Page<InventoryLot> findAllByProductIdOrderByIdAsc(Long productId, Pageable pageable);

	@Query("""
			select lot
			  from InventoryLot lot
			 where lot.product.id = :productId
			   and lot.availableQuantity > 0
			   and lot.status = com.haru.product.inventory.domain.InventoryLotStatus.AVAILABLE
			   and (lot.expirationDate is null or lot.expirationDate >= :referenceDate)
			 order by lot.expirationDate asc nulls last,
			          lot.id asc
			""")
	@EntityGraph(attributePaths = "product")
	List<InventoryLot> findAvailableLotsForFefo(
			@Param("productId") Long productId,
			@Param("referenceDate") LocalDate referenceDate,
			Pageable pageable);

	@Query("""
			select coalesce(sum(lot.availableQuantity), 0)
			  from InventoryLot lot
			 where lot.product.id = :productId
			   and lot.availableQuantity > 0
			   and lot.status = com.haru.product.inventory.domain.InventoryLotStatus.AVAILABLE
			   and (lot.expirationDate is null or lot.expirationDate >= :referenceDate)
			""")
	BigDecimal sumAvailableQuantity(
			@Param("productId") Long productId,
			@Param("referenceDate") LocalDate referenceDate);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update InventoryLot lot
			   set lot.availableQuantity = lot.availableQuantity - :quantity,
			       lot.status = case
			           when lot.availableQuantity = :quantity then :depletedStatus
			           else :availableStatus
			       end,
			       lot.updatedAt = :updatedAt,
			       lot.version = lot.version + 1
			 where lot.id = :lotId
			   and :quantity > 0
			   and lot.availableQuantity >= :quantity
			   and lot.status = :availableStatus
			   and (lot.expirationDate is null or lot.expirationDate >= :referenceDate)
			""")
	int decreaseForConsumption(
			@Param("lotId") Long lotId,
			@Param("quantity") BigDecimal quantity,
			@Param("referenceDate") LocalDate referenceDate,
			@Param("updatedAt") Instant updatedAt,
			@Param("availableStatus") InventoryLotStatus availableStatus,
			@Param("depletedStatus") InventoryLotStatus depletedStatus);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update InventoryLot lot
			   set lot.availableQuantity = lot.availableQuantity - :quantity,
			       lot.status = case
			           when lot.availableQuantity = :quantity then :depletedStatus
			           else lot.status
			       end,
			       lot.updatedAt = :updatedAt,
			       lot.version = lot.version + 1
			 where lot.id = :lotId
			   and :quantity > 0
			   and lot.availableQuantity >= :quantity
			""")
	int decreaseForAdjustment(
			@Param("lotId") Long lotId,
			@Param("quantity") BigDecimal quantity,
			@Param("updatedAt") Instant updatedAt,
			@Param("depletedStatus") InventoryLotStatus depletedStatus);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update InventoryLot lot
			   set lot.availableQuantity = lot.availableQuantity + :quantity,
			       lot.status = case
			           when lot.status = :blockedStatus then :blockedStatus
			           when lot.expirationDate is not null and lot.expirationDate < :referenceDate
			               then :expiredStatus
			           else :availableStatus
			       end,
			       lot.updatedAt = :updatedAt,
			       lot.version = lot.version + 1
			 where lot.id = :lotId
			   and :quantity > 0
			""")
	int increaseForAdjustment(
			@Param("lotId") Long lotId,
			@Param("quantity") BigDecimal quantity,
			@Param("referenceDate") LocalDate referenceDate,
			@Param("updatedAt") Instant updatedAt,
			@Param("availableStatus") InventoryLotStatus availableStatus,
			@Param("expiredStatus") InventoryLotStatus expiredStatus,
			@Param("blockedStatus") InventoryLotStatus blockedStatus);
}
