# Assessment matrix — BACK-END (Stage 3)

Verified 2026-11-23. PASS = implemented **and** verified by the command/result shown. Commands from the repository root
(Windows PowerShell) unless noted; `verify` = `cd backend; .\mvnw.cmd clean verify`.

## Minimum requirements

| # | Requirement | Status | Evidence | Verification | Actual result | Remaining |
|---|---|---|---|---|---|---|
| 1 | Modern framework (Spring Boot) | PASS | `backend/pom.xml` (Boot 4.1.2, Java 21) | `verify` | BUILD SUCCESS | — |
| 2 | PostgreSQL database | PASS | Flyway V1–V10, `infra/docker-compose.yml` | `VeyaApplicationTests.connectsToPostgresql17` | `server_version` 17.x | — |
| 3 | Real ORM (Hibernate/JPA) used by the functionality | PASS | 9 entities in `*/domain`, 8 Spring Data repositories, `ddl-auto: validate` ([README §4](README.md#4-data-access-jpahibernate-and-jdbc)) | API tests of identity, profile, diary, saved meals, workouts | Hibernate validates all mappings at start-up; 45 API tests pass through the JPA layer | — |
| 4 | JDBC only where justified | PASS | documented per class ([README §4](README.md#4-data-access-jpahibernate-and-jdbc)) | `RecoveryApiTests`, `PlanningApiTests` | SECURITY DEFINER routines, views, partial-index upsert | — |
| 5 | Layered architecture (presentation / business / data access) | PASS | `web` / `application`+`domain` / `infrastructure` per module | code review; `OpenApiContractTests` | controllers contain no SQL; repositories no HTTP | — |
| 6 | SOLID, module boundaries, no duplication | PASS | modules use only `*.api` of other modules; one `HardConstraints` rule set; shared `SqlLike`, `ProblemDetails` | unit tests of the shared rules | rules reused by 4 features | — |
| 7 | No placeholders / fake responses | PASS | no TODO stubs; EDIT returns a documented 400 instead of a fake success | `grep -rn TODO backend/src/main` | 0 matches | Q12 EDIT (decision) |
| 8 | Real REST API following the OpenAPI contract | PASS (44/45) + PARTIAL (1) | `docs/api/openapi.yaml` statuses | every API test validates responses against the schema; `OpenApiContractTests` | 44 IMPLEMENTED, 1 PARTIAL (EDIT blocked) | Q12 |
| 9 | Correct methods, status codes, validation, authorization | PASS | Bean Validation DTOs, ownership checks | API tests (400/401/403/404/409/422/502/504 asserted) | all pass | — |
| 10 | RFC 9457 Problem Details, stable error codes | PASS | `GlobalExceptionHandler`, `ErrorCode`, `ProblemAuthenticationEntryPoint` | `assertProblem(...)` in API tests; `SharedUnitTest.errorCodes` | every error has `type`, `code`, `traceId` | — |
| 11 | OpenAPI documentation | PASS | `GET /v3/api-docs`, `docs/api/` | `SystemApiTests.apiDocsServeThePublishedContract`; `python docs/api/tools/check_contract.py` | identical to the contract; RESULT: PASSED | — |
| 12 | Structured logging of important operations and errors, no sensitive data | PASS | `RequestIdFilter`, service INFO events, ECS JSON in `prod` | `docker logs veya-api`; grep for secrets | JSON lines with `requestId`; 0 matches for password/secret/Bearer | — |
| 13 | Production-like environment accessible to **external** users | **PARTIAL** | `backend/Dockerfile`, `infra/docker-compose.yml` (`--profile app`), `infra/docker-compose.public.yml`, `infra/caddy/Caddyfile`, [DEPLOYMENT.md](DEPLOYMENT.md) | local: Compose + smoke test | local Docker deployment healthy, smoke test 35/35 HTTP checks + DB row checks passed; **PUBLIC DEPLOYMENT NOT VERIFIED** | create a VM + DNS, run DEPLOYMENT.md §4, record URL |
| 14 | Dockerfile, reproducible app + DB deployment, env secrets, health checks, docs | PASS | Dockerfile (non-root, HEALTHCHECK), Compose with healthchecks, `generate-env.sh` | `docker compose ... --profile app up -d --build` | `veya-api healthy`, `veya-postgres healthy` | — |
| 15 | ≥ 70 % coverage of business logic (JaCoCo), HTML report, CI gate | PASS | JaCoCo in `pom.xml`, gate in `verify`; CI artifact | `verify`; `jacoco:check@check -Dcoverage.business.branch=0.95` | business logic **95.2 % lines / 86.0 % branches**; gate fails (exit 1) at 95 % | — |
| 16 | No hard-coded secrets | PASS | env only; random test secrets | `git grep -nE "(password|secret)\s*[:=]\s*\S{8,}"` on the change set | no real secrets | — |
| 17 | Modern password hashing | PASS | BCrypt cost 12 (`SecurityConfig`) | `IdentityApiTests.registerCreatesAccount` | hash `$2a$12$…` stored | — |
| 18 | No known critical dependency vulnerabilities | PASS (point in time) | `.github/scripts/osv_check.py` | OSV query of 107 runtime dependencies | 0 known vulnerabilities (2026-11-23) | re-run in CI |
| 19 | No custom cryptography | PASS | Spring Security / Nimbus JOSE / BCrypt only | code review | — | — |
| 20 | JSON as main format | PASS | `application/json`, `application/problem+json` | API tests check content types | — | — |
| 21 | Reproducible deployment from Git, maintainable docs | PASS (local) | README, this folder | clone → `generate-env.sh` → Compose → smoke | verified on Windows | public run |

## Maximum-grade items

| Requirement | Status | Evidence / result | Remaining |
|---|---|---|---|
| Automated Docker image build (CI) | PASS (configured) | job `image`: build + Compose + smoke test | first green run after push |
| Automated deployment (CD) | NOT IMPLEMENTED | no target environment/credentials | add deploy step once a server exists |
| Quality gates | PASS | coverage gate, javac `-Xlint:all -Werror`, contract check, OSV check, generated-docs diff check | — |
| Request IDs + structured logs | PASS | `X-Request-Id`, MDC, `traceId`, ECS JSON | — |
| Monitoring (Micrometer/Actuator) | PARTIAL | health/liveness/readiness, `/actuator/metrics` (authenticated) | no Prometheus/Grafana (not justified for the MVP) |
| Caching where justified | NOT APPLICABLE | no measured need (catalogue read in milliseconds); not added artificially | — |
| CI/CD improvements | PASS | 3 jobs, artifacts, job summary with coverage | — |
| Integration with an external provider + fallback | PASS (stub-verified) | `OpenFoodFactsProvider`, `FoodSearchService`, tests against a stub | live provider disabled by default (AQ-7) |
| Dynamic Plan Recovery (core feature) | PASS | engine + API + E2E + concurrency + rollback tests | EDIT (Q12) |
