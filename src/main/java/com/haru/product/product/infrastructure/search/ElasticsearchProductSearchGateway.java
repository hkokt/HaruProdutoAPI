package com.haru.product.product.infrastructure.search;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import tools.jackson.databind.JsonNode;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.haru.product.product.application.dto.ProductSearchPageResponse;
import com.haru.product.product.application.dto.ProductSearchResultResponse;
import com.haru.product.product.application.exception.ProductSearchUnavailableException;
import com.haru.product.product.application.exception.ProductSearchVersionConflictException;
import com.haru.product.product.application.search.ProductSearchDocument;
import com.haru.product.product.application.search.ProductSearchGateway;
import com.haru.product.product.application.search.ProductSearchIndexEntry;
import com.haru.product.product.domain.MeasurementUnit;
import com.haru.product.product.domain.ProductType;

@Component
public class ElasticsearchProductSearchGateway implements ProductSearchGateway {

	private static final Pattern INDEX_NAME = Pattern.compile("[a-z0-9][a-z0-9._-]{0,254}");
	private static final int MAX_RESULT_WINDOW = 10_000;
	private static final String UNAVAILABLE_MESSAGE = "Product search is temporarily unavailable";

	private final RestClient restClient;
	private final String indexName;

	@Autowired
	public ElasticsearchProductSearchGateway(
			@Value("${haru.search.elasticsearch.url:http://localhost:9200}") String elasticsearchUrl,
			@Value("${haru.search.elasticsearch.products-index:haru-products-v1}") String indexName,
			@Value("${haru.search.elasticsearch.connect-timeout:2s}") Duration connectTimeout,
			@Value("${haru.search.elasticsearch.read-timeout:5s}") Duration readTimeout) {
		this(
				configureTimeouts(RestClient.builder(), connectTimeout, readTimeout),
				elasticsearchUrl,
				indexName);
	}

	ElasticsearchProductSearchGateway(
			RestClient.Builder restClientBuilder,
			String elasticsearchUrl,
			String indexName) {
		this.indexName = validateIndexName(indexName);
		this.restClient = restClientBuilder.baseUrl(normalizeBaseUrl(elasticsearchUrl)).build();
	}

	@Override
	public void put(ProductSearchDocument document) {
		put(document, "wait_for");
	}

	@Override
	public void putWithoutRefresh(ProductSearchDocument document) {
		put(document, "false");
	}

	@Override
	public void refresh() {
		try {
			ensureIndexExists();
			HttpStatusCode status = restClient.post()
					.uri(uriBuilder -> uriBuilder.pathSegment(indexName, "_refresh").build())
					.exchange((request, response) -> response.getStatusCode());
			requireSuccessful(status);
		}
		catch (ProductSearchUnavailableException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw unavailable(exception);
		}
	}

	private void put(ProductSearchDocument document, String refreshPolicy) {
		try {
			ensureIndexExists();
			HttpStatusCode status = restClient.put()
					.uri(uriBuilder -> uriBuilder
							.pathSegment(indexName, "_doc", document.id().toString())
							.queryParam("version", document.externalVersion())
							.queryParam("version_type", "external")
							.queryParam("refresh", refreshPolicy)
							.build())
					.body(document)
					.exchange((request, response) -> response.getStatusCode());
			if (status.value() == 409) {
				throw new ProductSearchVersionConflictException(document.id());
			}
			requireSuccessful(status);
		}
		catch (ProductSearchVersionConflictException exception) {
			throw exception;
		}
		catch (ProductSearchUnavailableException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw unavailable(exception);
		}
	}

	@Override
	public ProductSearchPageResponse search(String query, int page, int size) {
		try {
			ensureIndexExists();
			int from = Math.multiplyExact(page, size);
			if (from < 0 || size < 1 || from + size > MAX_RESULT_WINDOW) {
				throw new IllegalArgumentException("The requested product search page is outside the result window");
			}

			Map<String, Object> request = new LinkedHashMap<>();
			request.put("from", from);
			request.put("size", size);
			request.put("track_total_hits", true);
			request.put("query", searchQuery(query));
			request.put("sort", List.of(
					Map.of("_score", Map.of("order", "desc")),
					Map.of("id", Map.of("order", "asc"))));

			JsonNode response = restClient.post()
					.uri(uriBuilder -> uriBuilder.pathSegment(indexName, "_search").build())
					.body(request)
					.retrieve()
					.body(JsonNode.class);
			return toSearchResponse(response, page, size);
		}
		catch (ProductSearchUnavailableException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw unavailable(exception);
		}
	}

	@Override
	public Map<Long, Long> findDatabaseVersions(Collection<Long> productIds) {
		if (productIds.isEmpty()) {
			return Map.of();
		}
		try {
			ensureIndexExists();
			JsonNode response = restClient.post()
					.uri(uriBuilder -> uriBuilder.pathSegment(indexName, "_mget").build())
					.body(Map.of("ids", productIds.stream().map(String::valueOf).toList()))
					.retrieve()
					.body(JsonNode.class);
			return parseDocumentVersions(response);
		}
		catch (ProductSearchUnavailableException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw unavailable(exception);
		}
	}

	@Override
	public List<ProductSearchIndexEntry> findLiveDocumentsAfter(Long productId, int size) {
		try {
			ensureIndexExists();
			if (size < 1 || size > 1_000) {
				throw new IllegalArgumentException("Product search reconciliation batch size must be between 1 and 1000");
			}

			Map<String, Object> request = new LinkedHashMap<>();
			request.put("size", size);
			request.put("track_total_hits", false);
			request.put("_source", List.of("id", "databaseVersion"));
			request.put("query", Map.of("term", Map.of("deleted", false)));
			request.put("sort", List.of(Map.of("id", Map.of("order", "asc"))));
			if (productId != null) {
				request.put("search_after", List.of(productId));
			}

			JsonNode response = restClient.post()
					.uri(uriBuilder -> uriBuilder.pathSegment(indexName, "_search").build())
					.body(request)
					.retrieve()
					.body(JsonNode.class);
			return parseLiveDocuments(response);
		}
		catch (ProductSearchUnavailableException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw unavailable(exception);
		}
	}

	@Override
	public long countLiveDocuments() {
		try {
			ensureIndexExists();
			JsonNode response = restClient.post()
					.uri(uriBuilder -> uriBuilder.pathSegment(indexName, "_count").build())
					.body(Map.of("query", Map.of("term", Map.of("deleted", false))))
					.retrieve()
					.body(JsonNode.class);
			if (response == null || !response.path("count").isIntegralNumber()) {
				throw new IllegalStateException("Elasticsearch returned an invalid count response");
			}
			return response.path("count").asLong();
		}
		catch (ProductSearchUnavailableException exception) {
			throw exception;
		}
		catch (RuntimeException exception) {
			throw unavailable(exception);
		}
	}

	private void ensureIndexExists() {
		HttpStatusCode status = restClient.head()
				.uri(uriBuilder -> uriBuilder.pathSegment(indexName).build())
				.exchange((request, response) -> response.getStatusCode());
		if (status.is2xxSuccessful()) {
			return;
		}
		if (status.value() != 404) {
			throw unavailable(null);
		}

		HttpStatusCode creationStatus = restClient.put()
				.uri(uriBuilder -> uriBuilder.pathSegment(indexName).build())
				.body(indexDefinition())
				.exchange((request, response) -> response.getStatusCode());
		if (creationStatus.is2xxSuccessful()) {
			return;
		}

		// Another API replica may have created the index after this replica's HEAD request.
		HttpStatusCode raceCheckStatus = restClient.head()
				.uri(uriBuilder -> uriBuilder.pathSegment(indexName).build())
				.exchange((request, response) -> response.getStatusCode());
		if (!raceCheckStatus.is2xxSuccessful()) {
			throw unavailable(null);
		}
	}

	private static Map<String, Object> searchQuery(String query) {
		List<Map<String, Object>> alternatives = new ArrayList<>();
		parsePositiveLong(query).ifPresent(id -> alternatives.add(Map.of(
				"term", Map.of("id", Map.of("value", id, "boost", 20.0)))));
		alternatives.add(Map.of(
				"match", Map.of("sku", Map.of("query", query, "boost", 12.0))));
		alternatives.add(Map.of(
				"prefix", Map.of("sku", Map.of(
						"value", query.toLowerCase(Locale.ROOT),
						"case_insensitive", true,
						"boost", 8.0))));
		alternatives.add(Map.of(
				"match_phrase", Map.of("name", Map.of("query", query, "boost", 8.0))));
		alternatives.add(Map.of(
				"multi_match", Map.of(
						"query", query,
						"fields", List.of("name^5", "description"),
						"type", "best_fields",
						"operator", "and",
						"fuzziness", "AUTO",
						"prefix_length", 1)));

		return Map.of(
				"bool", Map.of(
						"filter", List.of(Map.of("term", Map.of("deleted", false))),
						"should", alternatives,
						"minimum_should_match", 1));
	}

	private static Optional<Long> parsePositiveLong(String query) {
		try {
			long id = Long.parseLong(query);
			return id > 0 ? Optional.of(id) : Optional.empty();
		}
		catch (NumberFormatException exception) {
			return Optional.empty();
		}
	}

	private static ProductSearchPageResponse toSearchResponse(JsonNode response, int page, int size) {
		if (response == null || !response.path("hits").path("hits").isArray()) {
			throw new IllegalStateException("Elasticsearch returned an invalid search response");
		}

		JsonNode hits = response.path("hits");
		long totalElements = hits.path("total").path("value").asLong();
		List<ProductSearchResultResponse> content = new ArrayList<>();
		for (JsonNode hit : hits.path("hits")) {
			JsonNode source = hit.path("_source");
			content.add(new ProductSearchResultResponse(
					source.path("id").asLong(),
					requiredText(source, "name"),
					requiredText(source, "sku"),
					ProductType.valueOf(requiredText(source, "type")),
					MeasurementUnit.valueOf(requiredText(source, "defaultMeasurementUnit")),
					source.path("active").asBoolean(),
					hit.path("_score").asDouble()));
		}

		long totalPageCount = totalElements == 0 ? 0 : ((totalElements - 1) / size) + 1;
		long accessiblePageCount = MAX_RESULT_WINDOW / size;
		return new ProductSearchPageResponse(
				content,
				page,
				size,
				totalElements,
				(int) Math.min(totalPageCount, accessiblePageCount));
	}

	private static Map<Long, Long> parseDocumentVersions(JsonNode response) {
		if (response == null || !response.path("docs").isArray()) {
			throw new IllegalStateException("Elasticsearch returned an invalid multi-get response");
		}
		Map<Long, Long> versions = new LinkedHashMap<>();
		for (JsonNode document : response.path("docs")) {
			if (!document.path("found").asBoolean()) {
				continue;
			}
			JsonNode source = document.path("_source");
			if (source.path("deleted").asBoolean()) {
				continue;
			}
			versions.put(
					Long.parseLong(document.path("_id").asString()),
					source.path("databaseVersion").asLong());
		}
		return Map.copyOf(versions);
	}

	private static List<ProductSearchIndexEntry> parseLiveDocuments(JsonNode response) {
		JsonNode hits = response == null ? null : response.path("hits").path("hits");
		if (hits == null || !hits.isArray()) {
			throw new IllegalStateException("Elasticsearch returned an invalid reconciliation response");
		}
		List<ProductSearchIndexEntry> entries = new ArrayList<>();
		for (JsonNode hit : hits) {
			JsonNode source = hit.path("_source");
			if (!source.path("id").isIntegralNumber()
					|| !source.path("databaseVersion").isIntegralNumber()) {
				throw new IllegalStateException("Elasticsearch reconciliation response is missing version data");
			}
			entries.add(new ProductSearchIndexEntry(
					source.path("id").asLong(),
					source.path("databaseVersion").asLong()));
		}
		return List.copyOf(entries);
	}

	private static String requiredText(JsonNode node, String field) {
		String value = node.path(field).asString();
		if (value.isBlank()) {
			throw new IllegalStateException("Elasticsearch response is missing field " + field);
		}
		return value;
	}

	private static void requireSuccessful(HttpStatusCode status) {
		if (!status.is2xxSuccessful()) {
			throw unavailable(null);
		}
	}

	private static Map<String, Object> indexDefinition() {
		Map<String, Object> properties = new LinkedHashMap<>();
		properties.put("id", Map.of("type", "long"));
		properties.put("name", Map.of(
				"type", "text",
				"analyzer", "brazilian",
				"fields", Map.of("keyword", Map.of("type", "keyword", "ignore_above", 150))));
		properties.put("description", Map.of("type", "text", "analyzer", "brazilian"));
		properties.put("sku", Map.of("type", "keyword", "normalizer", "sku_normalizer"));
		properties.put("type", Map.of("type", "keyword"));
		properties.put("defaultMeasurementUnit", Map.of("type", "keyword"));
		properties.put("active", Map.of("type", "boolean"));
		properties.put("databaseVersion", Map.of("type", "long"));
		properties.put("updatedAt", Map.of("type", "date"));
		properties.put("deleted", Map.of("type", "boolean"));

		return Map.of(
				"settings", Map.of(
						"analysis", Map.of(
								"normalizer", Map.of(
										"sku_normalizer", Map.of(
												"type", "custom",
												"filter", List.of("lowercase", "asciifolding"))))),
				"mappings", Map.of(
						"dynamic", "strict",
						"properties", properties));
	}

	private static String normalizeBaseUrl(String value) {
		String normalized = value == null ? "" : value.strip();
		while (normalized.endsWith("/")) {
			normalized = normalized.substring(0, normalized.length() - 1);
		}
		if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
			throw new IllegalArgumentException("Elasticsearch URL must use HTTP or HTTPS");
		}
		return normalized;
	}

	private static RestClient.Builder configureTimeouts(
			RestClient.Builder builder,
			Duration connectTimeout,
			Duration readTimeout) {
		if (connectTimeout.isNegative() || connectTimeout.isZero()
				|| readTimeout.isNegative() || readTimeout.isZero()) {
			throw new IllegalArgumentException("Elasticsearch timeouts must be positive");
		}
		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(connectTimeout)
				.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(readTimeout);
		return builder.requestFactory(requestFactory);
	}

	private static String validateIndexName(String value) {
		String normalized = value == null ? "" : value.strip();
		if (!INDEX_NAME.matcher(normalized).matches()) {
			throw new IllegalArgumentException("Invalid Elasticsearch product index name");
		}
		return normalized;
	}

	private static ProductSearchUnavailableException unavailable(Throwable cause) {
		return cause == null
				? new ProductSearchUnavailableException(UNAVAILABLE_MESSAGE)
				: new ProductSearchUnavailableException(UNAVAILABLE_MESSAGE, cause);
	}
}
