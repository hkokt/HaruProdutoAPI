package com.haru.product.product.infrastructure.persistence;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.haru.product.product.domain.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {

	List<Product> findAllByIdGreaterThanOrderByIdAsc(Long id, Pageable pageable);
}
