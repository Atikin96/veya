# VEYA API — frontend handoff (for Hanna)

Status: **2026-11-22, contract draft 0.1.0.** Backend/DB: Mikita Malafei. Frontend, TypeScript interfaces and the
frontend API client documentation: Hanna Ivanova.

This document explains what the backend contract looks like, what exists today, and what you can build and
document now. The **source of truth is [`openapi.yaml`](openapi.yaml)**; [`API_ENDPOINTS.md`](API_ENDPOINTS.md) and
the Postman files are generated from it, so all three always agree.

---

## 1. What exists today and what is planned

| Group | Count | Can it be called now? |
|---|---|---|
| **IMPLEMENTED** — health (3), `/v3/api-docs`, reference data, identity (register, login, me, delete, export, consents), profile, daily constraints, nutrition diary / saved meals / totals / meals, exercises and workout records | 31 | **Yes** - verified over real HTTP by the backend API tests (`backend/src/test/java/com/veya/api`), responses validated against `openapi.yaml` |
| **PARTIAL** — `GET /api/v1/foods` | 1 | **Yes**, local catalogue only: no external provider yet, `providerStatus` is always `UNAVAILABLE`, no 502/504 (AQ-7) |
| **PLANNED** — plans, meal-plan suggestions, replacement options, item status, deviations, recovery proposals/decisions, day summary | 13 | **No** - no controller yet; requests return 401 without a token, 404 with one |
| **BLOCKED variant** — `EDIT` decision of a recovery proposal | 1 variant | No - meaning of EDIT is undecided (BA question Q12) |
| **BLOCKED function** — adding items to an already saved plan | no endpoint | Not specified by the BA (AQ-3) |

Already real and tested: the **PostgreSQL database** behind these endpoints (schema, versioned plans, recovery
rules, roles). The API contract follows it, so field names and rules are stable even though the REST layer is
still to be written.

Every endpoint says whether its path/status **comes from the BA** (Table 2.8: `meal-options`, `recovery-proposals`,
`decision`, `days/{date}/summary`) or is **PROPOSED** and still open for our agreement.

## 2. Endpoint matrix (frontend view)

Full details, parameters, schemas and examples: [`API_ENDPOINTS.md`](API_ENDPOINTS.md). Suggested client method
names = OpenAPI `operationId`.

| Screen / flow | Client method (`operationId`) | Endpoint | Auth |
|---|---|---|---|
| Sign-up | `listConsentDocuments` | `GET /api/v1/consent-documents` | public |
| Sign-up | `register` | `POST /api/v1/auth/register` | public |
| Sign-in | `login` | `POST /api/v1/auth/login` | public |
| App start / account | `getMe` | `GET /api/v1/me` | JWT |
| Forms (codes) | `getReferenceData` | `GET /api/v1/reference-data` | JWT |
| Profile & goal | `getMyProfile`, `saveMyProfile` | `GET`/`PUT /api/v1/me/profile` | JWT |
| Day constraints | `getDailyConstraints`, `saveDailyConstraints` | `GET`/`PUT /api/v1/constraints/{date}` | JWT |
| Food search | `searchFoods` | `GET /api/v1/foods?q=&limit=` | JWT |
| Diary | `listFoodEntries`, `createFoodEntry`, `updateFoodEntry`, `deleteFoodEntry` | `/api/v1/food-entries[/{entryId}]` | JWT |
| Quick re-log | `listRecentFoodEntries`, `listSavedMeals`, `createSavedMeal`, `logSavedMeal` | `/api/v1/food-entries/recent`, `/api/v1/saved-meals[...]` | JWT |
| Nutrition totals | `getNutritionSummary` | `GET /api/v1/days/{date}/nutrition` | JWT |
| Plan creation | `suggestMealPlan`, `listMeals`, `listExercises`, `createPlan` | `POST /meal-plan-suggestions`, `GET /meals`, `GET /exercises`, `POST /plans` | JWT |
| Plan view | `getPlanByDate`, `getPlan` | `GET /api/v1/plans?date=`, `GET /api/v1/plans/{planId}` | JWT |
| Replace meal (Fig. B.9) | `getMealOptions`, `replacePlanItem` | `POST /api/v1/meal-options`, `POST /plans/{planId}/items/{itemKey}/replacement` | JWT |
| Replace exercise | `getExerciseOptions`, `replacePlanItem` | `POST /api/v1/exercise-options`, same replacement | JWT |
| Confirm / skip items | `recordPlanItemStatus`, `createActivityEntry`, `listActivityEntries` | `POST /plans/{planId}/items/{itemKey}/status`, `/api/v1/activity-entries` | JWT |
| Recovery (Fig. B.10) | `listOpenDeviations`, `createRecoveryProposal`, `getRecoveryProposal`, `decideRecoveryProposal` | `/plans/{planId}/deviations`, `/plans/{planId}/recovery-proposals`, `/recovery-proposals/{id}[/decision]` | JWT |
| Dashboard (Fig. B.8) | `getDaySummary` | `GET /api/v1/days/{date}/summary` | JWT |
| Privacy | `listMyConsents`, `grantConsent`, `withdrawConsent`, `exportMyData`, `deleteMyAccount` | `/api/v1/me/consents[...]`, `GET /api/v1/me/export`, `DELETE /api/v1/me?confirm=true` | JWT |
| System | `getHealth` (+ liveness/readiness) | `GET /actuator/health` | public |

All business paths start with `/api/v1`; the base URL locally is `http://localhost:8080`.

## 3. Authentication flow

1. **Sign-up:** load `GET /api/v1/consent-documents`, show the texts, then `POST /api/v1/auth/register` with
   `adultConfirmed: true` (explicit "I am at least 18" checkbox - agreed Q13) and the accepted `consentDocumentIds`.
   Registration does **not** sign in (PROPOSED).
2. **Sign-in:** `POST /api/v1/auth/login` -> `{ accessToken, tokenType: "Bearer", expiresIn }`.
3. **Every protected call:** header `Authorization: Bearer <accessToken>`.
4. **401** on any protected call (missing/expired token, deleted account) -> clear the token and go to sign-in.
   Invalid credentials at sign-in also return 401 with the same message whether the account exists or not.
5. **Sign-out:** discard the token on the client (no server logout in the MVP - PROPOSED, AQ-5).
6. **Account deletion:** after the confirmation dialog, `DELETE /api/v1/me?confirm=true` -> 204, then clear the token.

Open for us to agree (AQ-5): token lifetime (PROPOSED 60 min), no refresh token, and **where the client keeps the
token** (in memory vs. `sessionStorage`/`localStorage` - security trade-off, your decision).

## 4. Standard request and response structures

* JSON with **camelCase** fields; `Content-Type: application/json`.
* Dates `YYYY-MM-DD` = the user's **local plan date**; times `HH:mm`; instants (`createdAt`, `decidedAt`) ISO-8601 UTC.
* Ids: UUID strings for user data (`planId`, `entryId`, `proposalId`, ...), integers for catalogue items
  (`foodId`, `mealId`, `exerciseId`, `consentDocumentId`), codes for vocabularies (`LUNCH`, `PEANUT`, `DUMBBELLS`).
* Nullable fields are always present with `null` (no missing keys) - e.g. a WORKOUT item has `meal: null`.
* **Plans are versioned.** `Plan.activeVersion` is what you send back as `baseVersion` when changing the plan or
  deciding a proposal. Plan items are identified by `itemKey`, which stays the same across versions.
* Plan items come sorted by `plannedTime`.
* Read-only "suggestion" calls (`meal-plan-suggestions`, `meal-options`, `exercise-options`) never change data; only
  `POST /plans`, `.../replacement` and `.../decision` change a plan, always after an explicit user action.
* Lists are bounded (`limit`) or per date; there is no cursor pagination in the MVP.

## 5. TypeScript types from OpenAPI

The spec is **OpenAPI 3.1** and validates (see §10). Two practical options:

* **Generate** types, e.g. with `openapi-typescript` 7.x (supports OpenAPI 3.1):
  `npx openapi-typescript docs/api/openapi.yaml -o <your-path>/veya-api.d.ts`, then use
  `components["schemas"]["Plan"]` etc. (with or without `openapi-fetch`).
* **Write by hand** from the schemas in `openapi.yaml` (`components.schemas`).

Mapping rules: `type: [string, 'null']` -> `string | null`; `enum` -> string union; `format: uuid/date/date-time` ->
`string`; `integer`/`number` -> `number`; required fields are non-optional.

Illustrative shapes (derived from the spec - **not production code**):

```ts
type MealSlot = "BREAKFAST" | "LUNCH" | "DINNER" | "SNACK";
type ItemStatus = "PLANNED" | "COMPLETED" | "SKIPPED" | "MISSED" | "CHANGED";

interface PlanItem {
  itemKey: string;                       // stable across versions
  itemType: "MEAL" | "WORKOUT";
  mealSlot: MealSlot | null;
  plannedTime: string | null;            // "HH:mm"
  meal: { mealId: number; name: string } | null;
  exercise: { exerciseId: number; name: string } | null;
  plannedKcal: number | null;
  plannedProteinG: number | null;
  plannedFatG: number | null;
  plannedCarbsG: number | null;
  durationMin: number | null;
  status: ItemStatus;
}

interface Plan {
  planId: string;
  date: string;                          // "YYYY-MM-DD"
  activeVersion: number;                 // send as baseVersion
  changeReason: "INITIAL_PLAN" | "MEAL_REPLACEMENT" | "EXERCISE_REPLACEMENT" | "RECOVERY_ACCEPTED" | "RECOVERY_EDITED";
  versionCreatedAt: string;
  recoveryRequired: boolean;
  items: PlanItem[];
}

interface Problem {                      // RFC 9457, Content-Type: application/problem+json
  type: string; title: string; status: number; code: string;
  detail?: string; instance?: string;
  errors?: { field: string; message: string; rejectedValue?: unknown }[];
  blockingConstraints?: { type: "TIME" | "EQUIPMENT" | "INGREDIENT" | "FOOD_EXCLUSION" | "NUTRITION_TARGET"; message: string; itemKey: string | null }[];
  currentVersion?: number;               // with STALE_PLAN_VERSION
  manualEntryAllowed?: boolean;          // provider errors
}
```

## 6. Error handling conventions

Every error is a Problem Details object with a stable `code`; the complete list is in
[API_ENDPOINTS.md → Error codes](API_ENDPOINTS.md#error-codes). Suggested UI behaviour (BA US-24, NFR-13: show
progress, safe retry, never lose entered data):

| Status / code | Client behaviour |
|---|---|
| 400 `VALIDATION_FAILED` | Map `errors[].field` to form fields; keep the form content. |
| 401 | Clear token, redirect to sign-in (after sign-in, return to the same screen if possible). |
| 403 `ACCESS_DENIED` | Generic "not available" message; never show other users' data. |
| 404 | "Not found" state; `PROFILE_NOT_FOUND` -> start profile setup; `PLAN_NOT_FOUND` -> "create plan" state. |
| 409 `STALE_PLAN_VERSION` | The plan changed meanwhile: reload the plan (`currentVersion` is given), then ask the user to retry / request a new proposal (UC-01 alternative B). |
| 409 `PROPOSAL_ALREADY_DECIDED`, `STATUS_ALREADY_RECORDED`, ... | Reload the affected data and show its current state. |
| 422 `RECOVERY_NOT_FEASIBLE`, `NO_FEASIBLE_ALTERNATIVE` | Show `blockingConstraints[].message` and offer to edit constraints (US-19, UC-01 alt. A); the plan is unchanged. |
| 502/504 `PROVIDER_*` | Show the message, offer **Retry** and **manual entry** when `manualEntryAllowed` (US-05, US-31). |
| 500 / network error / timeout > 5 s | Error with Retry; keep entered data (NFR-13). |

## 7. How client methods map to endpoints - recovery flow example

Illustrative only (not production code) - shows the order of calls and where `baseVersion` comes from:

```ts
// Hypothetical thin client: request<T>(method, path, body?) adds the bearer token and throws Problem on non-2xx.
async function recoverDay(planId: string) {
  const plan = await request<Plan>("GET", `/api/v1/plans/${planId}`);
  if (!plan.recoveryRequired) return plan;

  const reasons = await request<Deviation[]>("GET", `/api/v1/plans/${planId}/deviations`);   // "Reason for review"
  try {
    const proposal = await request<RecoveryProposal>(                                         // 201 Created
      "POST", `/api/v1/plans/${planId}/recovery-proposals`, { baseVersion: plan.activeVersion });
    // show proposal.changes[i].before / after / reason, then the user decides explicitly:
    const result = await request<DecisionResult>(
      "POST", `/api/v1/recovery-proposals/${proposal.proposalId}/decision`,
      { decision: "ACCEPT", baseVersion: proposal.baseVersion });                             // or "REJECT"
    return result.plan;                                                                        // new version
  } catch (e) {
    const p = e as Problem;
    if (p.code === "RECOVERY_NOT_FEASIBLE") { /* show p.blockingConstraints, offer constraint edit */ }
    if (p.code === "STALE_PLAN_VERSION") { /* reload plan, offer a new proposal */ }
    throw e;
  }
}
```

Diary + deviation: `createFoodEntry` (with `planItemKey` of the planned meal) -> `recordPlanItemStatus`
(`COMPLETED`) -> the response says `recoveryRequired` and lists `newDeviations` (>= 200 kcal difference, BA US-15).

## 8. Where to find everything

| What | File |
|---|---|
| OpenAPI 3.1 specification (source of truth) | [`docs/api/openapi.yaml`](openapi.yaml) |
| Endpoint catalogue with examples, traceability, open questions, error codes | [`docs/api/API_ENDPOINTS.md`](API_ENDPOINTS.md) |
| Postman Collection v2.1 (91 requests incl. UC-01 scenario and negative cases) | [`docs/api/postman/VEYA_API.postman_collection.json`](postman/VEYA_API.postman_collection.json) |
| Postman environment (no secrets) | [`docs/api/postman/VEYA_Local.postman_environment.json`](postman/VEYA_Local.postman_environment.json) |
| Generator and checks | [`docs/api/tools/`](tools) (`build_docs.py`, `check_contract.py`) |
| BA decisions | [`docs/ba-open-questions.md`](../ba-open-questions.md) |
| Database (what the API is built on) | [`docs/database/README.md`](../database/README.md) |

**Postman:** import both files; set `demoPassword` locally (never commit it); run "Sign in" to fill `accessToken`.
Request names are prefixed **[IMPLEMENTED]**, **[PLANNED]** or **[BLOCKED Q12]**. Saved responses are
**documented examples from OpenAPI, not recorded runs**. Only the three health requests can pass today.

## 9. Decisions we need to agree on

Full list with proposals: [API_ENDPOINTS.md → Open questions](API_ENDPOINTS.md#open-questions). Most relevant for
the frontend:

1. **Q12 / AQ-1 - EDIT of a recovery proposal.** Choose only some changes, or also edit values per item (Fig. B.10
   shows an "Edit" link per change)? Until decided, implement ACCEPT and REJECT only.
2. **AQ-2 - 403 or 404 for another user's resource.** The BA (US-30, NFR-08) requires 401/403; the contract uses
   403 `ACCESS_DENIED` for another user's existing resource and 404 for an unknown id. The database reports both
   cases as "not found" (`VY404`), so this still needs backend confirmation - handle both 403 and 404 in the UI.
3. **AQ-5 - token lifetime, refresh and where the token is stored.**
4. **AQ-4 - which consents are mandatory at sign-up** and what the UI shows after a consent is withdrawn.
5. **AQ-6 - duplicate e-mail at sign-up:** explicit 409 message or neutral response.
6. **AQ-9 - empty states:** day without plan (`plan: null`), constraints not set, profile not created (404).
7. **AQ-7 - food search when the provider fails** (`providerStatus: UNAVAILABLE` vs. 502/504).
8. **AQ-12 - plan creation in two steps** (suggestion, then explicit save).
9. **AQ-3 - adding a workout to an already saved plan** (not defined by the BA).
10. **AQ-8 - naming** of all PROPOSED paths and fields - tell me what does not fit your screens before I implement.

## 10. What can be done now vs. after the REST implementation

| Now (contract-level) | After the backend implements the endpoints |
|---|---|
| TypeScript interfaces from `openapi.yaml` | Real integration tests of the client |
| API client methods (one per `operationId`) and their documentation | Postman runs with real responses |
| UI states for every documented status/error code | End-to-end tests of UC-01 (NFR-16) |
| Mocks from the documented examples (e.g. MSW, or an OpenAPI mock server such as Prism - not verified by us) | Performance checks (NFR-01/02/03) |
| Calling every IMPLEMENTED/PARTIAL operation against the running backend | Everything marked PLANNED |

Contract checks already run (2026-11-22): `openapi.yaml` validated with `openapi-spec-validator` 0.7.1 (OpenAPI 3.1);
every example validated against its schema; negative examples are rejected by the schema; the Postman Collection
validated against the official Postman v2.1 JSON schema; generated files match the spec
(`python docs/api/tools/check_contract.py` -> `RESULT: PASSED`). Backend day 1 (2026-11-22): the IMPLEMENTED
operations were exercised over real HTTP by 31 API integration tests and a manual smoke run (register -> login ->
profile -> constraints -> food search -> diary entry -> daily totals -> 401/403 checks); PLANNED operations have not
been executed because they do not exist yet.
