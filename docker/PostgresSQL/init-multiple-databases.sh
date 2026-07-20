#!/bin/sh
set -eu

create_database_and_user() {
  database="$1"
  username="$2"
  password="$3"

  psql --set ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE USER "$username" WITH PASSWORD '$password';
    CREATE DATABASE "$database" OWNER "$username";
    GRANT ALL PRIVILEGES ON DATABASE "$database" TO "$username";
EOSQL
}

create_database_and_user "$APP_DB_NAME" "$APP_DB_USER" "$APP_DB_PASSWORD"
create_database_and_user "$KEYCLOAK_DB_NAME" "$KEYCLOAK_DB_USER" "$KEYCLOAK_DB_PASSWORD"
