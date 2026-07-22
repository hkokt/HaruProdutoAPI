package com.haru.product.shared.security;

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

	@Test
	void ignoresMalformedRoleClaimsAndFallsBackToTheSubject() {
		Jwt jwt = Jwt.withTokenValue("token")
				.header("alg", "none")
				.claim("sub", "user-id")
				.claim("scope", "products.read")
				.claim("realm_access", "invalid")
				.claim("resource_access", List.of("invalid"))
				.build();

		var authentication = new SecurityConfig()
				.jwtAuthenticationConverter("haru-api")
				.convert(jwt);

		assertThat(authentication.getName()).isEqualTo("user-id");
		assertThat(authentication.getAuthorities())
				.extracting(GrantedAuthority::getAuthority)
					.containsExactly("SCOPE_products.read");
	}

	@Test
	void fallsBackToTheSubjectWhenPreferredUsernameIsBlank() {
		Jwt jwt = Jwt.withTokenValue("token")
				.header("alg", "none")
				.claim("sub", "stable-user-id")
				.claim("preferred_username", "   ")
				.build();

		var authentication = new SecurityConfig()
				.jwtAuthenticationConverter("haru-api")
				.convert(jwt);

		assertThat(authentication.getName()).isEqualTo("stable-user-id");
	}
}
