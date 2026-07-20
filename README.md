# HaruProdutoAPI

Infraestrutura local para uma API Spring Boot em Java 25, com PostgreSQL,
autenticação JWT pelo Keycloak e Nginx como proxy reverso/load balancer.

## Execução local

O Dockerfile da aplicação espera um projeto Maven com `pom.xml` e `src/` na
raiz deste repositório.

```bash
cp docker/.env.example docker/.env
docker compose --env-file docker/.env -f docker/docker-compose.yml up --build
```

Serviços disponíveis:

- API via Nginx: `http://localhost/`
- Keycloak via Nginx: `http://localhost/auth/`
- Console administrativo: `http://localhost/auth/admin/`
- PostgreSQL: `localhost:5432`
- Health check do Nginx: `http://localhost/nginx-health`

O Nginx distribui as requisições entre `api-1` e `api-2` usando `least_conn`.
O realm `haru` e o client público `haru-api` são importados automaticamente na
primeira inicialização. Troque todas as senhas do `.env` antes de usar fora do
ambiente local.

Para remover também os dados persistidos e refazer a inicialização dos bancos:

```bash
docker compose --env-file docker/.env -f docker/docker-compose.yml down -v
```
