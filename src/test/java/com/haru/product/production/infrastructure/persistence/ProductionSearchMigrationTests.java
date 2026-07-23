package com.haru.product.production.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.junit.jupiter.api.Test;

class ProductionSearchMigrationTests {

	private static final Path MIGRATION_FILE = Path.of(
			"src", "main", "resources", "db", "migration",
			"V6__index_production_order_search.sql");

	@Test
	void indexesGlobalAndStatusFilteredProductionOrderSearches() throws IOException {
		String migration = Files.readString(MIGRATION_FILE)
				.toLowerCase(Locale.ROOT)
				.replaceAll("\\s+", " ")
				.trim();

		assertThat(migration).contains(
				"create index idx_production_orders_created_id "
						+ "on production_orders (created_at desc, id desc)",
				"create index idx_production_orders_status_created_id "
						+ "on production_orders (status, created_at desc, id desc)");
	}
}
