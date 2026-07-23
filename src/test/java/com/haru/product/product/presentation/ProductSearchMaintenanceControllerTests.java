package com.haru.product.product.presentation;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.haru.product.product.application.ProductSearchMaintenanceService;
import com.haru.product.product.application.dto.ProductSearchReconcileResponse;
import com.haru.product.product.application.dto.ProductSearchReindexResponse;
import com.haru.product.product.application.dto.ProductSearchValidationResponse;

class ProductSearchMaintenanceControllerTests {

	private ProductSearchMaintenanceService maintenanceService;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		maintenanceService = mock(ProductSearchMaintenanceService.class);
		mockMvc = MockMvcBuilders
				.standaloneSetup(new ProductSearchMaintenanceController(maintenanceService))
				.build();
	}

	@Test
	void reindexesProducts() throws Exception {
		when(maintenanceService.reindex())
				.thenReturn(new ProductSearchReindexResponse(4, 3, 1, List.of(9L)));

		mockMvc.perform(post("/admin/search/products/reindex"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.scanned").value(4))
				.andExpect(jsonPath("$.indexed").value(3))
				.andExpect(jsonPath("$.failed").value(1))
				.andExpect(jsonPath("$.failedProductIds[0]").value(9));

		verify(maintenanceService).reindex();
	}

	@Test
	void validatesTheProductIndex() throws Exception {
		when(maintenanceService.validate())
				.thenReturn(new ProductSearchValidationResponse(
						4,
						5,
						2,
						1,
						1,
						1,
						List.of(8L),
						List.of(9L),
						false));

		mockMvc.perform(get("/admin/search/products/validation"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.databaseProductCount").value(4))
				.andExpect(jsonPath("$.liveIndexDocumentCount").value(5))
				.andExpect(jsonPath("$.matchingCount").value(2))
				.andExpect(jsonPath("$.missingCount").value(1))
				.andExpect(jsonPath("$.staleCount").value(1))
				.andExpect(jsonPath("$.orphanCount").value(1))
				.andExpect(jsonPath("$.missingProductIds[0]").value(8))
				.andExpect(jsonPath("$.staleProductIds[0]").value(9))
				.andExpect(jsonPath("$.consistent").value(false));

		verify(maintenanceService).validate();
	}

	@Test
	void reconcilesOrphanDocuments() throws Exception {
		when(maintenanceService.reconcile())
				.thenReturn(new ProductSearchReconcileResponse(5, 2, 1, List.of(9L)));

		mockMvc.perform(post("/admin/search/products/reconcile"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.scanned").value(5))
				.andExpect(jsonPath("$.tombstoned").value(2))
				.andExpect(jsonPath("$.failed").value(1))
				.andExpect(jsonPath("$.failedProductIds[0]").value(9));

		verify(maintenanceService).reconcile();
	}
}
