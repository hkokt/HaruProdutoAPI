package com.haru.produto.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class SecurityConfigTests {

	@Test
	void mapsKeycloakRealmAndClientRolesToSpringAuthorities() {
		Jwt jwt = Jwt.withTokenValue("token")
				.header("alg", "none")
				.claim("sub", "user-id")
				.claim("preferred_username", "customer@example.com")
				.claim("realm_access", Map.of("roles", List.of("admin")))
				.claim("resource_access", Map.of("haru-api", Map.of("roles", List.of("customer"))))
				.build();

		var authentication = new SecurityConfig()
				.jwtAuthenticationConverter("haru-api")
				.convert(jwt);

		assertThat(authentication.getName()).isEqualTo("customer@example.com");
		assertThat(authentication.getAuthorities())
				.extracting(GrantedAuthority::getAuthority)
				.contains("ROLE_ADMIN", "ROLE_CUSTOMER");
	}
}
