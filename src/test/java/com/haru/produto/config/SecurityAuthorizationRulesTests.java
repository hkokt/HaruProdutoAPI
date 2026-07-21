package com.haru.produto.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(properties = {
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

	@Test
	void customerCanReadProducts() throws Exception {
		mockMvc.perform(get("/produtos").with(keycloakJwt(customerJwt())))
				.andExpect(status().isOk());
	}

	@Test
	void customerCannotCreateProducts() throws Exception {
		mockMvc.perform(post("/produtos").with(keycloakJwt(customerJwt())))
				.andExpect(status().isForbidden());
	}

	@Test
	void adminCanCreateProductsAndAccessAdminRoutes() throws Exception {
		Jwt adminJwt = adminJwt();

		mockMvc.perform(post("/produtos").with(keycloakJwt(adminJwt)))
				.andExpect(status().isOk());

		mockMvc.perform(get("/admin/status").with(keycloakJwt(adminJwt)))
				.andExpect(status().isOk());
	}

	@Test
	void unauthenticatedRequestIsRejected() throws Exception {
		mockMvc.perform(get("/produtos"))
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
				.claim("preferred_username", role + "@example.com")
				.claim("realm_access", Map.of("roles", List.of(role)))
				.build();
	}

	@RestController
	static class TestEndpoints {

		@GetMapping("/produtos")
		ResponseEntity<Void> listProducts() {
			return ResponseEntity.ok().build();
		}

		@PostMapping("/produtos")
		ResponseEntity<Void> createProduct() {
			return ResponseEntity.ok().build();
		}

		@GetMapping("/admin/status")
		ResponseEntity<Void> adminStatus() {
			return ResponseEntity.ok().build();
		}
	}
}
