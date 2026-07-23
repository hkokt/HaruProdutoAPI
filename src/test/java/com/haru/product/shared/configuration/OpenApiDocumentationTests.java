package com.haru.product.shared.configuration;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.haru.product.inventory.infrastructure.persistence.InventoryLotRepository;
import com.haru.product.inventory.infrastructure.persistence.InventoryMovementRepository;
import com.haru.product.product.infrastructure.persistence.ProductCompositionRepository;
import com.haru.product.product.infrastructure.persistence.ProductCompositionTopologyLock;
import com.haru.product.product.infrastructure.persistence.PostgreSqlProductSkuGenerator;
import com.haru.product.product.infrastructure.persistence.ProductRepository;
import com.haru.product.production.infrastructure.persistence.ProducedLotRepository;
import com.haru.product.production.infrastructure.persistence.ProductionConsumptionRepository;
import com.haru.product.production.infrastructure.persistence.ProductionOrderRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
		"spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
				+ "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
				+ "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
})
@AutoConfigureMockMvc
class OpenApiDocumentationTests {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ProductRepository productRepository;

	@MockitoBean
	private ProductCompositionRepository productCompositionRepository;

	@MockitoBean
	private ProductCompositionTopologyLock productCompositionTopologyLock;

	@MockitoBean
	private PostgreSqlProductSkuGenerator productSkuGenerator;

	@MockitoBean
	private InventoryLotRepository inventoryLotRepository;

	@MockitoBean
	private InventoryMovementRepository inventoryMovementRepository;

	@MockitoBean
	private ProductionOrderRepository productionOrderRepository;

	@MockitoBean
	private ProductionConsumptionRepository productionConsumptionRepository;

	@MockitoBean
	private ProducedLotRepository producedLotRepository;

	@Test
	void exposesTheOpenApiContractWithoutAuthentication() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.openapi").exists())
				.andExpect(jsonPath("$.info.title").value("Haru Product API"))
				.andExpect(jsonPath("$.info.version").value("v1"))
				.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
				.andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
				.andExpect(jsonPath("$.security[0].bearerAuth").isArray())
				.andExpect(jsonPath("$.components.schemas.CreateProductRequest.properties.sku")
						.doesNotExist())
				.andExpect(jsonPath("$.components.schemas.UpdateProductRequest.properties.sku")
						.doesNotExist())
				.andExpect(jsonPath("$.components.schemas.ProductResponse.properties.sku").exists())
				.andExpect(jsonPath("$['paths']['/api/products/search']").exists())
				.andExpect(jsonPath("$['paths']['/api/products/search']['get']['parameters'][?(@.name == 'offset')]")
						.isNotEmpty())
				.andExpect(jsonPath("$['paths']['/api/products/search']['get']['parameters'][?(@.name == 'limit')]")
						.isNotEmpty())
				.andExpect(jsonPath("$['paths']['/api/products/{id}']").exists())
				.andExpect(jsonPath("$['paths']['/api/inventory/lots/{id}']").exists())
				.andExpect(jsonPath("$['paths']['/api/inventory/products/search']['get']").exists())
				.andExpect(jsonPath("$['paths']['/api/production-orders/{id}']").exists())
				.andExpect(jsonPath("$['paths']['/api/production-orders/search']['get']").exists())
				.andExpect(jsonPath("$['paths']['/admin/status']").doesNotExist())
				.andExpect(jsonPath("$['paths']['/admin/search/products/reindex']").doesNotExist());

		mockMvc.perform(get("/v3/api-docs.yaml"))
				.andExpect(status().isOk())
				.andExpect(content().string(containsString("openapi:")));
	}

	@Test
	void servesTheSwaggerUiWithoutAuthentication() throws Exception {
		mockMvc.perform(get("/swagger-ui.html"))
				.andExpect(status().is3xxRedirection())
				.andExpect(header().string(
						HttpHeaders.LOCATION,
						containsString("/swagger-ui/index.html")));

		mockMvc.perform(get("/swagger-ui/index.html"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
				.andExpect(content().string(containsString("Swagger UI")));
	}

	@Test
	void keepsBusinessEndpointsProtected() throws Exception {
		mockMvc.perform(get("/api/products/1"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
	}
}
