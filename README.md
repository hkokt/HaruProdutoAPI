# HaruProductAPI

Local infrastructure for a Java 25 Spring Boot API with PostgreSQL,
Elasticsearch product search, JWT authentication through Keycloak, and Nginx
as a reverse proxy/load balancer.

## Local development

The application Dockerfile expects a Maven project with `pom.xml` and `src/` at
the repository root.

```bash
cp docker/.env.example docker/.env
docker compose --env-file docker/.env -f docker/docker-compose.yml up --build
```

Available services:

- API through Nginx: `http://localhost/`
- Keycloak through Nginx: `http://localhost/auth/`
- Keycloak administration console: `http://localhost/auth/admin/`
- Swagger UI: `http://localhost/swagger-ui.html`
- OpenAPI JSON: `http://localhost/v3/api-docs`
- OpenAPI YAML: `http://localhost/v3/api-docs.yaml`
- PostgreSQL: `localhost:5432`
- Elasticsearch (Docker network only): `http://elasticsearch:9200`
- Nginx health check: `http://localhost/nginx-health`

Nginx distributes requests between `api-1` and `api-2` using `least_conn`. The
`haru` realm and public `haru-api` client are imported automatically during the
first startup. Elasticsearch is available only to containers on the `backend`
network at `http://elasticsearch:9200`; it is not published on a host port. Its
data is persisted in the `elasticsearch_data` volume. The local defaults cap it
at one CPU and 1 GiB of memory with a 512 MiB JVM heap; these values can be
overridden through `docker/.env`. Replace every password in `.env` before using
this setup outside local development.

## API documentation

The OpenAPI description and Swagger UI are public so the browser can load the
documentation. Business endpoints remain protected by JWT authorization. In
Swagger UI, select **Authorize** and paste a Keycloak access token into the
`bearerAuth` field; Swagger UI adds the `Bearer` prefix to requests.

Only paths under `/api/**` are included in the generated contract. Internal
routes such as `/error`, `/admin/**`, Keycloak, and the Nginx health endpoint
are not part of the OpenAPI description.

## Automatic product SKUs

The API assigns an immutable SKU when a product is created. Create and update
requests do not accept an SKU; the generated value is returned in the product
response:

```text
PRD-0000000001
```

PostgreSQL owns the bounded, non-cycling sequence used by both API replicas.
The sequence is initialized above any existing SKU in the reserved
`PRD-##########` namespace. Sequence values are not rolled back, so gaps are
expected and must not be interpreted as missing products. Existing user-defined
SKUs are preserved.

The SKU is protected from updates by the HTTP contract, JPA mapping, and a
PostgreSQL trigger. The case-insensitive unique index remains the final
integrity guarantee. Product IDs remain internal persistence identifiers; SKUs
are stable catalog identifiers.

## Product search

Authenticated customers and administrators can search the product catalog by
name, numeric ID, or SKU:

```text
GET /api/products/search?q=sakura&offset=0&limit=20
```

The query is optional; an empty value lists the complete catalog. The response
uses the common offset envelope: `content`, `offset`, `limit`, `totalElements`,
`hasPrevious`, and `hasNext`. `offset` defaults to 0, while `limit` defaults to
20 and accepts at most 50 items. Product search results contain the product ID,
name, SKU, type, default measurement unit, active state, and relevance score.
The index uses the Elasticsearch Brazilian analyzer for product text,
`search_as_you_type` name fields for autocomplete prefixes, exact and prefix
matching for SKU, and a boosted exact match for numeric IDs. The current index
generation is `haru-products-v2`; changing an incompatible mapping requires a
new index generation followed by reindexing from PostgreSQL.

The main inventory and production searches use the same server-side pagination
contract:

```text
GET /api/inventory/products/search?q={name|id|sku}&offset=0&limit=20
GET /api/production-orders/search?q={order|product|name|sku}&status={status}&offset=0&limit=20
```

Inventory results add the current available quantity and lot count for each
catalog product. Production results can be filtered by status. Product and
inventory catalog searches respect Elasticsearch's 10,000-result window.

PostgreSQL remains the source of truth. Product create, update, and delete
operations follow this sequence:

1. The database transaction commits.
2. The API sends a synchronous, versioned `PUT` to Elasticsearch.
3. The HTTP product response is returned.

Live Elasticsearch external versions are derived from the JPA product version
and use strict external versioning, which prevents an older or equal-version
retry from replacing a newer document. Deletes write a tombstone through the
same `PUT` path at a reserved high external version, and searches filter
tombstones out. Product IDs are generated and never reused, so the tombstone
acts as a permanent fence against delayed live writes. Composition changes also
refresh the parent document because they advance the parent product version. If
indexing fails after the database commit, the API logs the failure and preserves
the successful database operation; it never reports the committed write as
rolled back. A search request returns the sanitized
`PRODUCT_SEARCH_UNAVAILABLE` problem with HTTP 503 when Elasticsearch cannot be
used.

If PostgreSQL is restored or reseeded in a way that reuses old product IDs,
recreate the Elasticsearch product index as part of the same operation before
reindexing it.

The Docker profile enables a startup reindex so products already stored in a
persisted PostgreSQL volume become searchable. This behavior is controlled by
`ELASTICSEARCH_REINDEX_ON_STARTUP`; the application default is disabled outside
the Docker environment.

Administrators can validate and repair the index with the internal endpoints:

```text
GET  /admin/search/products/validation
POST /admin/search/products/reindex
POST /admin/search/products/reconcile
```

Validation compares database and indexed versions and reports matching,
missing, stale, and orphan counts. Reindex writes every current database
product again and repairs missing or stale documents. Reconcile pages live
index documents and writes newer tombstones for IDs that no longer exist in
PostgreSQL. Run validation again after either repair operation. These routes
require the `admin` realm or client role and are intentionally excluded from
OpenAPI.

To remove all persisted local data and initialize the databases and search
index again:

```bash
docker compose --env-file docker/.env -f docker/docker-compose.yml down -v
```
