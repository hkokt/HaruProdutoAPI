package com.haru.product.shared.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.junit.jupiter.api.Test;

class DatabaseHardeningMigrationTests {

	private static final Path MIGRATION_FILE = Path.of(
			"src", "main", "resources", "db", "migration",
			"V4__harden_database_invariants.sql");

	@Test
	void replacesThePreviousLotIndexWithThePostgresqlFefoOrder() throws IOException {
		String migration = normalizedMigration();

		assertThat(migration).contains(
				"drop index idx_inventory_lots_product_expiration_id",
				"create index idx_inventory_lots_fefo on inventory_lots "
						+ "( product_id, status, expiration_date asc nulls last, id asc )");
	}

	@Test
	void constrainsEveryPersistedEnumValue() throws IOException {
		String migration = normalizedMigration();

		assertThat(migration).contains(
				"constraint ck_products_product_type check (product_type in",
				"constraint ck_products_default_measurement_unit "
						+ "check (default_measurement_unit in",
				"constraint ck_product_compositions_measurement_unit "
						+ "check (measurement_unit in",
				"constraint ck_inventory_lots_status check (status in",
				"constraint ck_inventory_movements_type check (movement_type in",
				"constraint ck_production_orders_status check (status in",
				"constraint ck_production_consumptions_measurement_unit "
						+ "check (measurement_unit in");

		assertThat(migration).contains(
				"'raw_material'",
				"'service'",
				"'milliliter'",
				"'blocked'",
				"'production_consumption'",
				"'completed'");
	}

	@Test
	void constrainsProductionOrderLifecycleTimestamps() throws IOException {
		String migration = normalizedMigration();

		assertThat(migration).contains(
				"constraint ck_production_orders_lifecycle check",
				"status = 'created' and started_at is null and completed_at is null",
				"status = 'in_progress' and started_at is not null and completed_at is null",
				"status = 'completed' and started_at is not null and completed_at is not null",
				"status = 'cancelled' and completed_at is null");
	}

	@Test
	void makesInventoryMovementsAppendOnlyAtTheDatabaseBoundary() throws IOException {
		String migration = normalizedMigration();

		assertThat(migration).contains(
				"create function reject_inventory_movement_mutation() returns trigger",
				"raise exception 'inventory_movements is append-only' "
						+ "using errcode = '55000'",
				"create trigger trg_inventory_movements_append_only "
						+ "before update or delete on inventory_movements",
				"execute function reject_inventory_movement_mutation()");
	}

	private static String normalizedMigration() throws IOException {
		return Files.readString(MIGRATION_FILE)
				.toLowerCase(Locale.ROOT)
				.replaceAll("\\s+", " ")
				.trim();
	}
}
