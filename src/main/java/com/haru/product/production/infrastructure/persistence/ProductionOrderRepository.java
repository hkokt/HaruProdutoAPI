package com.haru.product.production.infrastructure.persistence;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.haru.product.production.domain.ProductionOrder;
import com.haru.product.production.domain.ProductionOrderStatus;

public interface ProductionOrderRepository extends JpaRepository<ProductionOrder, Long> {

	@Override
	@EntityGraph(attributePaths = "product")
	Optional<ProductionOrder> findById(Long id);

	@Query(
			value = """
					select productionOrder
					  from ProductionOrder productionOrder
					 where (:status is null or productionOrder.status = :status)
					   and (:query = ''
					        or lower(productionOrder.product.name) like lower(concat('%', :query, '%'))
					        or lower(productionOrder.product.sku) like lower(concat('%', :query, '%'))
					        or (:numericQuery is not null
					            and (productionOrder.id = :numericQuery
					                 or productionOrder.product.id = :numericQuery)))
					 order by productionOrder.createdAt desc, productionOrder.id desc
					""",
			countQuery = """
					select count(productionOrder)
					  from ProductionOrder productionOrder
					 where (:status is null or productionOrder.status = :status)
					   and (:query = ''
					        or lower(productionOrder.product.name) like lower(concat('%', :query, '%'))
					        or lower(productionOrder.product.sku) like lower(concat('%', :query, '%'))
					        or (:numericQuery is not null
					            and (productionOrder.id = :numericQuery
					                 or productionOrder.product.id = :numericQuery)))
					""")
	@EntityGraph(attributePaths = "product")
	Page<ProductionOrder> search(
			@Param("query") String query,
			@Param("numericQuery") Long numericQuery,
			@Param("status") ProductionOrderStatus status,
			Pageable pageable);
}
