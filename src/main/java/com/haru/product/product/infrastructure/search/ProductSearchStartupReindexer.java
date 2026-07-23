package com.haru.product.product.infrastructure.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.haru.product.product.application.ProductSearchMaintenanceService;
import com.haru.product.product.application.dto.ProductSearchReindexResponse;

@Component
@ConditionalOnProperty(
		name = "haru.search.elasticsearch.reindex-on-startup",
		havingValue = "true")
public class ProductSearchStartupReindexer implements ApplicationRunner {

	private static final Logger LOGGER = LoggerFactory.getLogger(ProductSearchStartupReindexer.class);

	private final ProductSearchMaintenanceService maintenanceService;

	public ProductSearchStartupReindexer(ProductSearchMaintenanceService maintenanceService) {
		this.maintenanceService = maintenanceService;
	}

	@Override
	public void run(ApplicationArguments arguments) {
		try {
			ProductSearchReindexResponse result = maintenanceService.reindex();
			LOGGER.info(
					"Startup product reindex completed: scanned={}, indexed={}, failed={}",
					result.scanned(),
					result.indexed(),
					result.failed());
		}
		catch (RuntimeException exception) {
			LOGGER.error("Startup product reindex could not be completed", exception);
		}
	}
}
