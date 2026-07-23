package com.haru.product.product.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class PostgreSqlProductSkuGeneratorTests {

	private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
	private final PostgreSqlProductSkuGenerator generator =
			new PostgreSqlProductSkuGenerator(jdbcTemplate);

	@Test
	void formatsTheNextSequenceValueAsAnAutomaticSku() {
		when(jdbcTemplate.queryForObject(
				"SELECT nextval('product_sku_sequence')",
				Long.class))
				.thenReturn(42L);

		assertThat(generator.nextSku()).isEqualTo("PRD-0000000042");
		verify(jdbcTemplate).queryForObject(
				"SELECT nextval('product_sku_sequence')",
				Long.class);
	}

	@Test
	void rejectsAMissingSequenceValue() {
		when(jdbcTemplate.queryForObject(
				"SELECT nextval('product_sku_sequence')",
				Long.class))
				.thenReturn(null);

		assertThatExceptionOfType(IllegalStateException.class)
				.isThrownBy(generator::nextSku)
				.withMessage("Product SKU sequence did not return a value");
	}
}
