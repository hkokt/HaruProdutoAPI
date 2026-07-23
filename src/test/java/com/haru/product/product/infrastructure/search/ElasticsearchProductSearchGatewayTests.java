package com.haru.product.product.infrastructure.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.haru.product.product.application.dto.ProductDeletionResult;
import com.haru.product.product.application.dto.ProductSearchResultResponse;
import com.haru.product.product.application.exception.ProductSearchUnavailableException;
import com.haru.product.product.application.exception.ProductSearchVersionConflictException;
import com.haru.product.product.application.search.ProductSearchDocument;
import com.haru.product.product.domain.MeasurementUnit;
import com.haru.product.product.domain.ProductType;
import com.haru.product.shared.pagination.OffsetPageResponse;

class ElasticsearchProductSearchGatewayTests {

	private static final String ELASTICSEARCH_URL = "http://elasticsearch:9200";
	private static final String INDEX_NAME = "haru-products-v1";
	private static final String INDEX_URL = ELASTICSEARCH_URL + "/" + INDEX_NAME;

	private MockRestServiceServer server;
	private ElasticsearchProductSearchGateway gateway;

	@BeforeEach
	void setUp() {
		RestClient.Builder restClientBuilder = RestClient.builder();
		server = MockRestServiceServer.bindTo(restClientBuilder).build();
		gateway = new ElasticsearchProductSearchGateway(
				restClientBuilder,
				ELASTICSEARCH_URL + "/",
				INDEX_NAME);
	}

	@AfterEach
	void verifyRequests() {
		server.verify();
	}

	@Test
	void lazilyCreatesTheIndexAndPutsAVersionedProductDocument() {
		server.expect(requestTo(INDEX_URL))
				.andExpect(method(HttpMethod.HEAD))
				.andRespond(withStatus(HttpStatus.NOT_FOUND));
		server.expect(requestTo(INDEX_URL))
				.andExpect(method(HttpMethod.PUT))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.settings.analysis.normalizer.sku_normalizer.type").value("custom"))
				.andExpect(jsonPath("$.settings.analysis.normalizer.sku_normalizer.filter[0]").value("lowercase"))
				.andExpect(jsonPath("$.settings.analysis.normalizer.sku_normalizer.filter[1]").value("asciifolding"))
				.andExpect(jsonPath("$.mappings.dynamic").value("strict"))
				.andExpect(jsonPath("$.mappings.properties.name.analyzer").value("brazilian"))
				.andExpect(jsonPath("$.mappings.properties.description.analyzer").value("brazilian"))
				.andExpect(jsonPath("$.mappings.properties.sku.normalizer").value("sku_normalizer"))
				.andRespond(withStatus(HttpStatus.OK));
		server.expect(requestTo(INDEX_URL + "/_doc/42?version=8&version_type=external&refresh=wait_for"))
				.andExpect(method(HttpMethod.PUT))
				.andExpect(queryParam("version", "8"))
				.andExpect(queryParam("version_type", "external"))
				.andExpect(queryParam("refresh", "wait_for"))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
				.andExpect(jsonPath("$.id").value(42))
				.andExpect(jsonPath("$.name").value("Perfume Sakura"))
				.andExpect(jsonPath("$.description").value("Perfume floral"))
				.andExpect(jsonPath("$.sku").value("PERF-SAKURA-100ML"))
				.andExpect(jsonPath("$.type").value("FINISHED_PRODUCT"))
				.andExpect(jsonPath("$.defaultMeasurementUnit").value("UNIT"))
				.andExpect(jsonPath("$.active").value(true))
				.andExpect(jsonPath("$.databaseVersion").value(7))
				.andExpect(jsonPath("$.updatedAt").value("2026-07-23T12:00:00Z"))
				.andExpect(jsonPath("$.deleted").value(false))
				.andRespond(withStatus(HttpStatus.CREATED));

		gateway.put(liveDocument());
	}

	@Test
	void putsWithoutWaitingForRefreshAndRefreshesTheIndexExplicitly() {
		expectExistingIndex();
		server.expect(requestTo(INDEX_URL + "/_doc/42?version=8&version_type=external&refresh=false"))
				.andExpect(method(HttpMethod.PUT))
				.andExpect(queryParam("version", "8"))
				.andExpect(queryParam("version_type", "external"))
				.andExpect(queryParam("refresh", "false"))
				.andRespond(withStatus(HttpStatus.CREATED));
		expectExistingIndex();
		server.expect(requestTo(INDEX_URL + "/_refresh"))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withStatus(HttpStatus.OK));

		gateway.putWithoutRefresh(liveDocument());
		gateway.refresh();
	}

	@Test
	void exposesAnExternalVersionConflictForTheCallerToResolve() {
		expectExistingIndex();
		server.expect(requestTo(INDEX_URL + "/_doc/42?version=8&version_type=external&refresh=false"))
				.andExpect(method(HttpMethod.PUT))
				.andRespond(withStatus(HttpStatus.CONFLICT));

		assertThatThrownBy(() -> gateway.putWithoutRefresh(liveDocument()))
				.isInstanceOf(ProductSearchVersionConflictException.class)
				.hasMessage("Product search version conflict for product 42");
	}

	@Test
	void buildsANumericIdSearchAndParsesThePageResponse() {
		expectExistingIndex();
		server.expect(requestTo(INDEX_URL + "/_search"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.from").value(15))
				.andExpect(jsonPath("$.size").value(10))
				.andExpect(jsonPath("$.track_total_hits").value(true))
				.andExpect(jsonPath("$.query.bool.filter[0].term.deleted").value(false))
				.andExpect(jsonPath("$.query.bool.minimum_should_match").value(1))
				.andExpect(jsonPath("$.query.bool.should[0].term.id.value").value(42))
				.andExpect(jsonPath("$.query.bool.should[0].term.id.boost").value(20.0))
				.andExpect(jsonPath("$.query.bool.should[1].match.sku.query").value("42"))
				.andExpect(jsonPath("$.query.bool.should[4].multi_match.query").value("42"))
				.andExpect(jsonPath("$.sort[0]._score.order").value("desc"))
				.andExpect(jsonPath("$.sort[1].id.order").value("asc"))
				.andRespond(withSuccess(searchResponse(), MediaType.APPLICATION_JSON));

		OffsetPageResponse<ProductSearchResultResponse> response = gateway.search("42", 15, 10);

		assertThat(response.offset()).isEqualTo(15);
		assertThat(response.limit()).isEqualTo(10);
		assertThat(response.totalElements()).isEqualTo(21);
		assertThat(response.hasPrevious()).isTrue();
		assertThat(response.hasNext()).isTrue();
		assertThat(response.content()).singleElement().satisfies(product -> {
			assertThat(product.id()).isEqualTo(42L);
			assertThat(product.name()).isEqualTo("Perfume Sakura");
			assertThat(product.sku()).isEqualTo("PERF-SAKURA-100ML");
			assertThat(product.type()).isEqualTo(ProductType.FINISHED_PRODUCT);
			assertThat(product.defaultMeasurementUnit()).isEqualTo(MeasurementUnit.UNIT);
			assertThat(product.active()).isTrue();
			assertThat(product.score()).isEqualTo(19.75);
		});
	}

	@Test
	void buildsANameSearchWithBrazilianTextMatchingAndNoIdAlternative() {
		expectExistingIndex();
		server.expect(requestTo(INDEX_URL + "/_search"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.query.bool.should[0].term.id").doesNotHaveJsonPath())
				.andExpect(jsonPath("$.query.bool.should[0].match.sku.query").value("Perfume de Sakura"))
				.andExpect(jsonPath("$.query.bool.should[1].prefix.sku.value").value("perfume de sakura"))
				.andExpect(jsonPath("$.query.bool.should[2].match_phrase.name.query")
						.value("Perfume de Sakura"))
				.andExpect(jsonPath("$.query.bool.should[3].multi_match.query").value("Perfume de Sakura"))
				.andExpect(jsonPath("$.query.bool.should[3].multi_match.fields[0]").value("name^5"))
				.andExpect(jsonPath("$.query.bool.should[3].multi_match.fields[1]").value("description"))
				.andExpect(jsonPath("$.query.bool.should[3].multi_match.fuzziness").value("AUTO"))
				.andExpect(jsonPath("$.query.bool.should[3].multi_match.operator").value("and"))
				.andRespond(withSuccess(emptySearchResponse(), MediaType.APPLICATION_JSON));

		OffsetPageResponse<ProductSearchResultResponse> response = gateway.search(
				"Perfume de Sakura", 0, 20);

		assertThat(response.content()).isEmpty();
		assertThat(response.totalElements()).isZero();
		assertThat(response.hasNext()).isFalse();
	}

	@Test
	void stopsPaginationAtTheAccessibleResultWindowWithoutHidingTheRealTotal() {
		expectExistingIndex();
		server.expect(requestTo(INDEX_URL + "/_search"))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withSuccess(
						searchResponse().replace("\"value\": 21", "\"value\": 25000"),
						MediaType.APPLICATION_JSON));

		OffsetPageResponse<ProductSearchResultResponse> response = gateway.search("Sakura", 9_999, 1);

		assertThat(response.totalElements()).isEqualTo(25_000);
		assertThat(response.hasPrevious()).isTrue();
		assertThat(response.hasNext()).isFalse();
	}

	@Test
	void browsesLiveProductsWithAStableDescendingIdSort() {
		expectExistingIndex();
		server.expect(requestTo(INDEX_URL + "/_search"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.from").value(0))
				.andExpect(jsonPath("$.size").value(20))
				.andExpect(jsonPath("$.query.bool.filter[0].term.deleted").value(false))
				.andExpect(jsonPath("$.query.bool.should").doesNotHaveJsonPath())
				.andExpect(jsonPath("$.sort[0].id.order").value("desc"))
				.andRespond(withSuccess(emptySearchResponse(), MediaType.APPLICATION_JSON));

		var response = gateway.search("", 0, 20);

		assertThat(response.content()).isEmpty();
		assertThat(response.hasPrevious()).isFalse();
		assertThat(response.hasNext()).isFalse();
	}

	@Test
	void buildsASkuSearchWithNormalizedPrefixAndExactKeywordMatch() {
		expectExistingIndex();
		server.expect(requestTo(INDEX_URL + "/_search"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.query.bool.should[0].match.sku.query").value("PERF-SAKURA"))
				.andExpect(jsonPath("$.query.bool.should[0].match.sku.boost").value(12.0))
				.andExpect(jsonPath("$.query.bool.should[1].prefix.sku.value").value("perf-sakura"))
				.andExpect(jsonPath("$.query.bool.should[1].prefix.sku.case_insensitive").value(true))
				.andExpect(jsonPath("$.query.bool.should[1].prefix.sku.boost").value(8.0))
				.andExpect(jsonPath("$.query.bool.filter[0].term.deleted").value(false))
				.andRespond(withSuccess(emptySearchResponse(), MediaType.APPLICATION_JSON));

		gateway.search("PERF-SAKURA", 0, 20);
	}

	@Test
	void putsADeletionTombstoneUsingTheReservedFenceVersion() {
		expectExistingIndex();
		server.expect(requestTo(INDEX_URL + "/_doc/42?version=9000000000000000000&version_type=external&refresh=wait_for"))
				.andExpect(method(HttpMethod.PUT))
				.andExpect(queryParam("version", "9000000000000000000"))
				.andExpect(queryParam("version_type", "external"))
				.andExpect(queryParam("refresh", "wait_for"))
				.andExpect(jsonPath("$.id").value(42))
				.andExpect(jsonPath("$.databaseVersion").value(8))
				.andExpect(jsonPath("$.updatedAt").value("2026-07-23T13:00:00Z"))
				.andExpect(jsonPath("$.deleted").value(true))
				.andExpect(jsonPath("$.name").doesNotExist())
				.andExpect(jsonPath("$.sku").doesNotExist())
				.andRespond(withStatus(HttpStatus.OK));

		gateway.put(ProductSearchDocument.tombstone(new ProductDeletionResult(
				42L,
				8L,
				Instant.parse("2026-07-23T13:00:00Z"))));
	}

	@Test
	void multiGetReturnsOnlyLiveFoundDocumentVersions() {
		expectExistingIndex();
		server.expect(requestTo(INDEX_URL + "/_mget"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.ids[0]").value("11"))
				.andExpect(jsonPath("$.ids[1]").value("12"))
				.andExpect(jsonPath("$.ids[2]").value("13"))
				.andRespond(withSuccess("""
						{
						  "docs": [
						    {
						      "_id": "11",
						      "found": true,
						      "_source": {"databaseVersion": 7, "deleted": false}
						    },
						    {
						      "_id": "12",
						      "found": true,
						      "_source": {"databaseVersion": 8, "deleted": true}
						    },
						    {"_id": "13", "found": false}
						  ]
						}
						""", MediaType.APPLICATION_JSON));

		Map<Long, Long> versions = gateway.findDatabaseVersions(List.of(11L, 12L, 13L));

		assertThat(versions).containsExactly(Map.entry(11L, 7L));
	}

	@Test
	void pagesLiveDocumentsForOrphanReconciliation() {
		expectExistingIndex();
		server.expect(requestTo(INDEX_URL + "/_search"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.size").value(200))
				.andExpect(jsonPath("$.track_total_hits").value(false))
				.andExpect(jsonPath("$._source[0]").value("id"))
				.andExpect(jsonPath("$._source[1]").value("databaseVersion"))
				.andExpect(jsonPath("$.query.term.deleted").value(false))
				.andExpect(jsonPath("$.sort[0].id.order").value("asc"))
				.andExpect(jsonPath("$.search_after[0]").value(42))
				.andRespond(withSuccess("""
						{
						  "hits": {
						    "hits": [
						      {"_source": {"id": 43, "databaseVersion": 8}},
						      {"_source": {"id": 44, "databaseVersion": 2}}
						    ]
						  }
						}
						""", MediaType.APPLICATION_JSON));

		var entries = gateway.findLiveDocumentsAfter(42L, 200);

		assertThat(entries)
				.extracting(entry -> entry.id() + ":" + entry.databaseVersion())
				.containsExactly("43:8", "44:2");
	}

	@Test
	void translatesElasticsearchServerErrorsIntoSearchUnavailable() {
		expectExistingIndex();
		server.expect(requestTo(INDEX_URL + "/_search"))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withStatus(HttpStatus.BAD_GATEWAY));

		assertThatThrownBy(() -> gateway.search("Sakura", 0, 20))
				.isInstanceOf(ProductSearchUnavailableException.class)
				.hasMessage("Product search is temporarily unavailable")
				.hasCauseInstanceOf(RuntimeException.class);
	}

	private void expectExistingIndex() {
		server.expect(requestTo(INDEX_URL))
				.andExpect(method(HttpMethod.HEAD))
				.andRespond(withStatus(HttpStatus.OK));
	}

	private static ProductSearchDocument liveDocument() {
		return new ProductSearchDocument(
				42L,
				"Perfume Sakura",
				"Perfume floral",
				"PERF-SAKURA-100ML",
				ProductType.FINISHED_PRODUCT,
				MeasurementUnit.UNIT,
				true,
				7L,
				Instant.parse("2026-07-23T12:00:00Z"),
				false);
	}

	private static String searchResponse() {
		return """
				{
				  "hits": {
				    "total": {"value": 21, "relation": "eq"},
				    "hits": [
				      {
				        "_id": "42",
				        "_score": 19.75,
				        "_source": {
				          "id": 42,
				          "name": "Perfume Sakura",
				          "sku": "PERF-SAKURA-100ML",
				          "type": "FINISHED_PRODUCT",
				          "defaultMeasurementUnit": "UNIT",
				          "active": true,
				          "deleted": false
				        }
				      }
				    ]
				  }
				}
				""";
	}

	private static String emptySearchResponse() {
		return """
				{
				  "hits": {
				    "total": {"value": 0, "relation": "eq"},
				    "hits": []
				  }
				}
				""";
	}
}
