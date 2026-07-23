package com.haru.product.production.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.haru.product.inventory.domain.InventoryLotStatus;
import com.haru.product.product.domain.MeasurementUnit;
import com.haru.product.production.application.ProductionService;
import com.haru.product.production.application.dto.CompleteProductionRequest;
import com.haru.product.production.application.dto.CreateProductionOrderRequest;
import com.haru.product.production.application.dto.ProductionConsumptionResponse;
import com.haru.product.production.application.dto.ProductionOrderResponse;
import com.haru.product.production.application.dto.ProductionResultResponse;
import com.haru.product.production.application.dto.ProductionResultResponse.ProducedLotSummary;
import com.haru.product.production.domain.ProductionOrderStatus;
import com.haru.product.production.domain.exception.DuplicateProducedLotException;
import com.haru.product.production.domain.exception.InvalidProductionOrderStateException;
import com.haru.product.production.domain.exception.ProductWithoutBomException;
import com.haru.product.production.domain.exception.ProductionOrderNotFoundException;
import com.haru.product.shared.exception.ApiExceptionHandler;
import com.haru.product.shared.pagination.OffsetPageResponse;

class ProductionControllerTests {

	private static final Instant CREATED_AT = Instant.parse("2026-07-22T12:00:00Z");
	private static final Instant STARTED_AT = Instant.parse("2026-07-22T12:05:00Z");
	private static final Instant COMPLETED_AT = Instant.parse("2026-07-22T12:10:00Z");
	private static final String ACTOR = "admin@example.com";

	private ProductionService productionService;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		productionService = mock(ProductionService.class);
		mockMvc = MockMvcBuilders
				.standaloneSetup(new ProductionController(productionService))
				.setControllerAdvice(new ApiExceptionHandler())
				.build();
	}

	@Test
	void createsAProductionOrder() throws Exception {
		when(productionService.create(any(CreateProductionOrderRequest.class)))
				.thenReturn(order(ProductionOrderStatus.CREATED));

		mockMvc.perform(post("/api/production-orders")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "productId": 7,
							  "quantityToProduce": 10
							}
							"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(21))
				.andExpect(jsonPath("$.productSku").value("PERF-SAKURA-100ML"))
				.andExpect(jsonPath("$.quantityToProduce").value(10))
				.andExpect(jsonPath("$.measurementUnit").value("UNIT"))
				.andExpect(jsonPath("$.status").value("CREATED"));

		verify(productionService).create(new CreateProductionOrderRequest(7L, BigDecimal.TEN));
	}

	@Test
	void searchesProductionOrdersByQueryStatusAndOffset() throws Exception {
		when(productionService.search("sakura", ProductionOrderStatus.IN_PROGRESS, 15, 20))
				.thenReturn(new OffsetPageResponse<>(
						List.of(order(ProductionOrderStatus.IN_PROGRESS)),
						15,
						20,
						36,
						true,
						true));

		mockMvc.perform(get("/api/production-orders/search")
					.param("q", "sakura")
					.param("status", "IN_PROGRESS")
					.param("offset", "15"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].id").value(21))
				.andExpect(jsonPath("$.content[0].status").value("IN_PROGRESS"))
				.andExpect(jsonPath("$.offset").value(15))
				.andExpect(jsonPath("$.limit").value(20))
				.andExpect(jsonPath("$.totalElements").value(36))
				.andExpect(jsonPath("$.hasPrevious").value(true))
				.andExpect(jsonPath("$.hasNext").value(true));

		verify(productionService).search("sakura", ProductionOrderStatus.IN_PROGRESS, 15, 20);
	}

	@Test
	void getsTheOrderWithProducedLotAndConsumedLotTraceability() throws Exception {
		when(productionService.getById(21L)).thenReturn(result());

		mockMvc.perform(get("/api/production-orders/21"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.order.status").value("COMPLETED"))
				.andExpect(jsonPath("$.producedLot.lotNumber").value("PERF-SAKURA-001"))
				.andExpect(jsonPath("$.producedLot.inventoryLotId").value(80))
				.andExpect(jsonPath("$.consumptions[0].componentProductSku").value("ESS-SAKURA"))
				.andExpect(jsonPath("$.consumptions[0].consumedLotNumber").value("ESS-001"));

		verify(productionService).getById(21L);
	}

	@Test
	void startsAProductionOrder() throws Exception {
		when(productionService.start(21L)).thenReturn(order(ProductionOrderStatus.IN_PROGRESS));

		mockMvc.perform(post("/api/production-orders/21/start"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("IN_PROGRESS"))
				.andExpect(jsonPath("$.startedAt").value(STARTED_AT.toString()));

		verify(productionService).start(21L);
	}

	@Test
	void completesAnOrderWithTheAuthenticatedActor() throws Exception {
		when(productionService.complete(
				eq(21L),
				any(CompleteProductionRequest.class),
				eq(ACTOR)))
				.thenReturn(result());

		mockMvc.perform(post("/api/production-orders/21/complete")
					.with(actor())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "producedLotNumber": "PERF-SAKURA-001",
							  "manufactureDate": "2026-07-22",
							  "expirationDate": "2027-07-22",
							  "producedUnitCost": 12.5000
							}
							"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.order.status").value("COMPLETED"))
				.andExpect(jsonPath("$.producedLot.producedQuantity").value(10))
				.andExpect(jsonPath("$.consumptions[0].consumedLotId").value(31));

		verify(productionService).complete(
				eq(21L),
				eq(new CompleteProductionRequest(
						"PERF-SAKURA-001",
						LocalDate.of(2026, 7, 22),
						LocalDate.of(2027, 7, 22),
						new BigDecimal("12.5000"))),
				eq(ACTOR));
	}

	@Test
	void cancelsAProductionOrder() throws Exception {
		when(productionService.cancel(21L)).thenReturn(order(ProductionOrderStatus.CANCELLED));

		mockMvc.perform(post("/api/production-orders/21/cancel"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CANCELLED"));

		verify(productionService).cancel(21L);
	}

	@Test
	void rejectsAnInvalidCreateRequestBeforeCallingTheService() throws Exception {
		mockMvc.perform(post("/api/production-orders")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "quantityToProduce": 0.0000001
							}
							"""))
				.andExpect(status().isBadRequest());

		verifyNoInteractions(productionService);
	}

	@Test
	void rejectsInvalidCompletionDataBeforeCallingTheService() throws Exception {
		mockMvc.perform(post("/api/production-orders/21/complete")
					.with(actor())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "producedLotNumber": " ",
							  "producedUnitCost": -0.0001
							}
							"""))
				.andExpect(status().isBadRequest());

		verifyNoInteractions(productionService);
	}

	@Test
	void mapsProductionFailuresToProblemDetails() throws Exception {
		when(productionService.getById(99L))
				.thenThrow(new ProductionOrderNotFoundException(99L));
		when(productionService.start(21L))
				.thenThrow(new InvalidProductionOrderStateException(
						21L, ProductionOrderStatus.IN_PROGRESS, "started"));
		when(productionService.create(any(CreateProductionOrderRequest.class)))
				.thenThrow(new ProductWithoutBomException(7L));
		when(productionService.complete(
				eq(21L),
				any(CompleteProductionRequest.class),
				eq(ACTOR)))
				.thenThrow(new DuplicateProducedLotException("PERF-SAKURA-001"));

		mockMvc.perform(get("/api/production-orders/99"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
		mockMvc.perform(post("/api/production-orders/21/start"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code")
						.value("PRODUCTION_ORDER_STATE_CONFLICT"));
		mockMvc.perform(post("/api/production-orders")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "productId": 7,
							  "quantityToProduce": 1
							}
							"""))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("INVALID_PRODUCTION_ORDER"));
		mockMvc.perform(post("/api/production-orders/21/complete")
					.with(actor())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "producedLotNumber": "PERF-SAKURA-001",
							  "manufactureDate": "2026-07-22",
							  "expirationDate": "2027-07-22",
							  "producedUnitCost": 12.5000
							}
							"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
	}

	private static ProductionOrderResponse order(ProductionOrderStatus status) {
		return new ProductionOrderResponse(
				21L,
				7L,
				"Sakura Perfume 100 ml",
				"PERF-SAKURA-100ML",
				BigDecimal.TEN,
				MeasurementUnit.UNIT,
				status,
				CREATED_AT,
				status == ProductionOrderStatus.CREATED ? null : STARTED_AT,
				status == ProductionOrderStatus.COMPLETED ? COMPLETED_AT : null,
				1L);
	}

	private static ProductionResultResponse result() {
		ProductionConsumptionResponse consumption = new ProductionConsumptionResponse(
				41L,
				8L,
				"Sakura Essence",
				"ESS-SAKURA",
				31L,
				"ESS-001",
				new BigDecimal("500.000000"),
				MeasurementUnit.MILLILITER,
				COMPLETED_AT);
		ProducedLotSummary producedLot = new ProducedLotSummary(
				51L,
				80L,
				"PERF-SAKURA-001",
				BigDecimal.TEN,
				MeasurementUnit.UNIT,
				InventoryLotStatus.AVAILABLE,
				LocalDate.of(2026, 7, 22),
				LocalDate.of(2027, 7, 22),
				COMPLETED_AT);
		return new ProductionResultResponse(
				order(ProductionOrderStatus.COMPLETED),
				producedLot,
				List.of(consumption));
	}

	private static RequestPostProcessor actor() {
		return request -> {
			request.setUserPrincipal(() -> ACTOR);
			return request;
		};
	}
}
