# VEYA

Web-based system for adaptive meal and physical activity planning with **Dynamic Plan Recovery**.
Diploma project, European Humanities University (2026).

## Stage 3 assessments — start here

| Assessment | Documentation | Requirement matrix |
|---|---|---|
| **Back-end** | [`docs/backend/README.md`](docs/backend/README.md) · deployment [`docs/backend/DEPLOYMENT.md`](docs/backend/DEPLOYMENT.md) | [`docs/backend/ASSESSMENT.md`](docs/backend/ASSESSMENT.md) |
| **Automated tests** | [`docs/testing/README.md`](docs/testing/README.md) | [`docs/testing/ASSESSMENT.md`](docs/testing/ASSESSMENT.md) |
| API contract | [`docs/api/openapi.yaml`](docs/api/openapi.yaml) (OpenAPI 3.1, served at `/v3/api-docs`) · [`docs/api/API_ENDPOINTS.md`](docs/api/API_ENDPOINTS.md) · [Postman](docs/api/postman/) | |

Evidence (2026-11-23): 45 API operations (44 implemented, 1 partial: EDIT blocked by open question Q12);
`mvnw verify` → 264 tests, 0 failures; JaCoCo 95.0 % lines / 82.8 % branches overall, 95.2 % / 86.0 % business logic;
Docker deployment (PostgreSQL + API) verified locally with an end-to-end smoke test (35 HTTP checks + persisted-row checks in PostgreSQL).
CI: [GitHub Actions run 45391042348](https://github.com/Atikin96/veya/actions/runs/45391042348) — all jobs green.
**Public deployment: NOT VERIFIED** (configuration and checklist in `DEPLOYMENT.md`).

Verify everything yourself (Windows PowerShell, Docker Desktop running, JDK 21):

```powershell
git clone https://github.com/Atikin96/veya.git; cd veya
& "C:\Program Files\Git\bin\sh.exe" infra/generate-env.sh                 # random local secrets (git-ignored)
Set-Location backend; .\mvnw.cmd verify; Set-Location ..                       # all tests + coverage gate
start backend\target\site\jacoco\index.html                                   # coverage report
docker compose -f infra/docker-compose.yml --profile app up -d --build        # PostgreSQL + API on :8080
powershell -ExecutionPolicy Bypass -File infra\smoke\smoke-test.ps1 -Db        # end-to-end UC-01 over HTTP
```

Requirements: [`docs/BA_Malafei_Mikita.docx`](docs/BA_Malafei_Mikita.docx) (Business Analysis).
Open BA questions and agreed interpretations: [`docs/ba-open-questions.md`](docs/ba-open-questions.md).
Database design: [`docs/database/`](docs/database/README.md).

## Database criterion — where to find the materials

| Material | Location |
|---|---|
| Design document (3NF, integrity, transactions, indexes, roles, masking, versioning, assessment map) | [`docs/database/README.md`](docs/database/README.md) |
| Data dictionary (every table, column, type, key, constraint, data-quality expectations) | [`docs/database/data-dictionary.md`](docs/database/data-dictionary.md) |
| Logical schema (colour-coded blocks) and ER diagrams | [`docs/database/er-diagram.md`](docs/database/er-diagram.md) |
| DDL — Flyway migrations V1–V10 | [`backend/src/main/resources/db/migration/`](backend/src/main/resources/db/migration) |
| Roles and database-level access | [`infra/postgres/initdb/01-create-roles.sh`](infra/postgres/initdb/01-create-roles.sh), [`V9__roles_and_privileges.sql`](backend/src/main/resources/db/migration/V9__roles_and_privileges.sql) |
| Reference data / test data | [`V10__reference_data.sql`](backend/src/main/resources/db/migration/V10__reference_data.sql) / [`infra/postgres/seed/demo_data.sql`](infra/postgres/seed/demo_data.sql) |
| Verification report script | [`infra/postgres/scripts/verify_database.sql`](infra/postgres/scripts/verify_database.sql) |
| Integration tests (real PostgreSQL 17) | [`backend/src/test/java/com/veya/db/`](backend/src/test/java/com/veya/db) |

The complete demonstration (clean database → migrations → demo data → verification report → tests) is in
[Database → Complete demonstration](#complete-demonstration).

## Repository structure

| Path | Contents |
|---|---|
| `backend/` | REST API — Java 21, Spring Boot 4.1, Spring Security/JWT, JPA/Hibernate, PostgreSQL, Flyway, Dockerfile |
| `frontend/` | Web client — React + TypeScript (not yet scaffolded) |
| `docs/` | Business Analysis, BA decisions, database, API, back-end and testing documentation |
| `infra/` | Docker Compose (DB + API, public override with Caddy), role provisioning, demo data, SQL scripts, smoke test |
| `.github/workflows/` | CI pipelines |

## Prerequisites

- JDK 21
- Docker (Docker Desktop on Windows/macOS) — needed for the database and for integration tests

Maven does not need to be installed: use the Maven Wrapper (`./mvnw`, or `mvnw.cmd` on Windows).

## Quick start

```bash
# 0. Create infra/.env with random local passwords (git-ignored; no passwords are stored in the repository)
sh infra/generate-env.sh

# 1. Start PostgreSQL 17 (host port 5433); roles are created on first start
docker compose -f infra/docker-compose.yml up -d

# 2. Run the backend; Flyway migrates the schema as veya_owner, the API connects as veya_app_user
cd backend
./mvnw spring-boot:run
```

Check that it is up:

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

## Database

PostgreSQL 17, schema `veya`, versioned with Flyway (`backend/src/main/resources/db/migration`, V1–V10).
Design, normalization, transactions, indexes, permissions and masking:
[`docs/database/README.md`](docs/database/README.md); diagram: [`er-diagram.md`](docs/database/er-diagram.md);
every table and column: [`data-dictionary.md`](docs/database/data-dictionary.md).

### Roles

Created by `infra/postgres/initdb/01-create-roles.sh` when the data volume is first initialised.
None of them is a superuser.

| Login | Used by | Can |
|---|---|---|
| `veya_owner` | Flyway | own and migrate schema `veya` |
| `veya_app_user` | the API | read/write user data; plan versions only through database routines; no DDL |
| `veya_readonly_user` | reporting | read reference data and masked/anonymised views only |
| `veya_admin_user` | administration, demo data | full DML on all tables; no DDL |

### Complete demonstration

Run from the repository root in Git Bash (Windows), WSL, Linux or macOS. Docker and JDK 21 are required.

```bash
# 1. Local secrets: random passwords in infra/.env (git-ignored). Skips if the file already exists.
sh infra/generate-env.sh

# 2. Clean PostgreSQL 17; the init script creates the roles (none is a superuser)
docker compose -f infra/docker-compose.yml down -v      # removes an old local volume, if any
docker compose -f infra/docker-compose.yml up -d
docker compose -f infra/docker-compose.yml ps           # wait for "healthy"

# 3. Migrations: start the API (Flyway applies V1-V10 as veya_owner; the API connects as veya_app_user)
cd backend && ./mvnw spring-boot:run                    # log: Successfully applied 10 migrations ...
#    second terminal:  curl http://localhost:8080/actuator/health   ->  {"status":"UP"}
#    stop the API with Ctrl+C when done; the schema stays in the database
cd ..

# 4. Demo data (synthetic; normal and edge cases), loaded as veya_admin_user
docker exec veya-postgres sh -c 'PGPASSWORD="$VEYA_ADMIN_PASSWORD" psql -v ON_ERROR_STOP=1 -h localhost -U veya_admin_user -d veya -f /veya/seed/demo_data.sql'

# 5. Verification report: migrations, roles, privilege matrix, object/row counts, masked view, dashboard
docker exec veya-postgres sh -c 'PGPASSWORD="$VEYA_ADMIN_PASSWORD" psql -X -h localhost -U veya_admin_user -d veya -f /veya/scripts/verify_database.sql'

# 6. Automated tests against a fresh PostgreSQL 17 container (independent of steps 2-5)
cd backend && ./mvnw verify
```

**Windows PowerShell** (no `sh`, `./mvnw` or `&&` there) — the same steps, from the repository root:

```powershell
& "C:\Program Files\Git\bin\sh.exe" infra/generate-env.sh          # 1. uses sh.exe from Git for Windows
docker compose -f infra/docker-compose.yml up -d                    # 2.
Set-Location backend; .\mvnw.cmd spring-boot:run                    # 3. stop with Ctrl+C, then: Set-Location ..
docker exec veya-postgres psql -v ON_ERROR_STOP=1 -U veya_admin_user -d veya -f /veya/seed/demo_data.sql   # 4.
docker exec veya-postgres psql -X -U veya_admin_user -d veya -f /veya/scripts/verify_database.sql          # 5.
Set-Location backend; .\mvnw.cmd verify                             # 6.
```

(`docker exec … psql` without `-h` uses the container's local socket, which the official PostgreSQL image
trusts; every connection from outside the container, including the API's, requires the password.)

Interactive sessions (passwords are read inside the container from its environment):

```bash
docker exec -it veya-postgres sh -c 'PGPASSWORD="$VEYA_READONLY_PASSWORD" psql -h localhost -U veya_readonly_user -d veya'
docker exec -it veya-postgres sh -c 'PGPASSWORD="$VEYA_APP_PASSWORD" psql -h localhost -U veya_app_user -d veya'
```

Try, as `veya_readonly_user`: `SELECT * FROM v_app_user_masked;` works, `SELECT * FROM app_user;` is denied.

### Reset to a clean database

```bash
docker compose -f infra/docker-compose.yml down -v   # deletes the local data volume
docker compose -f infra/docker-compose.yml up -d     # re-creates roles; start the API to migrate again
```

### Regenerate the data dictionary (after a schema change)

```bash
docker exec veya-postgres sh -c 'PGPASSWORD="$VEYA_ADMIN_PASSWORD" psql -X -q -At -h localhost \
    -U veya_admin_user -d veya -f /veya/scripts/data_dictionary.sql' > docs/database/data-dictionary.md
```

### Configuration

| Variable | Default | Source |
|---|---|---|
| `VEYA_DB_URL` | `jdbc:postgresql://localhost:5433/veya?currentSchema=veya` | environment |
| `VEYA_DB_USERNAME` / `VEYA_APP_PASSWORD` | `veya_app_user` / — | `infra/.env` or environment |
| `VEYA_FLYWAY_USERNAME` / `VEYA_OWNER_PASSWORD` | `veya_owner` / — | `infra/.env` or environment |
| `VEYA_SERVER_PORT` | `8080` | environment |

Passwords have no defaults anywhere in the repository. `infra/generate-env.sh` writes them to `infra/.env`,
which Docker Compose reads and the API imports when started from `backend/`; `infra/.env.example` lists the
variables.

## Tests

```bash
cd backend
./mvnw verify
```

Integration tests start a disposable PostgreSQL 17 container with Testcontainers (Docker must be running),
provisioned with the same role script as Docker Compose; the tests connect as the real non-superuser roles.
They cover schema creation, constraints, relationships, privileges, triggers, the recovery transactions
(including rollback and concurrent decisions), masking, and loading the demo data into a clean database.
CI runs the same command on pushes to `main` and on pull requests that touch `backend/`, `infra/postgres/`,
`docs/database/` or the workflow file.
