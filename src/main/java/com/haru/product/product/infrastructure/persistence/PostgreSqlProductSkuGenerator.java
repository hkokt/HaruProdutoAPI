package com.haru.product.product.infrastructure.persistence;

import java.util.Locale;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PostgreSqlProductSkuGenerator {

	private static final String NEXT_SEQUENCE_VALUE_SQL =
			"SELECT nextval('product_sku_sequence')";
	private static final String SKU_FORMAT = "PRD-%010d";

	private final JdbcTemplate jdbcTemplate;

	public PostgreSqlProductSkuGenerator(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public String nextSku() {
		Long sequenceValue = jdbcTemplate.queryForObject(
				NEXT_SEQUENCE_VALUE_SQL,
				Long.class);
		if (sequenceValue == null) {
			throw new IllegalStateException("Product SKU sequence did not return a value");
		}
		return String.format(Locale.ROOT, SKU_FORMAT, sequenceValue);
	}
}
