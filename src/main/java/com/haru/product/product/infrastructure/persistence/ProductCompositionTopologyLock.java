package com.haru.product.product.infrastructure.persistence;

import org.springframework.stereotype.Repository;

import jakarta.persistence.EntityManager;

@Repository
public class ProductCompositionTopologyLock {

	private static final long BOM_TOPOLOGY_LOCK_KEY = 72_821_170_079L;

	private final EntityManager entityManager;

	public ProductCompositionTopologyLock(EntityManager entityManager) {
		this.entityManager = entityManager;
	}

	public void acquire() {
		entityManager.createNativeQuery(
				"SELECT pg_advisory_xact_lock(" + BOM_TOPOLOGY_LOCK_KEY + ")")
				.getSingleResult();
	}
}
