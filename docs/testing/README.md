# VEYA — automated testing

Evidence for the "Automated tests" assessment. Checklist: [`ASSESSMENT.md`](ASSESSMENT.md).
All numbers below are from `./mvnw clean verify` on 2026-11-23 (Windows 11, Docker Desktop, JDK 21.0.11).

## 1. Strategy and test pyramid

| Level | What | Tools | Count |
|---|---|---|---|
| Unit | Pure business logic and single classes, no Spring context, no database (`@Tag("unit")`, names `*Test`) | JUnit 5, AssertJ, Mockito, parameterized tests | **73** tests in 11 classes |
| Integration — REST API | Full application on a random port, real HTTP, real PostgreSQL 17; every response validated against `docs/api/openapi.yaml` | Spring Boot Test, Testcontainers, JDK HttpClient, networknt JSON Schema | **45** tests in 9 classes |
| Integration — database | Schema, constraints, triggers, routines, roles/privileges, migrations from a clean environment | Testcontainers PostgreSQL 17 (same role init script as Compose) | **136** tests in 8 classes |
| Integration — configuration | Context start-up, PostgreSQL version, non-superuser connection, Flyway as owner, health probes, hidden actuator endpoints | Spring Boot Test | **10** tests in 2 classes |
| End-to-end | UC-01 scenario over HTTP against the deployed Docker containers (35 HTTP checks + persisted-row checks with psql) | `infra/smoke/smoke-test.ps1` (PowerShell 5.1 and 7) | 1 script, run locally and in CI |
| **Total (Maven)** | | | **264 tests, 0 failures, 0 errors, 0 skipped** |

No H2 or in-memory database is used: every database test runs against PostgreSQL 17 with the real roles.
UI tests: **not applicable** — the repository has no implemented frontend yet (`frontend/` contains only a README).

## 2. Test classes and what they verify

### Unit (`./mvnw test -Dgroups=unit`)
| Class | Verifies |
|---|---|
| `planning.api.HardConstraintsTest` | exclusion (profile vs day), time boundary (=, +1, 0, null), equipment unknown/empty/available, blocking summary |
| `planning.api.AlternativesTest` | ranked compliant meal/exercise options, reasons, option limit, shortened duration, no-option cases, conservative suggestions with unknown equipment (regression) |
| `planning.domain.MealPlannerTest` | closest distinct combination, determinism, tolerance boundaries (parameterized), no target, slot without candidates |
| `recovery.domain.RecoveryEngineTest` | 14 fixed-data scenarios: UC-01 (forecast 1871.7 kcal), frozen items untouched, over-eating, nothing to improve, no candidate (US-19), workout replace/remove, no target, bounded search fallback, determinism |
| `recovery.application.RecoveryServiceTest` | Mockito doubles: EDIT blocked, 403/404 before the procedure, stale version, already decided, lost concurrent race, preconditions, totals |
| `recovery.application.ConstraintFeasibilityCheckerTest` | US-15 rule 3 only for PLANNED items, reasons |
| `nutrition.application.FoodSearchServiceTest` | provider fallback rules: disabled, OK + cache merge, 200 UNAVAILABLE, 502, 504 |
| `integration.food.OpenFoodFactsProviderTest` | adapter against a local stub server: normalization, invalid products skipped, HTTP 500, timeout, unreachable |
| `nutrition.domain.EntityRulesTest` | JPA entity invariants: merge-patch correction, saved-meal numbering/totals, workout duration rule, PUT semantics |
| `identity.IdentityUnitTest` | startup validation of secrets/TTL/bcrypt, token claims and lifetime, 72-byte password rule (multi-byte), consent withdraw-once |
| `shared.SharedUnitTest` | database error mapping (VY400/404/409/001, 23xxx), no leaking of constraint details, LIKE escaping, unique problem types, request-id filter |

### Integration — REST API (`com.veya.api`)
| Class | Verifies |
|---|---|
| `IdentityApiTests` | register (bcrypt, adult confirmation, consents), validation, duplicate e-mail, login, 401 for missing/malformed/foreign-key/expired/deleted-account tokens, consents 403/404/409, export, deletion |
| `ProfileApiTests` | profile CRUD, ranges/codes/decimals, constraints per user and day, US-15 rule 3 deviations (idempotent) |
| `NutritionApiTests` | search, meals without exclusions, diary create/validate/correct/delete, 403/404, plan links (403, 400, 409 history), recent, saved meals, daily totals |
| `ActivityApiTests` | exercises, workout records, duration rules, skipped planned workout → status + deviation, 409 second record |
| `PlanningApiTests` | suggestion (US-09), plan creation/validation/422/409, 403/404, options with reasons, replacement + stale 409, item status (≥ 200 kcal rule vs 100 kcal), day summary |
| `RecoveryApiTests` | **UC-01 end to end**, REJECT, 422 no solution, **concurrent ACCEPT (4 threads → exactly one 200)**, **atomic rollback of decision + version**, optional body (regression) |
| `FoodProviderApiTests` | provider enabled against a stub: caching with foodId, 200 UNAVAILABLE, 502, 504 |
| `SystemApiTests` | `/v3/api-docs` equals the contract, reference data, Problem Details for 404/405, CORS preflight, contract checker self-test |
| `OpenApiContractTests` | every controller handler is documented with the right status in the contract and vice versa |

### Integration — database (`com.veya.db`, from the database criterion, preserved)
`SchemaStructureTests`, `ConstraintAndRelationshipTests`, `PlanVersioningTests`, `DeviationDetectionTests`,
`RecoveryDecisionTests`, `RolePrivilegeTests`, `ExportAndMaskingTests`, `CleanEnvironmentSeedTests` (fresh container:
roles + migrations + demo seed). Plus `VeyaApplicationTests`, `HealthEndpointTests`.

## 3. FIRST principles

* **Fast** — unit tests run in < 3 s in total; the integration suites share one PostgreSQL container per JVM.
* **Independent** — each test creates its own users (random e-mails) and data; no ordering assumptions.
* **Repeatable** — fixed fixtures (`planning.api.Fixtures`), deterministic engine, random secrets per run, local stub
  instead of the real food provider, Testcontainers instead of a shared database.
* **Self-validating** — every test asserts results (status, body, persisted rows); API responses are additionally
  validated against the OpenAPI schemas; no test only "calls" code.
* **Timely** — tests were written with the features; regression tests were added for each defect found (§7).

## 4. Fixtures, test doubles, boundary and negative cases

* Fixtures: `planning.api.Fixtures` (in-memory catalogue/plan), `api.PlanFixtures` (plans via `create_plan_version`),
  `ApiTestSupport` (users, HTTP calls, contract validation), `VeyaTestDatabase` (PostgreSQL 17 with real roles).
* Test doubles: Mockito mocks for repositories and the provider port; `StubFoodProvider` (local HTTP server with
  OK / HTTP 500 / slow modes) for the external food provider — no real network calls.
* Boundaries: time `=`/`+1`/`0`/`null`, tolerance exactly at / just beyond the limit, 72 vs 73 bytes, duration 0/1/600/601,
  limits 0/1/50/51/100/101, one decimal vs two, invalid dates (`2026-13-45`).
* Negative/authorization: missing, malformed, foreign-signed, expired tokens and tokens of deleted accounts (401);
  another user's consent, entry, saved meal, plan, plan item, proposal (403); unknown ids (404); duplicate e-mail,
  consent, saved-meal name, plan per date, status recorded twice, stale version, decided proposal (409); hard
  constraints and infeasible recovery (422); provider failures (502/504).
* Transactions and concurrency: `RecoveryApiTests.acceptIsAtomic`, `concurrentDecisions`, plus the database-level
  `RecoveryDecisionTests` / `PlanVersioningTests`; rejected requests leave no rows (e.g. 422 plan creation).

## 5. Coverage (JaCoCo 0.8.13)

Metric definitions: *line coverage* = executable source lines executed at least once; *branch coverage* = outcomes of
`if`/`switch`/`?:`/boolean operators taken; *business logic* = packages `com.veya.*.domain`, `com.veya.*.application`
and `com.veya.planning.api` (the pure rule/engine code). **No class is excluded.**

| Scope | Lines | Branches | Instructions |
|---|---|---|---|
| Whole application | **95.0 %** (1913/2013) | **82.8 %** (649/784) | 96.3 % |
| Business logic | **95.2 %** (1092/1147) | **86.0 %** (424/493) | 96.4 % |

Quality gate (`backend/pom.xml`, phase `verify`): whole application lines ≥ 70 %; **every** business-logic package
lines ≥ 80 % and branches ≥ 70 %. Verified that it fails: `./mvnw jacoco:check@check -Dcoverage.business.branch=0.95`
→ exit code 1 ("Rule violated for package com.veya.recovery.domain: branches covered ratio is 0.84 …"); with the
configured thresholds → exit code 0 ("All coverage checks have been met").

Open the HTML report after a build: `backend/target/site/jacoco/index.html` (also `jacoco.xml`, `jacoco.csv`); in CI it
is the downloadable artifact **`jacoco-coverage-report`**.

Known gaps (lowest branch coverage): `activity.infrastructure` and `nutrition.infrastructure` (50 %, 1 of 2 branches -
the `null` branch of LIKE escaping for an omitted search text), `planning.web` (60 %, null handling of optional request
fields), `shared.web` (65.7 %, defensive branches for rare Spring MVC errors such as 406). Uncovered lines are mostly
defensive code (e.g. `InterruptedException` handling).

## 6. CI (`.github/workflows/backend.yml`)

| Job | Steps |
|---|---|
| `contract` | regenerate catalogue + Postman and fail on a diff; `check_contract.py` (OpenAPI 3.1 validation, examples vs schemas, metadata, Postman/catalogue consistency) |
| `build` | static check (javac `-Xlint:all -Werror`); **unit tests**; **integration tests + JaCoCo report + coverage gate** (`verify`); coverage summary in the job summary; artifacts `test-reports`, `jacoco-coverage-report`; OSV dependency vulnerability check |
| `image` | build the Docker image, start PostgreSQL + API with Compose, wait for readiness, run the end-to-end smoke test |

Note: the workflow is committed with this stage; its first run on GitHub happens after the push, so CI results are
evidence only once that run is green (link it in the submission).

## 7. Defects found by the tests (and fixed)

| # | Found by | Defect | Fix |
|---|---|---|---|
| 1 | `NutritionApiTests.searchFoods` | 500 on food search: the row mapper mapped `kcalPer100g` to column `kcal_per100g` (digits are not split) | explicit column aliases |
| 2 | `ActivityApiTests` | 500 on workout records after the JPA migration: `refresh()` is not allowed on `@Immutable` entities | immutability via non-updatable columns |
| 3 | `NutritionApiTests.savedMeals` | 500: `SELECT DISTINCT` with `ORDER BY lower(name)` is invalid in PostgreSQL | removed `distinct` (fetch join is de-duplicated by Hibernate) |
| 4 | `SchemaStructureTests.referenceDataLoaded` (full suite) | test-isolation defect: counted runtime provider-cache rows as V10 reference data | count `source = 'LOCAL'` rows only |
| 5 | smoke test (real HTTP) | recovery suggested *Kettlebell swings* although the user had not declared any equipment | suggestions with unknown equipment are equipment-free; regression unit test |
| 6 | smoke test (real HTTP) | 415 for `POST …/recovery-proposals` without body when the client sends a form Content-Type, although the body is optional | body parsed leniently; regression API test |
| 7 | smoke test in PowerShell 7 | `application/problem+json` bodies arrive as bytes in PS 7 | script decodes bytes (CI uses PS 7) |
| 8 | `-Xlint -Werror` | redundant casts, deprecated Jackson API in tests | removed |

## 8. Running the tests locally

```powershell
cd backend
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21.0.11"     # any JDK 21; Docker Desktop must be running
.\mvnw.cmd test -Dgroups=unit -Djacoco.skip=true           # unit tests only (~10 s, no Docker needed)
.\mvnw.cmd verify                                          # all 264 tests + JaCoCo report + coverage gate
start target\site\jacoco\index.html                        # open the coverage report
.\mvnw.cmd test -Dtest=RecoveryApiTests                    # one class
```
End-to-end against containers: see [`docs/backend/DEPLOYMENT.md`](../backend/DEPLOYMENT.md) §3.
Contract checks: `python -m pip install pyyaml openapi-spec-validator==0.7.1 jsonschema; python docs/api/tools/check_contract.py`.
