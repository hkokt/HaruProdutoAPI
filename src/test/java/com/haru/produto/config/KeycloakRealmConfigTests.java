package com.haru.produto.config;

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

	@Test
	void importsHaruRealmWithCustomerSelfRegistration() throws IOException {
		Map<String, Object> realm = readRealm();

		assertThat(realm)
				.containsEntry("realm", "haru")
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
	void importsAdminAndCustomerRealmRoles() throws IOException {
		Map<String, Object> realm = readRealm();
		Map<String, Object> roles = objectValue(realm.get("roles"));

		assertThat(stringValues(roleNames(roles.get("realm"))))
				.containsExactlyInAnyOrder("admin", "customer");
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
