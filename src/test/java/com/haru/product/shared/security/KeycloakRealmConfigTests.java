package com.haru.product.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.json.JsonParserFactory;

class KeycloakRealmConfigTests {

	private static final Path REALM_FILE = Path.of("docker", "Keycloak", "realm", "haru-realm.json");
	private static final Path APPLICATION_PROPERTIES =
			Path.of("src", "main", "resources", "application.properties");
	private static final Path COMPOSE_FILE = Path.of("docker", "docker-compose.yml");
	private static final Path ENV_EXAMPLE_FILE = Path.of("docker", ".env.example");

	@Test
	void importsHaruRealmWithCustomerSelfRegistration() throws IOException {
		Map<String, Object> realm = readRealm();

		assertThat(realm)
				.containsEntry("realm", "haru")
				.containsEntry("displayName", "Haru Product API")
				.containsEntry("enabled", true)
				.containsEntry("registrationAllowed", true)
				.containsEntry("loginWithEmailAllowed", true);

		assertThat(stringValues(realm.get("defaultRoles")))
				.contains("customer");
	}

	@Test
	void importsHaruClientWithLoginAndRegistrationFlows() throws IOException {
		Map<String, Object> realm = readRealm();
		Map<String, Object> client = findClient(realm, "haru-api");

		assertThat(client)
				.containsEntry("enabled", true)
				.containsEntry("protocol", "openid-connect")
				.containsEntry("publicClient", true)
				.containsEntry("standardFlowEnabled", true)
				.containsEntry("directAccessGrantsEnabled", true);

		assertThat(stringValues(client.get("redirectUris")))
				.contains(
						"http://localhost/*",
						"https://bookish-fiesta-vjv9rggj4jjcxrvw-80.app.github.dev/*");
		assertThat(stringValues(client.get("webOrigins")))
				.contains(
						"http://localhost",
						"https://bookish-fiesta-vjv9rggj4jjcxrvw-80.app.github.dev");
		assertThat(stringValues(client.get("defaultClientScopes"))).contains("roles");
	}

	@Test
	void addsTheHaruApiAudienceToAccessTokens() throws IOException {
		Map<String, Object> realm = readRealm();
		Map<String, Object> client = findClient(realm, "haru-api");
		Map<String, Object> audienceMapper = findProtocolMapper(client, "haru-api-audience");
		Map<String, Object> mapperConfig = objectValue(audienceMapper.get("config"));

		assertThat(audienceMapper)
				.containsEntry("protocol", "openid-connect")
				.containsEntry("protocolMapper", "oidc-audience-mapper")
				.containsEntry("consentRequired", false);
		assertThat(mapperConfig)
				.containsEntry("included.client.audience", "haru-api")
				.containsEntry("access.token.claim", "true")
				.containsEntry("id.token.claim", "false")
				.containsEntry("introspection.token.claim", "true");
	}

	@Test
	void keepsTheRequiredAudienceSynchronizedAcrossApplicationAndDockerConfiguration()
			throws IOException {
		assertThat(Files.readString(APPLICATION_PROPERTIES))
				.contains("spring.security.oauth2.resourceserver.jwt.audiences="
						+ "${KEYCLOAK_AUDIENCE:haru-api}");
		assertThat(Files.readString(COMPOSE_FILE))
				.contains("SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_AUDIENCES: "
						+ "${KEYCLOAK_AUDIENCE:-haru-api}");
		assertThat(Files.readString(ENV_EXAMPLE_FILE))
				.contains("KEYCLOAK_AUDIENCE=haru-api");
	}

	@Test
	void importsAdminAndCustomerRealmRoles() throws IOException {
		Map<String, Object> realm = readRealm();
		Map<String, Object> roles = objectValue(realm.get("roles"));
		Collection<Map<String, Object>> realmRoles = objectValues(roles.get("realm"));

		assertThat(stringValues(roleNames(realmRoles)))
				.containsExactlyInAnyOrder("admin", "customer");
		assertThat(realmRoles)
				.extracting(role -> role.get("description"))
				.containsExactlyInAnyOrder("API administrator", "API customer");
	}

	private static Map<String, Object> readRealm() throws IOException {
		String json = Files.readString(REALM_FILE);
		return JsonParserFactory.getJsonParser().parseMap(json);
	}

	private static Map<String, Object> findClient(Map<String, Object> realm, String clientId) {
		return objectValues(realm.get("clients")).stream()
				.filter(client -> clientId.equals(client.get("clientId")))
				.findFirst()
				.orElseThrow(() -> new AssertionError("Client not found: " + clientId));
	}

	private static Map<String, Object> findProtocolMapper(
			Map<String, Object> client,
			String mapperName) {
		return objectValues(client.get("protocolMappers")).stream()
				.filter(mapper -> mapperName.equals(mapper.get("name")))
				.findFirst()
				.orElseThrow(() -> new AssertionError("Protocol mapper not found: " + mapperName));
	}

	private static Collection<Object> roleNames(Object roles) {
		return objectValues(roles).stream()
				.map(role -> role.get("name"))
				.toList();
	}

	@SuppressWarnings("unchecked")
	private static Collection<Map<String, Object>> objectValues(Object value) {
		assertThat(value).isInstanceOf(Collection.class);
		return ((Collection<?>) value).stream()
				.map(KeycloakRealmConfigTests::objectValue)
				.toList();
	}

	private static Collection<String> stringValues(Object value) {
		assertThat(value).isInstanceOf(Collection.class);
		return ((Collection<?>) value).stream()
				.map(String.class::cast)
				.toList();
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> objectValue(Object value) {
		assertThat(value).isInstanceOf(Map.class);
		return (Map<String, Object>) value;
	}
}
