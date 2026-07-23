package com.haru.product.inventory.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.haru.product.inventory.application.InventorySearchService;
import com.haru.product.inventory.application.InventoryService;
import com.haru.product.inventory.application.dto.AdjustInventoryRequest;
import com.haru.product.inventory.application.dto.ConsumeInventoryRequest;
import com.haru.product.inventory.application.dto.CreateInventoryLotRequest;
import com.haru.product.inventory.application.dto.InventoryAvailabilityResponse;
import com.haru.product.inventory.application.dto.InventoryConsumptionResponse;
import com.haru.product.inventory.application.dto.InventoryLotResponse;
import com.haru.product.inventory.application.dto.InventoryMovementResponse;
import com.haru.product.inventory.application.dto.InventoryProductSummaryResponse;
import com.haru.product.inventory.domain.InventoryLotStatus;
import com.haru.product.inventory.domain.InventoryMovementType;
import com.haru.product.inventory.domain.exception.DuplicateInventoryLotException;
import com.haru.product.inventory.domain.exception.InsufficientInventoryException;
import com.haru.product.inventory.domain.exception.InvalidInventoryLotException;
import com.haru.product.inventory.domain.exception.InventoryLotNotFoundException;
import com.haru.product.product.domain.MeasurementUnit;
import com.haru.product.shared.exception.ApiExceptionHandler;
import com.haru.product.shared.pagination.OffsetPageResponse;

class InventoryControllerTests {

	private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 7, 22);
	private static final Instant NOW = Instant.parse("2026-07-22T12:00:00Z");
	private static final String ACTOR = "admin@example.com";

	private InventoryService inventoryService;
	private InventorySearchService inventorySearchService;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		inventoryService = mock(InventoryService.class);
		inventorySearchService = mock(InventorySearchService.class);
		mockMvc = MockMvcBuilders
				.standaloneSetup(new InventoryController(inventoryService, inventorySearchService))
				.setControllerAdvice(new ApiExceptionHandler())
				.build();
	}

	@Test
	void createsALotAndRecordsTheAuthenticatedActor() throws Exception {
		when(inventoryService.createLot(any(CreateInventoryLotRequest.class), eq(ACTOR)))
				.thenReturn(lotResponse(1L, "ESS-001", "100.000000", InventoryLotStatus.AVAILABLE));

		mockMvc.perform(post("/api/inventory/lots")
					.with(actor())
					.contentType(MediaType.APPLICATION_JSON)
					.content(createLotJson()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").value(1))
				.andExpect(jsonPath("$.lotNumber").value("ESS-001"))
				.andExpect(jsonPath("$.availableQuantity").value(100.0))
				.andExpect(jsonPath("$.status").value("AVAILABLE"));

		verify(inventoryService).createLot(any(CreateInventoryLotRequest.class), eq(ACTOR));
	}

	@Test
	void getsLotAndProductLots() throws Exception {
		InventoryLotResponse response = lotResponse(
				1L, "ESS-001", "100.000000", InventoryLotStatus.AVAILABLE);
		when(inventoryService.getLot(1L)).thenReturn(response);
		when(inventoryService.getProductLots(7L, 15, 20))
				.thenReturn(new OffsetPageResponse<>(
						List.of(response), 15, 20, 36, true, true));

		mockMvc.perform(get("/api/inventory/lots/1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.productId").value(7));
		mockMvc.perform(get("/api/inventory/products/7/lots")
					.param("offset", "15")
					.param("limit", "20"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].lotNumber").value("ESS-001"))
				.andExpect(jsonPath("$.offset").value(15))
				.andExpect(jsonPath("$.totalElements").value(36))
				.andExpect(jsonPath("$.hasPrevious").value(true))
				.andExpect(jsonPath("$.hasNext").value(true));
	}

	@Test
	void getsAvailabilityAndMovementHistory() throws Exception {
		when(inventoryService.getAvailability(7L)).thenReturn(new InventoryAvailabilityResponse(
				7L,
				"Sakura Essence",
				"ESS-SAKURA",
				MeasurementUnit.MILLILITER,
				new BigDecimal("100.000000"),
				REFERENCE_DATE));
		when(inventoryService.getProductMovements(7L, 0, 20))
				.thenReturn(new OffsetPageResponse<>(
						List.of(movementResponse()), 0, 20, 1, false, false));

		mockMvc.perform(get("/api/inventory/products/7/availability"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.availableQuantity").value(100.0))
				.andExpect(jsonPath("$.measurementUnit").value("MILLILITER"));
		mockMvc.perform(get("/api/inventory/products/7/movements"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].type").value("ENTRY"))
				.andExpect(jsonPath("$.content[0].createdBy").value(ACTOR));
	}

	@Test
	void searchesInventoryProductSummariesWithOffsetMetadata() throws Exception {
		when(inventorySearchService.search("sakura", 15, 20))
				.thenReturn(new OffsetPageResponse<>(
						List.of(new InventoryProductSummaryResponse(
								7L,
								"Sakura Essence",
								"ESS-SAKURA",
								MeasurementUnit.MILLILITER,
								true,
								new BigDecimal("100.000000"),
								2,
								REFERENCE_DATE)),
						15,
						20,
						25,
						true,
						true));

		mockMvc.perform(get("/api/inventory/products/search")
					.param("q", "sakura")
					.param("offset", "15"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].productId").value(7))
				.andExpect(jsonPath("$.content[0].availableQuantity").value(100.0))
				.andExpect(jsonPath("$.content[0].lotCount").value(2))
				.andExpect(jsonPath("$.offset").value(15))
				.andExpect(jsonPath("$.limit").value(20))
				.andExpect(jsonPath("$.hasPrevious").value(true))
				.andExpect(jsonPath("$.hasNext").value(true));

		verify(inventorySearchService).search("sakura", 15, 20);
	}

	@Test
	void appliesCompensatingAdjustmentsThroughDedicatedEndpoints() throws Exception {
		when(inventoryService.adjustIn(eq(1L), any(AdjustInventoryRequest.class), eq(ACTOR)))
				.thenReturn(lotResponse(1L, "ESS-001", "110.000000", InventoryLotStatus.AVAILABLE));
		when(inventoryService.adjustOut(eq(1L), any(AdjustInventoryRequest.class), eq(ACTOR)))
				.thenReturn(lotResponse(1L, "ESS-001", "90.000000", InventoryLotStatus.AVAILABLE));

		mockMvc.perform(post("/api/inventory/lots/1/adjustments/in")
					.with(actor())
					.contentType(MediaType.APPLICATION_JSON)
					.content(adjustmentJson("10")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.availableQuantity").value(110.0));
		mockMvc.perform(post("/api/inventory/lots/1/adjustments/out")
					.with(actor())
					.contentType(MediaType.APPLICATION_JSON)
					.content(adjustmentJson("10")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.availableQuantity").value(90.0));
	}

	@Test
	void consumesThroughTheExplicitFefoEndpoint() throws Exception {
		when(inventoryService.consume(eq(7L), any(ConsumeInventoryRequest.class), eq(ACTOR)))
				.thenReturn(consumptionResponse());

		mockMvc.perform(post("/api/inventory/products/7/consumption")
					.with(actor())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{
							  "quantity": 50,
							  "referenceType": "MANUAL_REQUEST",
							  "referenceId": 99,
							  "description": "FEFO test consumption"
							}
							"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.consumedQuantity").value(50.0))
				.andExpect(jsonPath("$.lots[0].lotNumber").value("ESS-001"))
				.andExpect(jsonPath("$.lots[0].status").value("DEPLETED"));
	}

	@Test
	void validatesQuantityPrecisionAndRequiredJustificationBeforeCallingTheService() throws Exception {
		mockMvc.perform(post("/api/inventory/lots/1/adjustments/out")
					.with(actor())
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"quantity": 0.0000001, "justification": " "}
							"""))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
				.andExpect(jsonPath("$.trace").doesNotExist());

		verifyNoInteractions(inventoryService);
	}

	@Test
	void mapsInventoryDomainFailuresToProblemDetails() throws Exception {
		when(inventoryService.getLot(99L)).thenThrow(new InventoryLotNotFoundException(99L));
		when(inventoryService.createLot(any(CreateInventoryLotRequest.class), eq(ACTOR)))
				.thenThrow(new DuplicateInventoryLotException(7L, "ESS-001"));
		when(inventoryService.consume(eq(7L), any(ConsumeInventoryRequest.class), eq(ACTOR)))
				.thenThrow(new InsufficientInventoryException(
						7L, new BigDecimal("50"), new BigDecimal("40")));

		mockMvc.perform(get("/api/inventory/lots/99"))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
		mockMvc.perform(post("/api/inventory/lots")
					.with(actor())
					.contentType(MediaType.APPLICATION_JSON)
					.content(createLotJson()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
		mockMvc.perform(post("/api/inventory/products/7/consumption")
					.with(actor())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{" + "\"quantity\": 50" + "}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("INVENTORY_CONFLICT"));
	}

	@Test
	void mapsInvalidLotDatesToUnprocessableContent() throws Exception {
		when(inventoryService.createLot(any(CreateInventoryLotRequest.class), eq(ACTOR)))
				.thenThrow(new InvalidInventoryLotException(
						"Inventory lot expiration date cannot be before its manufacture date"));

		mockMvc.perform(post("/api/inventory/lots")
					.with(actor())
					.contentType(MediaType.APPLICATION_JSON)
					.content(createLotJson()))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("INVALID_INVENTORY_OPERATION"));
	}

	private static InventoryLotResponse lotResponse(
			Long id,
			String lotNumber,
			String availableQuantity,
			InventoryLotStatus status) {
		return new InventoryLotResponse(
				id,
				7L,
				"Sakura Essence",
				"ESS-SAKURA",
				lotNumber,
				LocalDate.of(2026, 7, 1),
				LocalDate.of(2026, 8, 10),
				new BigDecimal("100.000000"),
				new BigDecimal(availableQuantity),
				new BigDecimal("0.5000"),
				status,
				NOW,
				NOW,
				0L);
	}

	private static InventoryMovementResponse movementResponse() {
		return new InventoryMovementResponse(
				11L,
				1L,
				"ESS-001",
				7L,
				InventoryMovementType.ENTRY,
				new BigDecimal("100.000000"),
				new BigDecimal("100.000000"),
				"INVENTORY_LOT",
				1L,
				"Initial inventory lot entry",
				NOW,
				ACTOR);
	}

	private static InventoryConsumptionResponse consumptionResponse() {
		return new InventoryConsumptionResponse(
				7L,
				"Sakura Essence",
				"ESS-SAKURA",
				new BigDecimal("50.000000"),
				new BigDecimal("50.000000"),
				MeasurementUnit.MILLILITER,
				REFERENCE_DATE,
				List.of(new InventoryConsumptionResponse.LotConsumption(
						1L,
						"ESS-001",
						LocalDate.of(2026, 8, 10),
						new BigDecimal("50.000000"),
						BigDecimal.ZERO,
						InventoryLotStatus.DEPLETED,
						12L)));
	}

	private static RequestPostProcessor actor() {
		Principal principal = () -> ACTOR;
		return request -> {
			request.setUserPrincipal(principal);
			return request;
		};
	}

	private static String createLotJson() {
		return """
				{
				  "productId": 7,
				  "lotNumber": "ESS-001",
				  "manufactureDate": "2026-07-01",
				  "expirationDate": "2026-08-10",
				  "initialQuantity": 100,
				  "unitCost": 0.50
				}
				""";
	}

	private static String adjustmentJson(String quantity) {
		return """
				{"quantity": %s, "justification": "Cycle count correction"}
				""".formatted(quantity);
	}
}
