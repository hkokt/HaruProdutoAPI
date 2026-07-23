package com.haru.product.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(classes = SecurityAuthorizationRulesTests.TestApplication.class, properties = {
		"spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
				+ "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
				+ "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
})
@AutoConfigureMockMvc
@Import(SecurityAuthorizationRulesTests.TestEndpoints.class)
class SecurityAuthorizationRulesTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter;

	@Autowired
	private OAuth2ResourceServerProperties resourceServerProperties;

	@Test
	void requiresTheHaruApiAudience() {
		assertThat(resourceServerProperties.getJwt().getAudiences())
				.containsExactly("haru-api");
	}

	@Test
	void documentationEndpointsArePublic() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk());
		mockMvc.perform(get("/swagger-ui/index.html"))
				.andExpect(status().isOk());
	}

	@Test
	void customerCanReadProducts() throws Exception {
		mockMvc.perform(get("/api/products/1").with(keycloakJwt(customerJwt())))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/products/search").with(keycloakJwt(customerJwt())))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/products/1/composition").with(keycloakJwt(customerJwt())))
				.andExpect(status().isOk());
	}

	@Test
	void customerCanReadInventory() throws Exception {
		mockMvc.perform(get("/api/inventory/lots/1").with(keycloakJwt(customerJwt())))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/inventory/products/1/lots").with(keycloakJwt(customerJwt())))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/inventory/products/1/availability").with(keycloakJwt(customerJwt())))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/inventory/products/1/movements").with(keycloakJwt(customerJwt())))
				.andExpect(status().isOk());
	}

	@Test
	void customerCanReadProductionOrders() throws Exception {
		mockMvc.perform(get("/api/production-orders/1").with(keycloakJwt(customerJwt())))
				.andExpect(status().isOk());
	}

	@Test
	void customerCannotMutateProductsOrCompositions() throws Exception {
		mockMvc.perform(post("/api/products").with(keycloakJwt(customerJwt())))
				.andExpect(status().isForbidden())
				.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(403))
				.andExpect(jsonPath("$.title").value("Access denied"))
				.andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
				.andExpect(jsonPath("$.detail").value(
						"The authenticated user does not have permission to access this resource"))
				.andExpect(jsonPath("$.trace").doesNotExist())
				.andExpect(jsonPath("$.exception").doesNotExist());
		mockMvc.perform(put("/api/products/1").with(keycloakJwt(customerJwt())))
				.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/products/1").with(keycloakJwt(customerJwt())))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/products/1/components").with(keycloakJwt(customerJwt())))
				.andExpect(status().isForbidden());
		mockMvc.perform(put("/api/products/1/components/2").with(keycloakJwt(customerJwt())))
				.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/products/1/components/2").with(keycloakJwt(customerJwt())))
				.andExpect(status().isForbidden());
	}

	@Test
	void customerCannotMutateInventory() throws Exception {
		mockMvc.perform(post("/api/inventory/lots").with(keycloakJwt(customerJwt())))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/inventory/lots/1/adjustments/in").with(keycloakJwt(customerJwt())))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/inventory/lots/1/adjustments/out").with(keycloakJwt(customerJwt())))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/inventory/products/1/consumption").with(keycloakJwt(customerJwt())))
				.andExpect(status().isForbidden());
	}

	@Test
	void customerCannotMutateProductionOrders() throws Exception {
		mockMvc.perform(post("/api/production-orders").with(keycloakJwt(customerJwt())))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/production-orders/1/start").with(keycloakJwt(customerJwt())))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/production-orders/1/complete").with(keycloakJwt(customerJwt())))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/production-orders/1/cancel").with(keycloakJwt(customerJwt())))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/admin/search/products/reindex").with(keycloakJwt(customerJwt())))
				.andExpect(status().isForbidden());
	}

	@Test
	void adminCanCreateProductsAndAccessAdminRoutes() throws Exception {
		Jwt adminJwt = adminJwt();

		mockMvc.perform(post("/api/products").with(keycloakJwt(adminJwt)))
				.andExpect(status().isOk());
		mockMvc.perform(put("/api/products/1").with(keycloakJwt(adminJwt)))
				.andExpect(status().isOk());
		mockMvc.perform(delete("/api/products/1").with(keycloakJwt(adminJwt)))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/products/1/components").with(keycloakJwt(adminJwt)))
				.andExpect(status().isOk());
		mockMvc.perform(put("/api/products/1/components/2").with(keycloakJwt(adminJwt)))
				.andExpect(status().isOk());
		mockMvc.perform(delete("/api/products/1/components/2").with(keycloakJwt(adminJwt)))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/inventory/lots").with(keycloakJwt(adminJwt)))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/inventory/lots/1/adjustments/in").with(keycloakJwt(adminJwt)))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/inventory/lots/1/adjustments/out").with(keycloakJwt(adminJwt)))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/inventory/products/1/consumption").with(keycloakJwt(adminJwt)))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/production-orders").with(keycloakJwt(adminJwt)))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/production-orders/1/start").with(keycloakJwt(adminJwt)))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/production-orders/1/complete").with(keycloakJwt(adminJwt)))
				.andExpect(status().isOk());
		mockMvc.perform(post("/api/production-orders/1/cancel").with(keycloakJwt(adminJwt)))
				.andExpect(status().isOk());

		mockMvc.perform(get("/admin/status").with(keycloakJwt(adminJwt)))
				.andExpect(status().isOk());
		mockMvc.perform(post("/admin/search/products/reindex").with(keycloakJwt(adminJwt)))
				.andExpect(status().isOk());
	}

	@Test
	void unauthenticatedRequestIsRejected() throws Exception {
		mockMvc.perform(get("/api/products/1"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, startsWith("Bearer")))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.title").value("Authentication required"))
				.andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
				.andExpect(jsonPath("$.detail").value(
						"A valid bearer token is required to access this resource"))
				.andExpect(jsonPath("$.trace").doesNotExist())
				.andExpect(jsonPath("$.exception").doesNotExist());
		mockMvc.perform(get("/api/inventory/lots/1"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/production-orders/1"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(get("/api/products/search"))
				.andExpect(status().isUnauthorized());
		mockMvc.perform(post("/admin/search/products/reindex"))
				.andExpect(status().isUnauthorized());
	}

	private RequestPostProcessor keycloakJwt(Jwt jwt) {
		return jwt().jwt(jwt).authorities(jwtAuthenticationConverter.convert(jwt).getAuthorities());
	}

	private static Jwt customerJwt() {
		return jwtWithRealmRoles("customer");
	}

	private static Jwt adminJwt() {
		return jwtWithRealmRoles("admin");
	}

	private static Jwt jwtWithRealmRoles(String role) {
		return Jwt.withTokenValue("token-" + role)
				.header("alg", "none")
				.claim("sub", role + "-id")
				.audience(List.of("haru-api"))
				.claim("preferred_username", role + "@example.com")
				.claim("realm_access", Map.of("roles", List.of(role)))
				.build();
	}

	@RestController
	static class TestEndpoints {

		@GetMapping("/api/products/search")
		ResponseEntity<Void> searchProducts() {
			return ResponseEntity.ok().build();
		}

		@GetMapping("/api/products/{id}")
		ResponseEntity<Void> getProduct(@PathVariable Long id) {
			return ResponseEntity.ok().build();
		}

		@PostMapping("/admin/search/products/reindex")
		ResponseEntity<Void> reindexProducts() {
			return ResponseEntity.ok().build();
		}

		@GetMapping("/api/products/{id}/composition")
		ResponseEntity<Void> getComposition(@PathVariable Long id) {
			return ResponseEntity.ok().build();
		}

		@PostMapping("/api/products")
		ResponseEntity<Void> createProduct() {
			return ResponseEntity.ok().build();
		}

		@PutMapping("/api/products/{id}")
		ResponseEntity<Void> updateProduct(@PathVariable Long id) {
			return ResponseEntity.ok().build();
		}

		@DeleteMapping("/api/products/{id}")
		ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
			return ResponseEntity.ok().build();
		}

		@PostMapping("/api/products/{id}/components")
		ResponseEntity<Void> addComponent(@PathVariable Long id) {
			return ResponseEntity.ok().build();
		}

		@PutMapping("/api/products/{id}/components/{componentId}")
		ResponseEntity<Void> updateComponent(
				@PathVariable Long id,
				@PathVariable Long componentId) {
			return ResponseEntity.ok().build();
		}

		@DeleteMapping("/api/products/{id}/components/{componentId}")
		ResponseEntity<Void> deleteComponent(
				@PathVariable Long id,
				@PathVariable Long componentId) {
			return ResponseEntity.ok().build();
		}

		@GetMapping("/api/inventory/lots/{id}")
		ResponseEntity<Void> getInventoryLot(@PathVariable Long id) {
			return ResponseEntity.ok().build();
		}

		@GetMapping("/api/inventory/products/{productId}/lots")
		ResponseEntity<Void> getInventoryLots(@PathVariable Long productId) {
			return ResponseEntity.ok().build();
		}

		@GetMapping("/api/inventory/products/{productId}/availability")
		ResponseEntity<Void> getInventoryAvailability(@PathVariable Long productId) {
			return ResponseEntity.ok().build();
		}

		@GetMapping("/api/inventory/products/{productId}/movements")
		ResponseEntity<Void> getInventoryMovements(@PathVariable Long productId) {
			return ResponseEntity.ok().build();
		}

		@PostMapping("/api/inventory/lots")
		ResponseEntity<Void> createInventoryLot() {
			return ResponseEntity.ok().build();
		}

		@PostMapping("/api/inventory/lots/{id}/adjustments/in")
		ResponseEntity<Void> adjustInventoryIn(@PathVariable Long id) {
			return ResponseEntity.ok().build();
		}

		@PostMapping("/api/inventory/lots/{id}/adjustments/out")
		ResponseEntity<Void> adjustInventoryOut(@PathVariable Long id) {
			return ResponseEntity.ok().build();
		}

		@PostMapping("/api/inventory/products/{productId}/consumption")
		ResponseEntity<Void> consumeInventory(@PathVariable Long productId) {
			return ResponseEntity.ok().build();
		}

		@GetMapping("/api/production-orders/{id}")
		ResponseEntity<Void> getProductionOrder(@PathVariable Long id) {
			return ResponseEntity.ok().build();
		}

		@PostMapping("/api/production-orders")
		ResponseEntity<Void> createProductionOrder() {
			return ResponseEntity.ok().build();
		}

		@PostMapping("/api/production-orders/{id}/start")
		ResponseEntity<Void> startProductionOrder(@PathVariable Long id) {
			return ResponseEntity.ok().build();
		}

		@PostMapping("/api/production-orders/{id}/complete")
		ResponseEntity<Void> completeProductionOrder(@PathVariable Long id) {
			return ResponseEntity.ok().build();
		}

		@PostMapping("/api/production-orders/{id}/cancel")
		ResponseEntity<Void> cancelProductionOrder(@PathVariable Long id) {
			return ResponseEntity.ok().build();
		}

		@GetMapping("/admin/status")
		ResponseEntity<Void> adminStatus() {
			return ResponseEntity.ok().build();
		}
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	@Import({ SecurityConfig.class, TestEndpoints.class })
	static class TestApplication {
	}
}
