# Project context for Codex

## Overview

HaruProductAPI is a single-module Maven project for a Spring Boot API. Its local
infrastructure combines two application instances, PostgreSQL, Keycloak, and
Nginx.

Current repository state:

- Java 25, Spring Boot 4.1.0, and Maven.
- The Product module implements products and Bill of Materials (BOM), including
  JPA entities, application DTOs and service, Spring Data repositories, HTTP
  endpoints, error handling, and a Flyway migration.
- The Inventory module implements lots, immutable stock movements, entry and
  adjustment operations, availability queries, and transactional FEFO
  consumption.
- The Production module implements production-order lifecycle, BOM
  multiplication, transactional FEFO component consumption, final-lot entry,
  immutable trace records, and concurrency-safe completion.
- `/api/products/**` is a real application API. `/admin/status` still exists only in
  the security test controller.

Do not assume CRUD or persistence functionality that is not present in the
source tree. Inspect the current repository before implementing changes.

All new source code, identifiers, routes, test names, configuration names, and
project documentation must be written in English. Do not reintroduce
non-English domain names.

## Main stack

- Spring MVC and Validation for HTTP.
- Spring Security and OAuth2 Resource Server for JWT authentication.
- Springdoc OpenAPI and Swagger UI for generated API documentation.
- Spring Data JPA, Flyway, and PostgreSQL for persistence.
- Keycloak as the identity provider and token issuer.
- Nginx as the HTTP entrypoint, reverse proxy, and load balancer.
- Lombok is available as an annotation processor but is not used by production
  code yet.

The root Java package is `com.haru.product`.

## Repository map

- `pom.xml`: Maven coordinates, dependencies, and plugins.
- `src/main/java/com/haru/product/ProductApplication.java`: Spring Boot
  entrypoint.
- `src/main/java/com/haru/product/shared/security/SecurityConfig.java`: HTTP
  authorization rules and Keycloak claim-to-authority conversion.
- `src/main/java/com/haru/product/shared/configuration/OpenApiConfiguration.java`:
  OpenAPI metadata and the Keycloak JWT bearer scheme.
- `src/main/java/com/haru/product/product`: Product/BOM domain, application,
  persistence, and presentation code.
- `src/main/java/com/haru/product/inventory`: Inventory domain, application,
  persistence, and presentation code.
- `src/main/java/com/haru/product/production`: Production domain, application,
  persistence, and presentation code.
- `src/main/java/com/haru/product/shared/exception/ApiExceptionHandler.java`:
  global RFC 9457 `ProblemDetail` responses.
- `src/main/resources/application.properties`: Resource Server client ID,
  issuer, JWK Set, JPA validation, Open Session in View, and springdoc
  configuration.
- `src/main/resources/db/migration/V1__create_products_and_product_compositions.sql`:
  Product and BOM schema.
- `src/main/resources/db/migration/V2__create_inventory.sql`: inventory lot
  and append-only movement schema.
- `src/main/resources/db/migration/V3__create_production.sql`: production
  orders, component-consumption trace, and produced-lot links.
- `src/main/resources/db/migration/V4__harden_database_invariants.sql`:
  PostgreSQL enum/lifecycle checks, the FEFO index, and database-enforced
  append-only inventory movements.
- `src/main/resources/db/migration/V5__automate_product_sku.sql`: the global
  product-SKU sequence, legacy-sequence initialization, and database-enforced
  SKU immutability.
- `src/main/resources/db/migration/V6__index_production_order_search.sql`:
  deterministic production-order search indexes for unfiltered and
  status-filtered pagination.
- `src/test/java`: executable specifications for context loading, security,
  and the Keycloak realm.
- `docker/docker-compose.yml`: complete local environment topology.
- `docker/app/Dockerfile`: application build and runtime image.
- `docker/Keycloak/realm/haru-realm.json`: versioned realm contract.
- `docker/PostgresSQL/init-multiple-databases.sh`: initial creation of the
  application and Keycloak databases.
- `docker/Nginx/nginx.conf`: proxy routes and API load balancing.

## Modular organization

Grow the codebase by business module, creating packages only when they contain
real implementation:

```text
com.haru.product
├── product
│   ├── domain
│   ├── application
│   ├── infrastructure
│   └── presentation
├── inventory
│   ├── domain
│   ├── application
│   ├── infrastructure
│   └── presentation
├── production
│   ├── domain
│   ├── application
│   ├── infrastructure
│   └── presentation
└── shared
    ├── exception
    ├── security
    └── configuration
```

Do not create empty modules or layers. The implemented packages are currently
`product.{domain,application,infrastructure,presentation}`,
`inventory.{domain,application,infrastructure,presentation}`,
`production.{domain,application,infrastructure,presentation}`,
`shared.configuration`, `shared.security`, and `shared.exception`. Introduce
domain repository interfaces and persistence adapters only when they provide a
real boundary; do not add abstractions merely to imitate Clean Architecture.
The current application services use Spring Data repositories directly.

Module dependencies are intentionally one-way: Product is independent,
Inventory depends on Product, and Production orchestrates Product and
Inventory in one database transaction. Production currently accesses the
other modules' domain types and Spring Data repositories directly. This keeps
the shared transaction explicit in this single deployable, but it is a
boundary to replace with application ports before extracting modules into
separate deployables. Do not add facade interfaces solely to hide these calls
while the modules still share the same persistence unit.

JPA entities use `Long` identity columns:

```java
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;
```

The corresponding PostgreSQL columns use
`BIGINT GENERATED BY DEFAULT AS IDENTITY`. Do not use UUID entity IDs.

## Security contracts

The application is stateless. CSRF, form login, HTTP Basic, and logout are
disabled, while method security is enabled. Preserve the order and meaning of
the rules in `SecurityConfig`:

1. `OPTIONS /**` and `/error` are public.
2. `/v3/api-docs`, `/v3/api-docs/**`, `/v3/api-docs.yaml`,
   `/swagger-ui.html`, and `/swagger-ui/**` are public.
3. `/admin/**` requires `ROLE_ADMIN`.
4. `GET /api/products/**` accepts `ROLE_ADMIN` or `ROLE_CUSTOMER`.
5. Other methods under `/api/products/**` require `ROLE_ADMIN`.
6. `GET /api/inventory/**` accepts `ROLE_ADMIN` or `ROLE_CUSTOMER`.
7. Other methods under `/api/inventory/**` require `ROLE_ADMIN`.
8. `GET /api/production-orders/**` accepts `ROLE_ADMIN` or `ROLE_CUSTOMER`.
9. Other methods under `/api/production-orders/**` require `ROLE_ADMIN`.
10. Every other route requires an authenticated JWT.

The JWT converter:

- preserves scope authorities generated by Spring;
- reads roles from `realm_access.roles`;
- reads roles from `resource_access[clientId].roles`;
- normalizes roles to `ROLE_<UPPERCASE_NAME>` and removes duplicates;
- uses `preferred_username` as the principal, falling back to `sub`;
- uses `haru-api` as the default client ID.

The Resource Server also requires the `haru-api` audience by default. The
Keycloak client includes that audience in access tokens. Authentication and
authorization failures return sanitized `application/problem+json` bodies,
while retaining the standard Bearer `WWW-Authenticate` header. Unexpected MVC
errors and common HTTP protocol failures are also sanitized; never expose SQL,
Hibernate messages, exception class names, or stack traces in responses.

The current authorization contract gives a self-registered `customer` read
access to BOM details, inventory lots and movements, and production trace
responses. Those DTOs can contain unit cost and actor data. This is an explicit
existing policy, not a least-privilege conclusion; obtain a business decision
before narrowing or expanding it for a production deployment.

When changing roles, the client ID, redirects, or origins, keep
`SecurityConfig`, `application.properties`, Compose, the realm JSON, and the
related tests synchronized.

## OpenAPI documentation contract

Springdoc generates the API contract at `/v3/api-docs` and
`/v3/api-docs.yaml`; Swagger UI is available at `/swagger-ui.html`. These
documentation resources are intentionally public so the UI can bootstrap,
while the documented business endpoints retain their normal JWT authorization
rules. Swagger UI accepts a raw Keycloak access token through the `bearerAuth`
authorization dialog and adds the `Bearer` prefix to requests.

Only `/api/**` routes are included in the generated contract. Keep internal
routes such as `/error` and `/admin/**`, as well as proxy and identity-provider
routes, out of the specification. Swagger UI does not persist authorization
between browser sessions. Keep `OpenApiConfiguration`, the springdoc
properties, `SecurityConfig`, `OpenApiDocumentationTests`, and the README in
sync when changing this contract.

## Keycloak configuration contract

Compose intentionally configures two different JWT URLs:

- `issuer-uri` uses the public URL, by default
  `http://localhost/auth/realms/haru`, and must match the token's `iss`
  claim.
- `jwk-set-uri` uses the Docker network URL,
  `http://keycloak:8080/auth/realms/haru/protocol/openid-connect/certs`, so
  the application can retrieve signing keys internally.

Do not replace one with the other without accounting for these separate
responsibilities.

The `haru` realm allows self-registration and email login. `customer` is the
default role; the current business roles are `admin` and `customer`. The
public `haru-api` client enables authorization code and direct access grants.
Its audience mapper adds `haru-api` to access and introspection tokens, but not
ID tokens. The realm JSON also contains localhost and a specific Codespace URL.
An already-persisted realm does not automatically receive mapper changes from
the import JSON; update it administratively or recreate local state
intentionally.

## Docker environment

| Service | Responsibility | Default exposure |
| --- | --- | --- |
| `nginx` | HTTP entrypoint and `least_conn` across API instances | host `:80` |
| `api-1` | First Spring instance | network only, `:8080` |
| `api-2` | Second Spring instance | network only, `:8080` |
| `keycloak` | OIDC/JWT under `/auth` | through Nginx |
| `postgres` | Separate application and Keycloak databases | `127.0.0.1:5432` |
| `elasticsearch` | Product full-text search index | network only, `:9200` |

Operational details:

- API instances wait for healthy PostgreSQL, Keycloak, and Elasticsearch
  services.
- Elasticsearch is a single-node local-development service. It is not exposed
  on a host port, persists data in `elasticsearch_data`, disables its own
  security because it is isolated on the backend network, and has conservative
  CPU, memory, and heap defaults in `docker/.env.example`.
- The Docker environment enables product reindexing at application startup so
  an existing PostgreSQL volume is searchable. Both API replicas may perform
  the idempotent versioned `PUT`s; disable this with
  `ELASTICSEARCH_REINDEX_ON_STARTUP=false` when startup reindexing is not
  desired.
- Nginx routes `/auth/` to Keycloak and all other requests to the APIs.
- `GET /nginx-health` is the proxy's public health endpoint.
- The application build uses Maven/Temurin 25; runtime uses Temurin 25 JRE.
- Java runs as the non-root `spring` user with UID 10001.
- `SPRING_PROFILES_ACTIVE=docker` is enabled even though there is currently no
  `application-docker.properties`; datasource and JWT configuration come from
  environment variables.
- `INSTANCE_ID` distinguishes `api-1` and `api-2` in Compose, but the code
  does not consume it yet.
- The PostgreSQL initialization script runs only for an empty data volume.
  Changing database names, users, or passwords does not update existing data.
- The application database is named `haru_product` by default. A volume
  initialized under the previous database name must be migrated manually or
  intentionally recreated.
- The Compose project name is `haru-product-api`. Stacks created under the
  previous project name belong to a different Compose namespace and are not
  automatically removed.
- Realm import also depends on Keycloak state persisted in PostgreSQL. Editing
  the JSON does not guarantee reimport over an existing realm.
- `KEYCLOAK_REALM` and `KEYCLOAK_CLIENT_ID` look configurable in `.env`,
  but the imported JSON fixes them as `haru` and `haru-api`. Changing only
  `.env` makes the environment inconsistent.
- Changing a port, domain, or public URL also requires reviewing
  `redirectUris`, `webOrigins`, and logout redirects in the realm JSON;
  `KEYCLOAK_PUBLIC_URL` does not rewrite them.
- This is a local development configuration: Keycloak uses `start-dev`, HTTP
  without TLS, non-strict hostname validation, and example credentials.

Never commit `docker/.env`. Use `docker/.env.example` as a reference and do
not treat its local example passwords as suitable for other environments.

## Working commands

Run commands from the repository root.

Validate Docker configuration without starting services:

```bash
docker compose --env-file docker/.env.example -f docker/docker-compose.yml config --quiet
```

Start the local environment:

```bash
cp docker/.env.example docker/.env
docker compose --env-file docker/.env -f docker/docker-compose.yml up --build
```

Stop while preserving data:

```bash
docker compose --env-file docker/.env -f docker/docker-compose.yml down
```

Recreate databases and Keycloak state only when a destructive reset is
intentional:

```bash
docker compose --env-file docker/.env -f docker/docker-compose.yml down -v
```

Tests require JDK 25. The Docker build uses Maven 3.9, and the suite has also
been verified with Maven 3.6.3 in the local environment:

```bash
mvn clean verify
mvn -Dtest=ProductTest test
mvn -Dtest=ProductServiceTests test
mvn -Dtest=ProductControllerTests test
mvn -Dtest=InventoryControllerTests test
mvn -Dtest=InventoryServiceIntegrationTests test
mvn -Dtest=ProductionServiceTests test
mvn -Dtest=ProductionServiceIntegrationTests test
mvn -Dtest=ProductionControllerTests test
mvn -Dtest=PersistenceHardeningIntegrationTests test
mvn -Dtest=DatabaseHardeningMigrationTests test
mvn -Dtest=ApiExceptionHandlerTests test
mvn -Dtest=OpenApiDocumentationTests test
mvn -Dtest=SecurityConfigTests test
mvn -Dtest=SecurityAuthorizationRulesTests test
mvn -Dtest=KeycloakRealmConfigTests test
```

Surefire starts Mockito as a Java agent, which is required by the current
Mockito/JDK 25 combination in restricted environments where runtime
self-attachment is unavailable.

Do not present `./mvnw` as functional in the current repository. Wrapper
scripts are present, but `.mvn/wrapper/maven-wrapper.properties` is not
versioned and `.mvn/` is ignored. Repair the wrapper before adopting it as an
official command.

`docker/app/Dockerfile` runs `mvn clean package -DskipTests`. A successful
image build therefore does not prove that the test suite passed. The build
stage also copies only `pom.xml` and `src`; the realm test would need the
realm JSON available in that stage if tests were enabled directly in the
Dockerfile.

## Product and BOM contracts

- Product IDs and composition IDs are `Long` identity values.
- Product creation obtains an automatic SKU from PostgreSQL in the fixed
  `PRD-##########` format. The global bounded sequence is safe across API
  replicas, does not cycle, and may contain gaps after rolled-back
  transactions. Existing SKUs are preserved.
- SKU is immutable after insertion. Create and update requests do not accept
  it; the JPA field is non-updatable and V5 rejects direct database changes.
  Preserve the PostgreSQL `LOWER(sku)` unique index as the final integrity
  guarantee.
- A BOM component has only its product, positive quantity, and measurement
  unit. Do not add inventory, lot, expiry, availability, or cost data.
- A product cannot contain itself, repeat a direct component, use an inactive
  component, or create a direct or indirect composition cycle.
- `SERVICE` products cannot own a physical BOM.
- Cycle detection uses an iterative graph walk with method-local visited sets;
  never move traversal state to a singleton field. Creating a BOM edge first
  acquires the
  transaction-scoped PostgreSQL advisory lock in
  `ProductCompositionTopologyLock`, serializing topology changes across API
  replicas before the graph snapshot is read.
- Composition responses use explicit fetch plans. The recursive BOM endpoint
  loads levels in bounded batches and builds the DTO iteratively, with limits
  of 32 levels and 5,000 rendered nodes. Requests exceeding either limit fail
  with a sanitized invalid-composition response.
- Removing a composition is explicit and must not remove the component product.
  Product relationships have neither remove cascade nor orphan removal, so the
  migration's `ON DELETE RESTRICT` remains authoritative for product deletion.
- Product writes use optimistic concurrency through `@Version long version`.
  This protects overlapping server transactions. The current update request
  does not carry a version or `If-Match`, so a disconnected client update that
  starts only after another update committed cannot be identified as stale;
  defining that HTTP concurrency contract remains a domain/API decision.
- PostgreSQL is the source of truth for product search. The non-transactional
  `ProductCommandFacade` invokes the transactional `ProductService` first and
  performs the Elasticsearch `PUT` only after that proxy returns, so the
  database commit precedes indexing. The facade uses `Propagation.NEVER` to
  reject an accidental surrounding transaction.
- Search indexing uses strict `version_type=external`; a live document's
  external version is one greater than the JPA version. Deletes write a
  tombstone with `deleted=true` at the reserved external fence version
  `9000000000000000000`, and search queries filter tombstones out. Product IDs
  are generated and never reused, so no delayed live `PUT` can cross the fence
  and resurrect a deleted document. A database restore or reseed that reuses
  IDs must recreate the product index before reindexing. An
  indexing failure is logged after commit and must not turn a committed
  database write into an HTTP failure.
- Composition writes also increment the parent Product version. Their facade
  operations reload and reindex the parent after commit so version validation
  does not report a false stale document when only the BOM changed.
- Product text uses Elasticsearch's Brazilian analyzer. Search combines a
  boosted exact numeric ID, exact and prefix SKU clauses,
  `search_as_you_type` name-prefix matching, and fuzzy name/description
  matching. The current index generation is `haru-products-v2`; incompatible
  mapping changes require a new generation and reindexing from PostgreSQL.
  Search unavailability returns a sanitized 503
  `PRODUCT_SEARCH_UNAVAILABLE` response.
- Docker reindexes persisted database products on startup. The internal admin
  validation route compares database and index versions; reindex repairs
  missing/stale documents, while reconcile writes tombstones for orphaned live
  documents. These operations are paginated and are not part of OpenAPI.
- Product schema changes belong in sequential Flyway migrations; Hibernate is
  configured with `ddl-auto=validate` and Open Session in View is disabled.

The Product and BOM endpoints are:

```text
POST   /api/products
GET    /api/products/search?q={query}&offset={offset}&limit={limit}
GET    /api/products/{id}
PUT    /api/products/{id}
DELETE /api/products/{id}
POST   /api/products/{id}/components
PUT    /api/products/{id}/components/{componentId}
DELETE /api/products/{id}/components/{componentId}
GET    /api/products/{id}/composition
```

The internal product-search maintenance endpoints are:

```text
GET  /admin/search/products/validation
POST /admin/search/products/reindex
POST /admin/search/products/reconcile
```

Controllers return application DTO records, never JPA entities. Global errors
use `ProblemDetail` and cover missing resources, duplicates, invalid BOMs,
cycles, request validation, database constraints, and optimistic conflicts.
They also sanitize unsupported methods/media types, missing resources,
authentication failures, authorization failures, and unexpected exceptions.

## Inventory contracts

- An inventory lot belongs to exactly one Product and is unique by
  `(product_id, lot_number)`. Its initial quantity is positive; available
  quantity and unit cost cannot be negative; expiration cannot precede
  manufacture.
- Expiration, cost, and balance belong to the lot, never to Product or BOM.
  Lots without expiration are valid but sort after dated lots during FEFO.
- Lot statuses are `AVAILABLE`, `DEPLETED`, `EXPIRED`, and `BLOCKED`. There is
  no general `RESERVED` status. Expired and blocked lots cannot be consumed.
- `InventoryMovement` is immutable after insertion. Balance corrections use
  compensating `ADJUSTMENT_IN` or `ADJUSTMENT_OUT` rows; there are no update or
  delete movement endpoints. V4 also rejects movement `UPDATE` and `DELETE`
  directly in PostgreSQL.
- Creating a lot inserts its `ENTRY` movement in the same transaction. Every
  application balance change and its movement share one transaction.
- Direct consumption uses FEFO: earliest expiration first, no-expiration lots
  last, then the smallest lot ID. One request may create one `EXIT` movement
  per consumed lot.
- FEFO consumption reads candidates in first-page batches of 100 instead of
  loading every eligible lot. Product lot and movement history endpoints are
  paginated by offset with a default of 20 and a server-side maximum of 50 rows.
- The inventory overview pages catalog products through Elasticsearch, then
  aggregates availability and lot counts in one PostgreSQL query for the
  returned product IDs. It preserves catalog order and includes products with
  no lots. Its optional query matches product name, numeric ID, or SKU and uses
  the same 10,000-result window as product search.
- Direct-consumption callers cannot claim system-owned reference types such as
  `INVENTORY_LOT`, `INVENTORY_ADJUSTMENT`, or `PRODUCTION_ORDER`.
- Consumption uses a conditional database update that requires enough balance,
  `AVAILABLE` status, and a non-expired lot. The update also increments the
  JPA version. A zero row count raises `InsufficientInventoryException`; the
  surrounding transaction rolls back all earlier allocations and movements.
  Do not replace this with JVM locks or singleton state.

The Inventory endpoints are:

```text
POST /api/inventory/lots
GET  /api/inventory/lots/{id}
GET  /api/inventory/products/search?q={query}&offset={offset}&limit={limit}
GET  /api/inventory/products/{productId}/lots?offset={offset}&limit={limit}
GET  /api/inventory/products/{productId}/availability
GET  /api/inventory/products/{productId}/movements?offset={offset}&limit={limit}
POST /api/inventory/lots/{lotId}/adjustments/in
POST /api/inventory/lots/{lotId}/adjustments/out
POST /api/inventory/products/{productId}/consumption
```

The final endpoint is an explicit direct-consumption operation for current use
and integration testing; it is not a production-order API. Production-order
completion generates the dedicated `PRODUCTION_CONSUMPTION` and
`PRODUCTION_ENTRY` movement types.

## Production contracts

- Production-order statuses are `CREATED`, `IN_PROGRESS`, `COMPLETED`, and
  `CANCELLED`. Only `CREATED` orders can start, and only `IN_PROGRESS` orders
  can complete. Completed and cancelled orders cannot be completed again.
- Production-order search is paginated by offset with a default of 20 and a
  maximum of 50 rows. Its optional query matches order ID, product ID, product
  name, or SKU; an optional status filter is applied in PostgreSQL. Results are
  ordered deterministically by creation time and order ID, both descending.
- Creating an order requires a positive `NUMERIC(19,6)` quantity and a final
  product with a direct BOM. Completion reloads and revalidates that BOM so a
  topology change cannot silently produce without components.
- Required component quantity is the BOM row quantity multiplied by the order
  quantity. The result must fit `NUMERIC(19,6)` exactly; no lossy rounding or
  implicit unit conversion is performed.
- Completion preflights every direct component before writing, then consumes
  eligible lots in FEFO order in batches of 100. Expired, blocked, depleted,
  and zero-balance lots are ignored. One movement and one
  `ProductionConsumption` row are inserted for every allocated component lot.
- After all components are consumed, completion creates one InventoryLot for
  the finished product, one `PRODUCTION_ENTRY` movement, and one `ProducedLot`
  link. A unique constraint permits only one produced lot per order and only
  one production link per inventory lot.
- Component decrements, consumption traces, movements, the finished lot, and
  the order transition execute in one Spring transaction. Any shortage,
  conditional-update conflict, constraint violation, or optimistic-lock
  conflict rolls the entire completion back.
- Concurrent balance protection uses the Inventory module's conditional SQL
  update, including status, expiry, and sufficient-balance predicates. The
  order also uses `@Version`; component requirements are processed in stable
  product-ID order. Never replace these database guarantees with
  `synchronized`, a local cache, or singleton state.
- Production movements use `reference_type = PRODUCTION_ORDER` and the order
  ID. `ProductionResultResponse` exposes the order, produced inventory lot,
  and every component lot allocation, providing forward and backward
  traceability without copying lot data into `ProducedLot`.
- The current production calculation consumes the final product's direct BOM.
  Nested BOM expansion and measurement-unit conversion are not implicit.
- PostgreSQL FKs guarantee that referenced rows exist, while the domain model
  validates that a consumed lot belongs to its component and that a produced
  lot belongs to the order product. Those cross-table equalities are not
  duplicated as composite database constraints or triggers.

The production endpoints are:

```text
POST /api/production-orders
GET  /api/production-orders/search?q={query}&status={status}&offset={offset}&limit={limit}
GET  /api/production-orders/{id}
POST /api/production-orders/{id}/start
POST /api/production-orders/{id}/complete
POST /api/production-orders/{id}/cancel
```

## Database hardening contracts

- V1-V6 are immutable history; add a later migration for future schema
  changes rather than editing an applied migration.
- Persisted Product, BOM, Inventory, and Production enum strings are restricted
  by PostgreSQL checks, in addition to Java enums.
- Production-order status and timestamps must satisfy the lifecycle check in
  V4.
- The FEFO index order is `(product_id, status, expiration_date ASC NULLS LAST,
  id ASC)` and mirrors the repository query. Preserve both sides together.
- Product SKU generation comes from the bounded, non-cycling
  `product_sku_sequence`; V5 initializes it above legacy automatic SKUs and
  rejects SKU updates through a trigger. Case-insensitive uniqueness still
  comes from the `LOWER(sku)` unique index. Lot uniqueness comes from
  `(product_id, lot_number)`; production output has unique order and
  inventory-lot links.
- All quantities and costs use the exact `NUMERIC` precision declared in the
  migrations. Do not introduce floating-point persistence for these values.

## Test contracts

Automated tests are executable specifications. Preserve valid existing tests
and fix production code when one of those tests fails. Before changing
production behavior, run the directly relevant tests when practical to
establish the baseline. After the change, run the affected tests and the full
validation suite.

Never make an existing test pass by:

- deleting, disabling, skipping, or excluding it;
- adding `.only`, disabled annotations, unconditional returns, or swallowed
  failures;
- removing assertions or replacing exact assertions with weaker checks;
- changing expected values merely to match incorrect production behavior;
- increasing timeouts merely to hide a race condition;
- mocking the integration boundary that the test exists to exercise;
- changing Maven or Surefire configuration to avoid executing it.

An existing test may change only when the requested behavior intentionally
changes its contract, or when the test is demonstrably incorrect, obsolete,
nondeterministic, or coupled to an implementation detail that is no longer a
contract. In that case, explain why the previous expectation is invalid,
preserve or strengthen its coverage, add coverage for the replacement
behavior, and report the test change explicitly.

New features and bug fixes require meaningful behavior tests. Tests written for
a new change may be corrected while developing when their initial expectation
was wrong, but never weakened solely to obtain a green build.

The required final backend validation is:

```bash
mvn -B -Dmaven.repo.local=/tmp/haru-product-m2 clean verify
```

Focused Maven tests are useful during development but never replace the final
`clean verify`. Do not use `-DskipTests`, `-Dmaven.test.skip=true`, test
exclusions, or disabled annotations as final validation. The complete suite
must finish with zero failures and zero errors. With Docker available, it must
also finish with zero skipped tests so the Testcontainers paths are proven.
If Docker or another required dependency is unavailable, report that as a
validation blocker or explicit limitation; do not modify tests to conceal it.
Always report the command executed and its failure, error, and skipped counts.

The suite covers:

- context loading with mocked repositories while DataSource, JPA, and Flyway
  are excluded;
- Keycloak claim conversion and HTTP authorization with in-memory JWTs;
- the versioned Keycloak realm JSON;
- the Sakura BOM scenario, invalid quantities, missing/inactive/self/duplicate
  components, `SERVICE` restrictions, and direct/indirect cycle prevention;
- JPA mapping and explicit repository fetch-plan requirements, plus the
  structure of V1-V6 migrations;
- automatic SKU generation and immutability, bounded composition-tree mapping,
  and a 5,000-level iterative cycle-validation stress case;
- all Product/BOM endpoints, request validation, `ProblemDetail`, and
  optimistic-lock error responses with standalone MockMvc;
- inventory lot and movement invariants, JPA mappings, V2 migration structure,
  inventory HTTP endpoints, `ProblemDetail`, and authorization;
- real PostgreSQL behavior through Testcontainers, including FEFO,
  all-or-nothing insufficient consumption, and simultaneous conditional
  balance updates;
- production lifecycle and mappings, V3 migration structure, HTTP DTOs and
  authorization, exact BOM multiplication, one- and ten-unit production,
  multi-lot FEFO, expired-lot exclusion, full rollback on shortage, final-lot
  traceability, invalid states, and concurrent orders disputing the same
  balance;
- real PostgreSQL enum/lifecycle checks and movement append-only behavior;
- deterministic races for distinct automatic SKU generation, lot creation,
  Product optimistic updates, and ProductionOrder optimistic transitions;
- sanitized 401, 403, 404, 405, 406, 415, validation, database, optimistic,
  and unexpected-error responses.
- public OpenAPI JSON/YAML and Swagger UI resources, documented JWT bearer
  authentication, business-path filtering, and continued protection of the
  underlying API endpoints.

Inventory, Production, and persistence-hardening integration tests start
PostgreSQL with Testcontainers when Docker is available. Keycloak and Nginx are
not started by the test suite. Realm and migration structure tests use paths
relative to the repository and must run from its root.
`InventoryServiceIntegrationTests`, `ProductionServiceIntegrationTests`, and
`PersistenceHardeningIntegrationTests` use `disabledWithoutDocker = true`, so
always inspect Maven's skipped-test count; a green build without Docker does
not prove the PostgreSQL or concurrent paths. With Docker available, the full
suite is expected to complete with no skipped tests.

The persistence-hardening race tests coordinate Spring Data spy calls through
the transaction-bound `JdbcTemplate` and shared `EntityManager` proxy. Do not
use Mockito `InvocationOnMock.callRealMethod()` for the repository interface
methods: they are abstract declarations and both concurrent operations fail
before reaching PostgreSQL. Raw JDBC parameters for `TIMESTAMPTZ` columns use
UTC `OffsetDateTime`, because the PostgreSQL driver does not infer a JDBC type
for `Instant` passed through `JdbcTemplate` varargs.

Treat tests as contracts when modifying security or infrastructure. Add
behavior tests for new code. When persistence is introduced, cover migrations
and PostgreSQL integration without weakening existing tests.

## Change guidelines

- Keep new code under `com.haru.product` and mirror the structure under
  `src/test/java`.
- Keep cross-cutting security code under `com.haru.product.shared.security`.
- Keep cross-cutting OpenAPI configuration under
  `com.haru.product.shared.configuration`.
- Use English for all code, domain terminology, routes, tests, configuration
  keys, comments, and project documentation.
- Follow the existing style: package-private JUnit 5 tests, AssertJ assertions,
  and MockMvc for HTTP authorization behavior.
- Never add real secrets, tokens, or credentials to the repository.
- Do not use HTTP sessions, mutable state in singleton services, static maps,
  local caches as a source of truth, local files for persistence, or
  `synchronized` as a distributed-concurrency mechanism.
- Add new schema changes as versioned Flyway migrations under
  `src/main/resources/db/migration`; do not rely on implicit ORM schema
  creation.
- Authorization changes must include successful cases, 401 for anonymous
  requests, and 403 for authenticated users without the required role.
- Realm changes must update `KeycloakRealmConfigTests` and account for an
  existing volume hiding import changes.
- Run a clean build after package or main-class renames so stale bytecode cannot
  mask problems.
- Before finishing, run the full test suite when Java 25 and Maven are
  available, and validate Compose whenever `docker/` changes.
- Clearly report any check that could not be run. Do not confuse a build using
  `-DskipTests` with test-suite validation.
