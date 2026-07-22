package com.haru.product.shared.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

	static final String BEARER_AUTH = "bearerAuth";

	@Bean
	OpenAPI haruProductOpenApi() {
		SecurityScheme bearerScheme = new SecurityScheme()
				.type(SecurityScheme.Type.HTTP)
				.scheme("bearer")
				.bearerFormat("JWT")
				.description("Keycloak access token with audience 'haru-api'");

		return new OpenAPI()
				.info(new Info()
						.title("Haru Product API")
						.version("v1")
						.description(
								"Product, bill of materials, inventory, and production management API."))
				.components(new Components().addSecuritySchemes(BEARER_AUTH, bearerScheme))
				.addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
	}
}
