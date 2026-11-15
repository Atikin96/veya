#!/bin/sh
# VEYA cluster-level role provisioning (runs once, when the PostgreSQL data directory is first created).
#
# Roles are cluster objects, so they are created here by the bootstrap superuser and not by Flyway.
# Flyway (connecting as veya_owner) creates the schema, tables and object privileges.
#
#   veya_owner        LOGIN  - owns the veya schema and all objects; used only by Flyway migrations.
#   veya_app          group  - application privileges (DML on user data, no DDL).
#   veya_readonly     group  - read-only reporting: reference data and masked/anonymised views only.
#   veya_admin        group  - administration: full DML on all tables, no DDL (DDL only via migrations).
#   veya_app_user     LOGIN  - member of veya_app; used by the Spring Boot datasource.
#   veya_readonly_user LOGIN - member of veya_readonly.
#   veya_admin_user   LOGIN  - member of veya_admin.
#
# None of these roles is a superuser. Passwords come only from environment variables
# (see infra/.env.example); no password is stored in this file.
set -eu

: "${VEYA_OWNER_PASSWORD:?VEYA_OWNER_PASSWORD must be set}"
: "${VEYA_APP_PASSWORD:?VEYA_APP_PASSWORD must be set}"
: "${VEYA_READONLY_PASSWORD:?VEYA_READONLY_PASSWORD must be set}"
: "${VEYA_ADMIN_PASSWORD:?VEYA_ADMIN_PASSWORD must be set}"

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
     -v db="$POSTGRES_DB" \
     -v owner_pw="$VEYA_OWNER_PASSWORD" \
     -v app_pw="$VEYA_APP_PASSWORD" \
     -v ro_pw="$VEYA_READONLY_PASSWORD" \
     -v admin_pw="$VEYA_ADMIN_PASSWORD" <<'SQL'
-- Migration owner: may create the schema and trusted extensions in this database, nothing more.
CREATE ROLE veya_owner LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS
    PASSWORD :'owner_pw';

-- Group (privilege) roles. Object privileges are granted by Flyway migration V9.
CREATE ROLE veya_app      NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
CREATE ROLE veya_readonly NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
CREATE ROLE veya_admin    NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;

-- Login roles inherit privileges from exactly one group role.
CREATE ROLE veya_app_user      LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS
    PASSWORD :'app_pw' IN ROLE veya_app;
CREATE ROLE veya_readonly_user LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS
    PASSWORD :'ro_pw' IN ROLE veya_readonly;
CREATE ROLE veya_admin_user    LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS
    PASSWORD :'admin_pw' IN ROLE veya_admin;

-- Read-only sessions cannot write even if a privilege were granted by mistake.
ALTER ROLE veya_readonly_user SET default_transaction_read_only = on;
ALTER ROLE veya_readonly_user SET statement_timeout = '30s';

-- All VEYA objects live in schema "veya".
ALTER ROLE veya_owner         SET search_path = veya;
ALTER ROLE veya_app_user      SET search_path = veya;
ALTER ROLE veya_readonly_user SET search_path = veya;
ALTER ROLE veya_admin_user    SET search_path = veya;

-- Database-level access: nobody connects by default; CREATE (schema/extension) only for the owner.
REVOKE ALL ON DATABASE :"db" FROM PUBLIC;
GRANT CONNECT, CREATE ON DATABASE :"db" TO veya_owner;
GRANT CONNECT ON DATABASE :"db" TO veya_app, veya_readonly, veya_admin;

-- The public schema is not used by VEYA.
REVOKE ALL ON SCHEMA public FROM PUBLIC;
SQL
