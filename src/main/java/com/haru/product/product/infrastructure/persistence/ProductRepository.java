package com.haru.product.product.infrastructure.persistence;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.haru.product.product.domain.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {

	List<Product> findAllByIdGreaterThanOrderByIdAsc(Long id, Pageable pageable);

	@Query("""
			select case when count(product) > 0 then true else false end
			  from Product product
			 where lower(product.sku) = lower(:sku)
			""")
	boolean existsBySkuIgnoreCase(@Param("sku") String sku);

	@Query("""
			select case when count(product) > 0 then true else false end
			  from Product product
			 where lower(product.sku) = lower(:sku)
			   and product.id <> :id
			""")
	boolean existsBySkuIgnoreCaseAndIdNot(
			@Param("sku") String sku,
			@Param("id") Long id);
}
