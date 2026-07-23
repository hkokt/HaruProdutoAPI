package com.haru.product.product.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.haru.product.product.application.ProductCommandFacade;
import com.haru.product.product.application.ProductSearchService;
import com.haru.product.product.application.ProductService;
import com.haru.product.product.application.dto.AddProductComponentRequest;
import com.haru.product.product.application.dto.CreateProductRequest;
import com.haru.product.product.application.dto.ProductCompositionResponse;
import com.haru.product.product.application.dto.ProductCompositionTreeResponse;
import com.haru.product.product.application.dto.ProductResponse;
import com.haru.product.product.application.dto.ProductSearchResultResponse;
import com.haru.product.product.application.dto.UpdateProductComponentRequest;
import com.haru.product.product.application.dto.UpdateProductRequest;
import com.haru.product.product.domain.MeasurementUnit;
import com.haru.product.product.domain.Product;
import com.haru.product.product.domain.ProductType;
import com.haru.product.product.domain.exception.ProductNotFoundException;
import com.haru.product.product.application.exception.ProductSearchUnavailableException;
import com.haru.product.product.application.exception.InvalidProductSearchRequestException;
import com.haru.product.shared.exception.ApiExceptionHandler;
import com.haru.product.shared.pagination.OffsetPageResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ProductControllerTests {

	private ProductService productService;
	private ProductCommandFacade productCommandFacade;
	private ProductSearchService productSearchService;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		productService = mock(ProductService.class);
		productCommandFacade = mock(ProductCommandFacade.class);
		productSearchService = mock(ProductSearchService.class);
		mockMvc = MockMvcBuilders
				.standaloneSetup(new ProductController(
						productService,
						productCommandFacade,
						productSearchService))
				.setControllerAdvice(new ApiExceptionHandler())
				.build();
	}

	@Test
	void createsProduct() throws Exception {
		when(productCommandFacade.create(any(CreateProductRequest.class))).thenReturn(productResponse());

		mockMvc.perform(post("/api/products")
					.contentType(MediaType.APPLICATION_JSON)
					.content(createProductJson()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(1))
				.andExpect(jsonPath("$.sku").value("PERF-SAKURA-100ML"))
				.andExpect(jsonPath("$.components").isArray());

		verify(productCommandFacade).create(any(CreateProductRequest.class));
	}

	@Test
	void searchesProductsByNameIdOrSku() throws Exception {
		when(productSearchService.search("sakura", 15, 20))
				.thenReturn(new OffsetPageResponse<>(
						List.of(new ProductSearchResultResponse(
								1L,
								"Sakura Perfume 100 ml",
								"PERF-SAKURA-100ML",
								ProductType.FINISHED_PRODUCT,
								MeasurementUnit.UNIT,
								true,
								12.5)),
						15,
						20,
						36,
						true,
						true));

		mockMvc.perform(get("/api/products/search")
					.param("q", "sakura")
					.param("offset", "15"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].id").value(1))
				.andExpect(jsonPath("$.content[0].name").value("Sakura Perfume 100 ml"))
				.andExpect(jsonPath("$.content[0].sku").value("PERF-SAKURA-100ML"))
				.andExpect(jsonPath("$.content[0].score").value(12.5))
				.andExpect(jsonPath("$.offset").value(15))
				.andExpect(jsonPath("$.limit").value(20))
				.andExpect(jsonPath("$.totalElements").value(36))
				.andExpect(jsonPath("$.hasPrevious").value(true))
				.andExpect(jsonPath("$.hasNext").value(true));

		verify(productSearchService).search("sakura", 15, 20);
	}

	@Test
	void reportsWhenProductSearchIsUnavailable() throws Exception {
		when(productSearchService.search("sakura", 0, 20))
				.thenThrow(new ProductSearchUnavailableException("internal Elasticsearch failure"));

		mockMvc.perform(get("/api/products/search").param("q", "sakura"))
				.andExpect(status().isServiceUnavailable())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("PRODUCT_SEARCH_UNAVAILABLE"))
				.andExpect(jsonPath("$.detail").value("Product search is temporarily unavailable"))
				.andExpect(jsonPath("$.trace").doesNotExist());
	}

	@Test
	void rejectsAnInvalidProductSearchRequest() throws Exception {
		when(productSearchService.search(" ", 0, 0))
				.thenThrow(new InvalidProductSearchRequestException());

		mockMvc.perform(get("/api/products/search")
					.param("q", " ")
					.param("limit", "0"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

		verify(productSearchService).search(" ", 0, 0);
	}

	@Test
	void getsAndUpdatesProduct() throws Exception {
		when(productService.getById(1L)).thenReturn(productResponse());
		when(productCommandFacade.update(org.mockito.ArgumentMatchers.eq(1L), any(UpdateProductRequest.class)))
				.thenReturn(productResponse());

		mockMvc.perform(get("/api/products/1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Sakura Perfume 100 ml"));

		mockMvc.perform(put("/api/products/1")
					.contentType(MediaType.APPLICATION_JSON)
					.content(updateProductJson()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.active").value(true));
	}

	@Test
	void deletesProduct() throws Exception {
		mockMvc.perform(delete("/api/products/1"))
				.andExpect(status().isNoContent())
				.andExpect(content().string(""));

		verify(productCommandFacade).delete(1L);
	}

	@Test
	void addsUpdatesAndRemovesComponent() throws Exception {
		when(productCommandFacade.addComponent(
				org.mockito.ArgumentMatchers.eq(1L),
				any(AddProductComponentRequest.class)))
				.thenReturn(compositionResponse());
		when(productCommandFacade.updateComponent(
				org.mockito.ArgumentMatchers.eq(1L),
				org.mockito.ArgumentMatchers.eq(2L),
				any(UpdateProductComponentRequest.class)))
				.thenReturn(compositionResponse());

		mockMvc.perform(post("/api/products/1/components")
					.contentType(MediaType.APPLICATION_JSON)
					.content(componentJson("50.000000")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.componentProductId").value(2))
				.andExpect(jsonPath("$.measurementUnit").value("MILLILITER"));

		mockMvc.perform(put("/api/products/1/components/2")
					.contentType(MediaType.APPLICATION_JSON)
					.content(updateComponentJson("50.000000")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.quantity").value(50.0));

		mockMvc.perform(delete("/api/products/1/components/2"))
				.andExpect(status().isNoContent());

		verify(productCommandFacade).removeComponent(1L, 2L);
	}

	@Test
	void getsCompositionTree() throws Exception {
		when(productService.getComposition(1L)).thenReturn(compositionTreeResponse());

		mockMvc.perform(get("/api/products/1/composition"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.productId").value(1))
				.andExpect(jsonPath("$.components[0].compositionId").value(10))
				.andExpect(jsonPath("$.components[0].productId").value(2));
	}

	@Test
	void rejectsNonPositiveComponentQuantity() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/products/1/components")
					.contentType(MediaType.APPLICATION_JSON)
					.content(componentJson("0")))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.errors[0].field").value("quantity"))
				.andExpect(jsonPath("$.trace").doesNotExist())
				.andReturn();

		assertThat(result.getResponse().getContentAsString()).doesNotContain("stackTrace");
		verifyNoInteractions(productService);
	}

	@Test
	void rejectsAComponentQuantityOutsideTheDatabasePrecisionAndScale() throws Exception {
		mockMvc.perform(post("/api/products/1/components")
					.contentType(MediaType.APPLICATION_JSON)
					.content(componentJson("0.0000001")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

		mockMvc.perform(post("/api/products/1/components")
					.contentType(MediaType.APPLICATION_JSON)
					.content(componentJson("10000000000000")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

		verifyNoInteractions(productService);
	}

	@Test
	void returnsProblemDetailWhenProductDoesNotExist() throws Exception {
		when(productService.getById(99L)).thenThrow(new ProductNotFoundException(99L));

		mockMvc.perform(get("/api/products/99"))
				.andExpect(status().isNotFound())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
				.andExpect(jsonPath("$.detail").value("Product with ID 99 was not found"))
				.andExpect(jsonPath("$.trace").doesNotExist());
	}

	@Test
	void returnsAGenericConflictForAnUnclassifiedDatabaseConstraint() throws Exception {
		when(productCommandFacade.create(any(CreateProductRequest.class)))
				.thenThrow(new DataIntegrityViolationException("constraint violation"));

		mockMvc.perform(post("/api/products")
					.contentType(MediaType.APPLICATION_JSON)
					.content(createProductJson()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DATA_INTEGRITY_VIOLATION"))
				.andExpect(jsonPath("$.detail").value("The operation conflicts with a database constraint"))
				.andExpect(jsonPath("$.trace").doesNotExist());
	}

	@Test
	void returnsConflictForOptimisticLocking() throws Exception {
		when(productService.getById(1L))
				.thenThrow(new ObjectOptimisticLockingFailureException(Product.class, 1L));

		mockMvc.perform(get("/api/products/1"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("OPTIMISTIC_LOCK_CONFLICT"))
				.andExpect(jsonPath("$.trace").doesNotExist());
	}

	private static ProductResponse productResponse() {
		Instant now = Instant.parse("2026-07-22T12:00:00Z");
		return new ProductResponse(
				1L,
				"Sakura Perfume 100 ml",
				"Finished perfume",
				"PERF-SAKURA-100ML",
				ProductType.FINISHED_PRODUCT,
				MeasurementUnit.UNIT,
				true,
				now,
				now,
				0L,
				List.of());
	}

	private static ProductCompositionResponse compositionResponse() {
		Instant now = Instant.parse("2026-07-22T12:00:00Z");
		return new ProductCompositionResponse(
				10L,
				2L,
				"Sakura Essence",
				"ESS-SAKURA",
				new BigDecimal("50.000000"),
				MeasurementUnit.MILLILITER,
				now,
				now);
	}

	private static ProductCompositionTreeResponse compositionTreeResponse() {
		return new ProductCompositionTreeResponse(
				1L,
				"Sakura Perfume 100 ml",
				"PERF-SAKURA-100ML",
				ProductType.FINISHED_PRODUCT,
				MeasurementUnit.UNIT,
				List.of(new ProductCompositionTreeResponse.Component(
						10L,
						2L,
						"Sakura Essence",
						"ESS-SAKURA",
						new BigDecimal("50.000000"),
						MeasurementUnit.MILLILITER,
						List.of())));
	}

	private static String createProductJson() {
		return """
				{
				  "name": "Sakura Perfume 100 ml",
				  "description": "Finished perfume",
				  "type": "FINISHED_PRODUCT",
				  "defaultMeasurementUnit": "UNIT",
				  "active": true
				}
				""";
	}

	private static String updateProductJson() {
		return createProductJson();
	}

	private static String componentJson(String quantity) {
		return """
				{
				  "componentProductId": 2,
				  "quantity": %s,
				  "measurementUnit": "MILLILITER"
				}
				""".formatted(quantity);
	}

	private static String updateComponentJson(String quantity) {
		return """
				{
				  "quantity": %s,
				  "measurementUnit": "MILLILITER"
				}
				""".formatted(quantity);
	}
}
