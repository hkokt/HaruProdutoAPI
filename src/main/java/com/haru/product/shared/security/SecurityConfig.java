package com.haru.product.shared.security;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.util.StringUtils;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

	private static final String ADMIN = "ADMIN";
	private static final String CUSTOMER = "CUSTOMER";
	private static final String ROLE_PREFIX = "ROLE_";

	@Bean
	SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter,
			AuthenticationEntryPoint authenticationEntryPoint,
			AccessDeniedHandler accessDeniedHandler) throws Exception {

		return http
				.csrf(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint(authenticationEntryPoint)
						.accessDeniedHandler(accessDeniedHandler))
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
						.requestMatchers("/error").permitAll()
						.requestMatchers(
								"/v3/api-docs",
								"/v3/api-docs/**",
								"/v3/api-docs.yaml",
								"/swagger-ui.html",
								"/swagger-ui/**")
								.permitAll()
						.requestMatchers("/admin/**").hasRole(ADMIN)
						.requestMatchers(HttpMethod.GET, "/api/products/**").hasAnyRole(ADMIN, CUSTOMER)
						.requestMatchers("/api/products/**").hasRole(ADMIN)
						.requestMatchers(HttpMethod.GET, "/api/inventory/**").hasAnyRole(ADMIN, CUSTOMER)
						.requestMatchers("/api/inventory/**").hasRole(ADMIN)
						.requestMatchers(HttpMethod.GET, "/api/production-orders/**").hasAnyRole(ADMIN, CUSTOMER)
						.requestMatchers("/api/production-orders/**").hasRole(ADMIN)
						.anyRequest().authenticated())
				.oauth2ResourceServer(oauth2 -> oauth2
						.authenticationEntryPoint(authenticationEntryPoint)
						.accessDeniedHandler(accessDeniedHandler)
						.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
				.build();
	}

	@Bean
	AuthenticationEntryPoint authenticationEntryPoint(ObjectMapper objectMapper) {
		BearerTokenAuthenticationEntryPoint delegate = new BearerTokenAuthenticationEntryPoint();
		return (request, response, exception) -> {
			delegate.commence(request, response, exception);
			writeSecurityProblem(
					objectMapper,
					request,
					response,
					HttpStatus.UNAUTHORIZED,
					"Authentication required",
					"AUTHENTICATION_REQUIRED",
					"A valid bearer token is required to access this resource");
		};
	}

	@Bean
	AccessDeniedHandler accessDeniedHandler(ObjectMapper objectMapper) {
		BearerTokenAccessDeniedHandler delegate = new BearerTokenAccessDeniedHandler();
		return (request, response, exception) -> {
			delegate.handle(request, response, exception);
			writeSecurityProblem(
					objectMapper,
					request,
					response,
					HttpStatus.FORBIDDEN,
					"Access denied",
					"ACCESS_DENIED",
					"The authenticated user does not have permission to access this resource");
		};
	}

	@Bean
	Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter(
			@Value("${haru.security.oauth2.client-id:haru-api}") String clientId) {

		JwtGrantedAuthoritiesConverter scopesConverter = new JwtGrantedAuthoritiesConverter();

		return jwt -> {
			Set<GrantedAuthority> authorities = new HashSet<>();
			Collection<GrantedAuthority> scopeAuthorities = scopesConverter.convert(jwt);

			if (scopeAuthorities != null) {
				authorities.addAll(scopeAuthorities);
			}

			addRealmRoles(jwt, authorities);
			addClientRoles(jwt, clientId, authorities);

			return new JwtAuthenticationToken(jwt, authorities, resolvePrincipalName(jwt));
		};
	}

	private static void addRealmRoles(Jwt jwt, Set<GrantedAuthority> authorities) {
		Object realmAccessClaim = jwt.getClaim("realm_access");
		if (realmAccessClaim instanceof Map<?, ?> realmAccess) {
			addRoles(realmAccess.get("roles"), authorities);
		}
	}

	private static void addClientRoles(Jwt jwt, String clientId, Set<GrantedAuthority> authorities) {
		Object resourceAccessClaim = jwt.getClaim("resource_access");
		if (!(resourceAccessClaim instanceof Map<?, ?> resourceAccess)) {
			return;
		}

		if (resourceAccess.get(clientId) instanceof Map<?, ?> clientAccess) {
			addRoles(clientAccess.get("roles"), authorities);
		}
	}

	private static void addRoles(Object rolesClaim, Set<GrantedAuthority> authorities) {
		if (!(rolesClaim instanceof Collection<?> roles)) {
			return;
		}

		roles.stream()
				.filter(String.class::isInstance)
				.map(String.class::cast)
				.filter(role -> !role.isBlank())
				.map(SecurityConfig::roleAuthority)
				.forEach(authorities::add);
	}

	private static SimpleGrantedAuthority roleAuthority(String role) {
		String normalizedRole = role.startsWith(ROLE_PREFIX) ? role : ROLE_PREFIX + role;
		return new SimpleGrantedAuthority(normalizedRole.toUpperCase(Locale.ROOT));
	}

	private static String resolvePrincipalName(Jwt jwt) {
		String preferredUsername = jwt.getClaimAsString("preferred_username");
		return StringUtils.hasText(preferredUsername) ? preferredUsername : jwt.getSubject();
	}

	private static void writeSecurityProblem(
			ObjectMapper objectMapper,
			HttpServletRequest request,
			HttpServletResponse response,
			HttpStatus status,
			String title,
			String code,
			String message) throws IOException {
		ProblemDetail detail = ProblemDetail.forStatusAndDetail(status, message);
		detail.setTitle(title);
		detail.setType(URI.create("urn:haru:problem:" + code.toLowerCase(Locale.ROOT).replace('_', '-')));
		detail.setInstance(URI.create(request.getRequestURI()));
		detail.setProperty("code", code);
		detail.setProperty("timestamp", Instant.now());

		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		objectMapper.writeValue(response.getOutputStream(), detail);
	}
}
