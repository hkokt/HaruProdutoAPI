package com.haru.product.product.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.junit.jupiter.api.Test;

class ProductSkuMigrationTests {

	private static final Path MIGRATION_FILE = Path.of(
			"src", "main", "resources", "db", "migration",
			"V5__automate_product_sku.sql");

	@Test
	void createsABoundedNonCyclingSkuSequence() throws IOException {
		String migration = normalizedMigration();

		assertThat(migration).contains(
				"create sequence product_sku_sequence as bigint",
				"minvalue 1",
				"maxvalue 9999999999",
				"start with 1",
				"no cycle");
	}

	@Test
	void startsAboveTheLargestExistingAutomaticSku() throws IOException {
		String migration = normalizedMigration();

		assertThat(migration).contains(
				"select max(substring(sku from '^prd-([0-9]{10})$')::bigint) from products",
				"coalesce(( select max",
				"), 0) + 1",
				"false );");
	}

	@Test
	void preventsChangingAPersistedSku() throws IOException {
		String migration = normalizedMigration();

		assertThat(migration).contains(
				"if new.sku is distinct from old.sku then",
				"constraint = 'ck_products_sku_immutable'",
				"create trigger trg_products_sku_immutable before update of sku on products",
				"execute function prevent_product_sku_update()");
	}

	private static String normalizedMigration() throws IOException {
		return Files.readString(MIGRATION_FILE)
				.toLowerCase(Locale.ROOT)
				.replaceAll("\\s+", " ")
				.trim();
	}
}
