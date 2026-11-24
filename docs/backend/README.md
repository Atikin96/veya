# VEYA back-end — technical documentation

Spring Boot REST API of VEYA (adaptive meal and activity planning with **Dynamic Plan Recovery**).
Requirements: BA [`docs/BA_Malafei_Mikita.docx`](../BA_Malafei_Mikita.docx) (27 Must stories); agreed interpretations:
[`docs/ba-open-questions.md`](../ba-open-questions.md). Assessment evidence: [`ASSESSMENT.md`](ASSESSMENT.md).
Deployment: [`DEPLOYMENT.md`](DEPLOYMENT.md). Automated tests: [`docs/testing/README.md`](../testing/README.md).

## 1. Technologies and why

| Technology | Version | Why |
|---|---|---|
| Java | 21 (LTS) | Records, pattern matching, text blocks; long-term support |
| Spring Boot | 4.1.2 | Mature web, security, validation, data and actuator stack; BA §2.4 |
| Spring Security + OAuth2 Resource Server (Nimbus JOSE) | 7.1 | Standard, maintained JWT validation — no custom cryptography |
| Spring Data JPA / Hibernate ORM | 7.4 | ORM for the CRUD aggregates (accounts, consents, profile, constraints, diary, saved meals, workouts) |
| Spring JDBC (`JdbcClient`) | 7 | Calls to the database routines and view/aggregate queries (see §4) |
| PostgreSQL | 17 | Relational integrity, triggers, SECURITY DEFINER routines, trigram search; DB criterion |
| Flyway | 11 | Versioned, reproducible schema (V1–V10, never edited after being applied) |
| Jakarta Bean Validation | 3.1 | Declarative request validation → `VALIDATION_FAILED` field errors |
| Jackson | 3 | JSON (camelCase), strict request parsing |
| Micrometer / Actuator | Boot 4.1 | Health, liveness/readiness probes, metrics |
| JaCoCo | 0.8.13 | Coverage report and CI gate |
| Docker / Docker Compose | — | Reproducible PostgreSQL + API deployment |

## 2. Architecture

Modular monolith (BA §2.4, Fig. B.12), root package `com.veya`, **package by module, then by layer**:

| Layer | Package | Responsibility |
|---|---|---|
| Presentation | `<module>.web` | REST controllers, request/response DTOs (records), Bean Validation |
| Business logic | `<module>.application`, `<module>.domain` | Use-case services (transactions, ownership, rules), pure domain logic and JPA entities |
| Data access | `<module>.infrastructure` | Spring Data JPA repositories, JDBC repositories, external adapters |
| Published module API | `<module>.api` | The only types other modules may use (interfaces + value records) |

| Module | Stories | Main classes |
|---|---|---|
| `identity` | US-01, US-04, US-30 | `AccountService`, `ConsentService`, `SecurityConfig`, `TokenService`, entities `AppUser`, `UserConsent`, `ConsentDocument` |
| `profile` | US-02, US-03 | `ProfileService`, `DailyConstraintService`, entities `UserProfile`, `DailyConstraint` |
| `nutrition` | US-05–US-08 | `FoodDiaryService`, `SavedMealService`, `FoodSearchService`, entities `FoodEntry`, `SavedMeal`, `SavedMealItem` |
| `activity` | US-12, US-13 | `ActivityService`, entity `ActivityEntry` |
| `planning` | US-09, US-10, US-12, US-14, US-15 | `PlanService`, `MealPlanner` (domain), `HardConstraints`, `Alternatives` (pure, published), `JdbcPlanStore` |
| `recovery` | US-15–US-19 | `RecoveryEngine` (pure domain), `RecoveryService`, `ConstraintFeasibilityChecker`, `RecoveryRepository` |
| `history` | US-20 | `DaySummaryService` |
| `integration` | US-05, US-31 | `FoodProvider` port, `OpenFoodFactsProvider` adapter |
| `shared` | US-29, NFR-14, NFR-18 | `GlobalExceptionHandler`, `ErrorCode`, `DatabaseErrors`, `RequestIdFilter`, `ReferenceCodes`, `/v3/api-docs` |

SOLID in practice: single-purpose services; the hard-constraint rules exist **once** (`HardConstraints`) and are reused
by plan creation, replacement options, the constraint-change check and the recovery engine; modules depend on
interfaces (`PlanQueries`, `PlanReferences`, `Deviations`, `ProfileLookup`, `ConstraintFeasibility`, `FoodProvider`),
never on another module's implementation (dependency inversion, open for a second food provider). The recovery engine
and the meal planner are pure functions without Spring or database access, so they are unit-tested with fixed data.

## 3. REST API

* Contract (single source of truth): [`docs/api/openapi.yaml`](../api/openapi.yaml) — OpenAPI 3.1, 45 operations.
* Served by the running API as JSON: `GET /v3/api-docs` (the same file, packaged into the jar).
* Endpoint catalogue: [`docs/api/API_ENDPOINTS.md`](../api/API_ENDPOINTS.md); Postman: [`docs/api/postman/`](../api/postman/).
* Status: **44 IMPLEMENTED, 1 PARTIAL** (`decideRecoveryProposal`: ACCEPT/REJECT; EDIT blocked by Q12).
  `OpenApiContractTests` fails if a handler is missing from the contract or the statuses are wrong; every API
  integration test validates the real response body against the documented schema.
* JSON everywhere (`application/json`, errors `application/problem+json`); unknown request fields and fractional values
  for integer fields are rejected with 400 (`spring.jackson.deserialization.*`).

## 4. Data access: JPA/Hibernate and JDBC

**ORM (Hibernate 7 via Spring Data JPA)** — 9 entities, 8 repositories, `ddl-auto: validate` (Hibernate checks every
mapping against the Flyway schema at startup):

| Entity | Table(s) | Mapping features |
|---|---|---|
| `AppUser` | `app_user` | generated UUID, DB-generated timestamps (`@Generated`), `@DynamicUpdate` (app role may only update e-mail/hash) |
| `ConsentDocument` | `consent_document` | `@Immutable` reference data |
| `UserConsent` | `user_consent` | `@ManyToOne` to `ConsentDocument`, withdraw-once rule |
| `UserProfile` | `user_profile` + `profile_food_exclusion` | `@ElementCollection` of exclusion codes |
| `DailyConstraint` | `daily_constraint` + `_equipment` + `_ingredient` | `@ElementCollection` set + `@ElementCollection` map (code → AVAILABLE/EXCLUDED) |
| `FoodEntry` | `food_entry` | `@Formula` for the linked plan item key and slot order, merge-patch `correct(...)` |
| `SavedMeal`, `SavedMealItem` | `saved_meal`, `saved_meal_item` | aggregate with `@OneToMany(cascade, orphanRemoval)`, `@EmbeddedId` + `@MapsId` composite key, `Persistable` |
| `ActivityEntry` | `activity_entry` | `@Formula` exercise name / item key, invariant in the constructor |

Repositories: `UserRepository`, `ConsentRepository`, `ConsentDocumentRepository` (JPQL), `ProfileRepository`,
`DailyConstraintRepository`, `FoodEntryRepository` (JPQL + one native `DISTINCT ON` query), `SavedMealRepository`
(fetch join), `ActivityEntryRepository` (derived queries).

**JDBC where it is the right tool** (each class documents why):

| Class | Why not JPA |
|---|---|
| `JdbcPlanStore` | plan versions are immutable and can only be created by the SECURITY DEFINER function `create_plan_version` (the app role has no INSERT); reads are projections of `v_active_plan_item` / `v_meal_nutrition` |
| `RecoveryRepository` | decisions only via `CALL apply_recovery_decision` (ownership + version check under a row lock, decision + version in one transaction); status from `v_recovery_proposal_overview` |
| `JdbcDeviations` | `ON CONFLICT ... WHERE resolved_at IS NULL` on a partial unique index |
| `UserDataExport` | one consistent JSON snapshot by `export_user_data` |
| `CatalogRepository`, `ExerciseCatalog`, `DaySummaryRepository`, `DailyNutritionView` | trigram/LIKE search with arrays, views `v_meal_nutrition`, `v_day_summary`, `v_daily_nutrition_summary` |

JPA and the routines share one transaction (`JpaTransactionManager`); services `saveAndFlush` before JDBC reads so
database triggers (e.g. deviation detection after a diary correction) run inside the same transaction. Verified by the
integration tests, e.g. `RecoveryApiTests.acceptIsAtomic` (rollback of decision + version) and `concurrentDecisions`.

## 5. Database integration

PostgreSQL 17, schema `veya`, Flyway V1–V10 (unchanged in this stage). Flyway migrates as `veya_owner`; the API connects
as `veya_app_user` (no DDL, no superuser, no UPDATE/DELETE on history tables). Details:
[`docs/database/README.md`](../database/README.md). Database error codes are mapped centrally (`DatabaseErrors`):
`VY400 → 400`, `VY404 → 404`, `VY409 / VY001 → 409`; constraint DETAIL texts (which may contain values) are never returned.

## 6. Security

* **Authentication**: stateless JWT bearer tokens (HS256, Nimbus), claims `sub` (user id), `iss`, `iat`, `exp`, `jti` only;
  60-minute lifetime (AQ-5 proposed, `VEYA_JWT_TTL`); a token of a deleted account is rejected (401).
* **Password hashing**: BCrypt, cost 12 (`VEYA_BCRYPT_STRENGTH`); passwords > 72 bytes rejected instead of truncated;
  identical response and cost for unknown e-mail and wrong password (no account probing).
* **Authorization**: every resource is checked against the caller; another user's resource → **403 ACCESS_DENIED**,
  unknown id → **404** (contract AQ-2); the database routines check ownership again (defence in depth).
* **Secrets**: none in the repository. `VEYA_JWT_SECRET` and database passwords come from the environment / git-ignored
  `infra/.env` (`infra/generate-env.sh`); the application refuses to start without a ≥ 256-bit key; tests generate
  random keys and passwords per run.
* **CORS** for the React client (`VEYA_CORS_ALLOWED_ORIGINS`), no credentials/cookies, CSRF not applicable.
* Public endpoints: health probes, `/v3/api-docs`, register, login, consent documents. `/actuator/metrics` needs a token;
  `env`, `beans`, `configprops` are not exposed.
* Dependencies: 107 runtime dependencies checked against OSV (osv.dev) on 2026-11-23 — 0 known vulnerabilities; the
  check runs in CI (`.github/scripts/osv_check.py`).

## 7. Error handling

`GlobalExceptionHandler` renders **every** error as RFC 9457 Problem Details (`type`, `title`, `status`, `detail`,
`instance`, `code`, optional `errors[]`, `blockingConstraints[]`, `currentVersion`, `manualEntryAllowed`, `traceId`).
Stable codes = the `ErrorCode` enum of the contract (e.g. `VALIDATION_FAILED`, `AUTHENTICATION_REQUIRED`,
`ACCESS_DENIED`, `STALE_PLAN_VERSION`, `RECOVERY_NOT_FEASIBLE`). Security errors (401/403 from the filter chain) use the
same format (`ProblemAuthenticationEntryPoint`). Passwords are never echoed as `rejectedValue`.

## 8. Structured logging and monitoring

* `RequestIdFilter`: request id from a safe `X-Request-Id` or a new UUID → MDC `requestId`, response header, and
  `traceId` in every Problem Details body.
* One access line per API request (`veya.access`): method, path, status, duration — no query string, header, body or token.
* Business events at INFO with ids only (e.g. `Recovery decision userId=… proposalId=… decision=ACCEPT activeVersion=2`);
  never e-mail, password, token or diary values (LEG-5, NFR-08).
* Profile `prod` (Docker image): JSON logs in Elastic Common Schema on stdout (Spring Boot structured logging).
* Actuator: `/actuator/health`, `/health/liveness`, `/health/readiness` (Docker HEALTHCHECK), `/actuator/metrics` (authenticated).

## 9. Running

```powershell
& "C:\Program Files\Git\bin\sh.exe" infra/generate-env.sh                      # random secrets in infra\.env (once)
docker compose -f infra/docker-compose.yml --profile app up -d --build          # PostgreSQL + API container
curl.exe http://localhost:8080/actuator/health/readiness                        # {"status":"UP"}
powershell -ExecutionPolicy Bypass -File infra\smoke\smoke-test.ps1 -Db         # UC-01 end-to-end over HTTP
```

Development alternative: `docker compose -f infra/docker-compose.yml up -d` and `cd backend; .\mvnw.cmd spring-boot:run`.
Production/public deployment: [`DEPLOYMENT.md`](DEPLOYMENT.md).

## 10. Dynamic Plan Recovery — implemented rules

`RecoveryEngine` (deterministic, no LLM): frozen = items not PLANNED (never changed); remaining energy target = profile
target − confirmed diary kcal; candidates filtered by `HardConstraints` (permanent/day exclusions, available time,
equipment; ≤ 100 candidates, NFR-02); meal combinations ranked by `|remaining target − planned kcal| + 50 kcal × changed
meals`; an infeasible workout is replaced or removed; a skipped/missed workout gets one feasible ADD; each change has a
reason with a US-17 category; nothing feasible → 422 `RECOVERY_NOT_FEASIBLE` with `blockingConstraints` and no new version.
ACCEPT/REJECT go through `apply_recovery_decision` (one transaction, stale `baseVersion` → 409, concurrent decisions:
exactly one wins).

## 11. Known gaps and decisions needing approval

| Item | Status |
|---|---|
| **Q12 — EDIT decision** | **Not implemented** (400 `INVALID_EDIT`). The BA (US-18 “accept, edit or reject”; Fig. B.10 shows an *Edit* action per proposed item) implies editing the proposed values of individual items. The database supports only applying a *subset* of changes (`selectedChangeIds`) — that is **not** per-item editing. Proposed implementation: `EDIT` with `edits: [{changeId, mealId? / exerciseId? + durationMin?}]`, each edited value re-validated by `HardConstraints` (400 `INVALID_EDIT` otherwise), then stored as an edited change set and applied by `apply_recovery_decision` as `RECOVERY_EDITED`. Needs a new migration (edited values) and team approval. |
| AQ-4 consent withdrawal effects | withdrawal is recorded; no processing restriction after withdrawing the health-data consent (open) |
| AQ-5 token lifetime / refresh | 60 min, no refresh (proposed) |
| AQ-7 food provider | Open Food Facts adapter implemented and tested against a stub, **disabled by default**; not verified against the live service in CI |
| AQ-13 rule 3, AQ-14 target range | implemented as proposed (configurable tolerance, default ±20 %) |
| Public deployment | **live** at https://veya-api-demo.duckdns.org (AWS EC2, verified externally 2026-11-24); deployment is manual (no CD step) — see [`DEPLOYMENT.md`](DEPLOYMENT.md) |
| Frontend | not part of this repository yet (`frontend/` has only a README) |
| Caching | not added: the only cache candidate (catalogue, 24 foods / 12 meals) is read in < 5 ms; no measured need |
