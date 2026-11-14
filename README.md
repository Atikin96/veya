# VEYA

Web-based system for adaptive meal and physical activity planning with **Dynamic Plan Recovery**.
Diploma project, European Humanities University (2026).

Requirements: [`docs/BA_Malafei_Mikita.docx`](docs/BA_Malafei_Mikita.docx) (Business Analysis).
Open BA questions and agreed interpretations: [`docs/ba-open-questions.md`](docs/ba-open-questions.md).

## Repository structure

| Path | Contents |
|---|---|
| `backend/` | REST API — Java 21, Spring Boot 4.1, Maven, PostgreSQL, Flyway |
| `frontend/` | Web client — React + TypeScript (not yet scaffolded) |
| `docs/` | Business Analysis and project documentation |
| `infra/` | Docker Compose for local infrastructure |
| `.github/workflows/` | CI pipelines |

## Prerequisites

- JDK 21
- Docker (Docker Desktop on Windows/macOS) — needed for the database and for integration tests

Maven does not need to be installed: use the Maven Wrapper (`./mvnw`, or `mvnw.cmd` on Windows).

## Quick start

```bash
# 1. Start PostgreSQL 17 (host port 5433)
docker compose -f infra/docker-compose.yml up -d

# 2. Run the backend
cd backend
./mvnw spring-boot:run
```

Check that it is up:

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

Flyway applies database migrations automatically on startup.

### Configuration

| Variable | Default |
|---|---|
| `VEYA_DB_URL` | `jdbc:postgresql://localhost:5433/veya` |
| `VEYA_DB_USERNAME` | `veya` |
| `VEYA_DB_PASSWORD` | `veya` |
| `VEYA_SERVER_PORT` | `8080` |

Compose settings can be overridden by copying `infra/.env.example` to `infra/.env`.
The defaults are for local development only.

## Tests

```bash
cd backend
./mvnw verify
```

Integration tests start a disposable PostgreSQL 17 container with Testcontainers, so Docker must be running.
CI runs the same command on pushes to `main` and on pull requests that touch `backend/`
or the workflow file.
