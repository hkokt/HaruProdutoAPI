package com.haru.product.product.application.search;

import java.util.Objects;

public record ProductSearchIndexEntry(
		Long id,
		long databaseVersion) {

	public ProductSearchIndexEntry {
		Objects.requireNonNull(id, "Product search index entry ID is required");
		if (id <= 0) {
			throw new IllegalArgumentException("Product search index entry ID must be positive");
		}
		if (databaseVersion < 0) {
			throw new IllegalArgumentException("Product search database version cannot be negative");
		}
	}
}
