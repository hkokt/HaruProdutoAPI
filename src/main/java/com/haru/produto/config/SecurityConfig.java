package com.haru.produto.config;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
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
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

	private static final String ADMIN = "ADMIN";
	private static final String CUSTOMER = "CUSTOMER";
	private static final String ROLE_PREFIX = "ROLE_";

	@Bean
	SecurityFilterChain securityFilterChain(
			HttpSecurity http,
			Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter) throws Exception {

		return http
				.csrf(AbstractHttpConfigurer::disable)
				.formLogin(AbstractHttpConfigurer::disable)
				.httpBasic(AbstractHttpConfigurer::disable)
				.logout(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
						.requestMatchers("/error").permitAll()
						.requestMatchers("/admin/**").hasRole(ADMIN)
						.requestMatchers(HttpMethod.GET, "/produtos/**").hasAnyRole(ADMIN, CUSTOMER)
						.requestMatchers("/produtos/**").hasRole(ADMIN)
						.anyRequest().authenticated())
				.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
				.build();
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
		Map<String, Object> realmAccess = jwt.getClaim("realm_access");

		if (realmAccess != null) {
			addRoles(realmAccess.get("roles"), authorities);
		}
	}

	private static void addClientRoles(Jwt jwt, String clientId, Set<GrantedAuthority> authorities) {
		Map<String, Object> resourceAccess = jwt.getClaim("resource_access");

		if (resourceAccess == null) {
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
		return preferredUsername != null ? preferredUsername : jwt.getSubject();
	}
}
