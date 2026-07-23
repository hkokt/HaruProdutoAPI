package com.haru.product.product.application.dto;

import java.time.Instant;

public record ProductDeletionResult(
		Long id,
		long databaseVersion,
		Instant deletedAt) {
}
