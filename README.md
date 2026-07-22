# HaruProductAPI

Local infrastructure for a Java 25 Spring Boot API with PostgreSQL, JWT
authentication through Keycloak, and Nginx as a reverse proxy/load balancer.

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
- Nginx health check: `http://localhost/nginx-health`

Nginx distributes requests between `api-1` and `api-2` using `least_conn`. The
`haru` realm and public `haru-api` client are imported automatically during the
first startup. Replace every password in `.env` before using this setup outside
local development.

## API documentation

The OpenAPI description and Swagger UI are public so the browser can load the
documentation. Business endpoints remain protected by JWT authorization. In
Swagger UI, select **Authorize** and paste a Keycloak access token into the
`bearerAuth` field; Swagger UI adds the `Bearer` prefix to requests.

Only paths under `/api/**` are included in the generated contract. Internal
routes such as `/error`, `/admin/**`, Keycloak, and the Nginx health endpoint
are not part of the OpenAPI description.

To remove persisted data and initialize the databases again:

```bash
docker compose --env-file docker/.env -f docker/docker-compose.yml down -v
```
