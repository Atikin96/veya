# Assessment matrix — AUTOMATED TESTS (Stage 3)

Verified 2026-11-23 with `cd backend; .\mvnw.cmd clean verify` → **264 tests, 0 failures, 0 errors, 0 skipped,
BUILD SUCCESS, all coverage checks met**. Details: [`README.md`](README.md).

## Minimum requirements

| # | Requirement | Status | Evidence | Verification | Actual result | Remaining |
|---|---|---|---|---|---|---|
| 1 | Well-structured, documented test setup | PASS | `docs/testing/README.md`; packages `com.veya.api`, `com.veya.db`, unit tests next to the code | — | — | — |
| 2 | ≥ 70 % measured coverage | PASS | JaCoCo report `backend/target/site/jacoco/` | `verify` | overall **95.0 % lines / 82.8 % branches**; business logic **95.2 % / 86.0 %** | — |
| 3 | Unit tests of core business logic (normal, boundary, invalid, failure; FIRST) | PASS | 73 tests in 11 classes (`@Tag("unit")`) | `.\mvnw.cmd test -Dgroups=unit -Djacoco.skip=true` | 73 passed, no Spring/DB | — |
| 4a | Integration: REST endpoints | PASS | 45 tests in `com.veya.api` (real HTTP, schema-validated) | `verify` | all pass | — |
| 4b | Integration: PostgreSQL | PASS | 136 tests in `com.veya.db` + all API tests (Testcontainers PostgreSQL 17, real roles, no H2) | `verify` | all pass | — |
| 4c | Integration: authentication and authorization | PASS | `IdentityApiTests` (401 variants), 403/404 in all module tests, `RolePrivilegeTests` (DB roles) | `verify` | all pass | — |
| 4d | Integration: application configuration | PASS | `VeyaApplicationTests`, `HealthEndpointTests`, `SystemApiTests` (CORS, api-docs), `IdentityUnitTest` (secret validation) | `verify` | all pass | — |
| 4e | Integration: transaction consistency | PASS | `RecoveryApiTests.acceptIsAtomic`, `concurrentDecisions`, `RecoveryDecisionTests`, rejected requests leave no rows | `verify` | rollback and serialization verified | — |
| 5 | UI tests (if a frontend exists) | NOT APPLICABLE | `frontend/` contains only a README — no UI exists in this repository | — | — | add with the React client |
| 6 | All automated tests in GitHub Actions | PASS | `.github/workflows/backend.yml` jobs `contract`, `build`, `image` | [CI run 45391042348](https://github.com/Atikin96/veya/actions/runs/45391042348) (commit `97c2ae3`) | all 3 jobs green; artifact `test-reports`: 264 tests, 0 failures/errors/skipped | — |
| 7 | Testing strategy documentation | PASS | [`README.md`](README.md) §1–§8 | — | — | — |
| 8 | Consistent naming and organization | PASS | unit `*Test` + `@Tag("unit")`, integration `*Tests`; `@DisplayName` with story ids | — | — | — |

## Maximum requirements

| Requirement | Status | Evidence / result |
|---|---|---|
| Thorough unit, integration and end-to-end testing | PASS | 73 / 191 / smoke E2E (35 HTTP checks + DB row checks against containers) |
| Authorization and input-validation coverage | PASS | 401/403/404 for every owned resource type; boundaries and invalid formats |
| Fixtures and mocks for external providers | PASS | `Fixtures`, `PlanFixtures`; Mockito; `StubFoodProvider` (OK/500/timeout) |
| Database migration tests | PASS | `CleanEnvironmentSeedTests` (fresh container: roles → V1–V10 → seed), `VeyaApplicationTests.flywayAppliedAllMigrationsAsOwner` |
| Concurrency tests | PASS | `RecoveryApiTests.concurrentDecisions` (4 parallel ACCEPTs → exactly one 200, one new version) |
| OpenAPI contract tests | PASS | schema validation of every API response; `OpenApiContractTests`; `check_contract.py` in CI |
| Property-based / mutation testing | NOT IMPLEMENTED | replaced by parameterized boundary tests; PIT/jqwik not added within the deadline |
| CI quality gates | PASS | coverage gate, `-Xlint -Werror`, contract check, OSV check, docs diff |
| Coverage artifacts | PASS | [CI run](https://github.com/Atikin96/veya/actions/runs/45391042348): `jacoco-coverage-report` (HTML/XML/CSV: 95.0 % lines, 82.8 % branches) and `test-reports` |
| Static analysis | PARTIAL | javac lint as errors; no SpotBugs/Sonar |
| Realistic performance tests | NOT IMPLEMENTED | NFR-01/02 not measured under load; the engine is bounded (≤ 100 candidates, ≤ 50 000 combinations) |
| Defects found by tests explained | PASS | [`README.md` §7](README.md#7-defects-found-by-the-tests-and-fixed) — 8 real defects with fixes |
