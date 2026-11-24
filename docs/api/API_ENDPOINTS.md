# VEYA REST API — endpoint catalogue (MVP contract draft)

> **Generated from [`openapi.yaml`](openapi.yaml) by `tools/build_docs.py` — do not edit by hand.**
> Status 2026-11-22: **IMPLEMENTED** operations are served by the running backend and verified over real HTTP by
> the API integration tests (`backend/src/test/java/com/veya/api`, responses validated against this contract).
> **PARTIAL** operations work with the limitation stated in their notes. **PLANNED** operations are documented only;
> no controller exists yet, so those requests cannot succeed against the running backend.

Operations: **45** — IMPLEMENTED 44, PLANNED 0, PARTIAL 1, BLOCKED 0 (fully blocked operations). One operation variant is blocked: **EDIT** of a recovery proposal (Q12). One function without endpoint is blocked: adding items to an existing plan (AQ-3).

Sources: BA `docs/BA_Malafei_Mikita.docx` (Table 2.8 API contract, Appendix A stories, UC-01/UC-02, Fig. B.4/B.5/B.10,
NFR-01..18, LEG-1..9), agreed decisions [`docs/ba-open-questions.md`](../ba-open-questions.md), database
[`docs/database/`](../database/README.md). **Contract source** of every operation says whether the path/status
comes from the BA or is **PROPOSED** (needs agreement, see [Open questions](#open-questions)).

## Conventions (PROPOSED unless stated)

* Base URL `http://localhost:8080`; business endpoints under `/api/v1` (BA Table 2.8).
* JSON, camelCase; dates `YYYY-MM-DD` (user's local plan date); times `HH:mm`; instants ISO-8601 UTC.
* Authentication: `Authorization: Bearer <JWT>` from `POST /api/v1/auth/login`; public operations are marked.
* Authorization: a user only sees and changes own data. `/me` endpoints never take a user id. Ids of another
  user's resources -> **403 `ACCESS_DENIED`** (US-30, NFR-08); unknown ids -> 404 (AQ-2).
* Errors: RFC 9457 Problem Details, `Content-Type: application/problem+json`, stable `code` (NFR-14) - see
  [Error codes](#error-codes).
* Plan changes need an explicit user action: suggestions and options are read-only; only `POST /plans`,
  `.../replacement` and `.../decision` change a plan, each creating a new immutable version (Q2).
* Optimistic locking: every plan change sends `baseVersion`; a stale value returns **409
  `STALE_PLAN_VERSION`** and nothing changes (NFR-10).
* Pagination: lists are bounded (`limit`) or limited to one date; no cursor pagination in the MVP.
* Idempotency: GET/PUT/DELETE are idempotent; POSTs are not (repeats return documented 409s). No
  `Idempotency-Key` header in the MVP.

## Endpoint matrix

| # | Module | Method and path | Operation | Stories | Auth | Status | Contract source |
|---|---|---|---|---|---|---|---|
| 1 | system | [`GET /actuator/health`](#get-actuatorhealth) | `getHealth` | US-32 | public | IMPLEMENTED | Actuator |
| 2 | system | [`GET /actuator/health/liveness`](#get-actuatorhealthliveness) | `getLiveness` | US-32 | public | IMPLEMENTED | Actuator |
| 3 | system | [`GET /actuator/health/readiness`](#get-actuatorhealthreadiness) | `getReadiness` | US-32 | public | IMPLEMENTED | Actuator |
| 4 | system | [`GET /v3/api-docs`](#get-v3api-docs) | `getApiDocs` | US-29 | public | IMPLEMENTED | PROPOSED |
| 5 | system | [`GET /api/v1/reference-data`](#get-apiv1reference-data) | `getReferenceData` | US-02, US-03, US-06 | JWT | IMPLEMENTED | PROPOSED |
| 6 | identity | [`POST /api/v1/auth/register`](#post-apiv1authregister) | `register` | US-01 | public | IMPLEMENTED | PROPOSED |
| 7 | identity | [`POST /api/v1/auth/login`](#post-apiv1authlogin) | `login` | US-01, US-30 | public | IMPLEMENTED | PROPOSED |
| 8 | identity | [`GET /api/v1/me`](#get-apiv1me) | `getMe` | US-01, US-30 | JWT | IMPLEMENTED | PROPOSED |
| 9 | identity | [`DELETE /api/v1/me`](#delete-apiv1me) | `deleteMyAccount` | US-04 | JWT | IMPLEMENTED | PROPOSED |
| 10 | identity | [`GET /api/v1/me/export`](#get-apiv1meexport) | `exportMyData` | US-04 | JWT | IMPLEMENTED | PROPOSED |
| 11 | identity | [`GET /api/v1/consent-documents`](#get-apiv1consent-documents) | `listConsentDocuments` | US-01 | public | IMPLEMENTED | PROPOSED |
| 12 | identity | [`GET /api/v1/me/consents`](#get-apiv1meconsents) | `listMyConsents` | US-01 | JWT | IMPLEMENTED | PROPOSED |
| 13 | identity | [`POST /api/v1/me/consents`](#post-apiv1meconsents) | `grantConsent` | US-01 | JWT | IMPLEMENTED | PROPOSED |
| 14 | identity | [`POST /api/v1/me/consents/{consentId}/withdrawal`](#post-apiv1meconsentsconsentidwithdrawal) | `withdrawConsent` | US-01 | JWT | IMPLEMENTED | PROPOSED |
| 15 | profile | [`GET /api/v1/me/profile`](#get-apiv1meprofile) | `getMyProfile` | US-02 | JWT | IMPLEMENTED | PROPOSED |
| 16 | profile | [`PUT /api/v1/me/profile`](#put-apiv1meprofile) | `saveMyProfile` | US-02, US-03 | JWT | IMPLEMENTED | PROPOSED |
| 17 | profile | [`GET /api/v1/constraints/{date}`](#get-apiv1constraintsdate) | `getDailyConstraints` | US-03 | JWT | IMPLEMENTED | PROPOSED |
| 18 | profile | [`PUT /api/v1/constraints/{date}`](#put-apiv1constraintsdate) | `saveDailyConstraints` | US-03, US-15 | JWT | IMPLEMENTED | PROPOSED |
| 19 | nutrition | [`GET /api/v1/foods`](#get-apiv1foods) | `searchFoods` | US-05, US-31 | JWT | IMPLEMENTED | PROPOSED |
| 20 | nutrition | [`GET /api/v1/meals`](#get-apiv1meals) | `listMeals` | US-09 | JWT | IMPLEMENTED | PROPOSED |
| 21 | nutrition | [`GET /api/v1/food-entries`](#get-apiv1food-entries) | `listFoodEntries` | US-06, US-07 | JWT | IMPLEMENTED | PROPOSED |
| 22 | nutrition | [`POST /api/v1/food-entries`](#post-apiv1food-entries) | `createFoodEntry` | US-06, US-15 | JWT | IMPLEMENTED | PROPOSED |
| 23 | nutrition | [`PATCH /api/v1/food-entries/{entryId}`](#patch-apiv1food-entriesentryid) | `updateFoodEntry` | US-06, US-15 | JWT | IMPLEMENTED | PROPOSED |
| 24 | nutrition | [`DELETE /api/v1/food-entries/{entryId}`](#delete-apiv1food-entriesentryid) | `deleteFoodEntry` | US-06 | JWT | IMPLEMENTED | PROPOSED |
| 25 | nutrition | [`GET /api/v1/food-entries/recent`](#get-apiv1food-entriesrecent) | `listRecentFoodEntries` | US-08 | JWT | IMPLEMENTED | PROPOSED |
| 26 | nutrition | [`GET /api/v1/saved-meals`](#get-apiv1saved-meals) | `listSavedMeals` | US-08 | JWT | IMPLEMENTED | PROPOSED |
| 27 | nutrition | [`POST /api/v1/saved-meals`](#post-apiv1saved-meals) | `createSavedMeal` | US-08 | JWT | IMPLEMENTED | PROPOSED |
| 28 | nutrition | [`POST /api/v1/saved-meals/{savedMealId}/entries`](#post-apiv1saved-mealssavedmealidentries) | `logSavedMeal` | US-08 | JWT | IMPLEMENTED | PROPOSED |
| 29 | nutrition | [`GET /api/v1/days/{date}/nutrition`](#get-apiv1daysdatenutrition) | `getNutritionSummary` | US-07 | JWT | IMPLEMENTED | PROPOSED |
| 30 | planning | [`POST /api/v1/meal-plan-suggestions`](#post-apiv1meal-plan-suggestions) | `suggestMealPlan` | US-09 | JWT | IMPLEMENTED | PROPOSED |
| 31 | planning | [`GET /api/v1/plans`](#get-apiv1plans) | `getPlanByDate` | US-09, US-12, US-20 | JWT | IMPLEMENTED | PROPOSED |
| 32 | planning | [`POST /api/v1/plans`](#post-apiv1plans) | `createPlan` | US-09, US-12 | JWT | IMPLEMENTED | PROPOSED |
| 33 | planning | [`GET /api/v1/plans/{planId}`](#get-apiv1plansplanid) | `getPlan` | US-09, US-12, US-18, US-20 | JWT | IMPLEMENTED | PROPOSED |
| 34 | planning | [`POST /api/v1/meal-options`](#post-apiv1meal-options) | `getMealOptions` | US-10 | JWT | IMPLEMENTED | BA |
| 35 | planning | [`POST /api/v1/exercise-options`](#post-apiv1exercise-options) | `getExerciseOptions` | US-14 | JWT | IMPLEMENTED | PROPOSED |
| 36 | planning | [`POST /api/v1/plans/{planId}/items/{itemKey}/replacement`](#post-apiv1plansplaniditemsitemkeyreplacement) | `replacePlanItem` | US-10, US-14 | JWT | IMPLEMENTED | PROPOSED |
| 37 | planning | [`POST /api/v1/plans/{planId}/items/{itemKey}/status`](#post-apiv1plansplaniditemsitemkeystatus) | `recordPlanItemStatus` | US-13, US-15 | JWT | IMPLEMENTED | PROPOSED |
| 38 | planning | [`GET /api/v1/plans/{planId}/deviations`](#get-apiv1plansplaniddeviations) | `listOpenDeviations` | US-15 | JWT | IMPLEMENTED | PROPOSED |
| 39 | activity | [`GET /api/v1/exercises`](#get-apiv1exercises) | `listExercises` | US-12 | JWT | IMPLEMENTED | PROPOSED |
| 40 | activity | [`GET /api/v1/activity-entries`](#get-apiv1activity-entries) | `listActivityEntries` | US-13, US-20 | JWT | IMPLEMENTED | PROPOSED |
| 41 | activity | [`POST /api/v1/activity-entries`](#post-apiv1activity-entries) | `createActivityEntry` | US-13, US-15 | JWT | IMPLEMENTED | PROPOSED |
| 42 | recovery | [`POST /api/v1/plans/{planId}/recovery-proposals`](#post-apiv1plansplanidrecovery-proposals) | `createRecoveryProposal` | US-16, US-17, US-19 | JWT | IMPLEMENTED | BA |
| 43 | recovery | [`GET /api/v1/recovery-proposals/{proposalId}`](#get-apiv1recovery-proposalsproposalid) | `getRecoveryProposal` | US-16, US-17, US-18 | JWT | IMPLEMENTED | PROPOSED |
| 44 | recovery | [`POST /api/v1/recovery-proposals/{proposalId}/decision`](#post-apiv1recovery-proposalsproposaliddecision) | `decideRecoveryProposal` | US-18 | JWT | PARTIAL (EDIT BLOCKED) | BA |
| 45 | history | [`GET /api/v1/days/{date}/summary`](#get-apiv1daysdatesummary) | `getDaySummary` | US-20, US-07 | JWT | IMPLEMENTED | BA |

## Traceability - 27 Must user stories

| Story | Title | Operations |
|---|---|---|
| US-01 | Register and sign in | `POST /api/v1/auth/register`<br>`POST /api/v1/auth/login`<br>`GET /api/v1/me`<br>`GET /api/v1/consent-documents`<br>`GET /api/v1/me/consents`<br>`POST /api/v1/me/consents`<br>`POST /api/v1/me/consents/{consentId}/withdrawal` |
| US-02 | Profile and goal | `GET /api/v1/reference-data`<br>`GET /api/v1/me/profile`<br>`PUT /api/v1/me/profile` |
| US-03 | Exclusions, equipment, available time | `GET /api/v1/reference-data`<br>`PUT /api/v1/me/profile`<br>`GET /api/v1/constraints/{date}`<br>`PUT /api/v1/constraints/{date}` |
| US-04 | Delete account and records | `DELETE /api/v1/me`<br>`GET /api/v1/me/export` |
| US-05 | Search food catalogue | `GET /api/v1/foods` |
| US-06 | Log and correct food | `GET /api/v1/reference-data`<br>`GET /api/v1/food-entries`<br>`POST /api/v1/food-entries`<br>`PATCH /api/v1/food-entries/{entryId}`<br>`DELETE /api/v1/food-entries/{entryId}` |
| US-07 | Consumed / target / remaining nutrition | `GET /api/v1/food-entries`<br>`GET /api/v1/days/{date}/nutrition`<br>`GET /api/v1/days/{date}/summary` |
| US-08 | Reuse saved or recent meals | `GET /api/v1/food-entries/recent`<br>`GET /api/v1/saved-meals`<br>`POST /api/v1/saved-meals`<br>`POST /api/v1/saved-meals/{savedMealId}/entries` |
| US-09 | Daily meal plan | `GET /api/v1/meals`<br>`POST /api/v1/meal-plan-suggestions`<br>`GET /api/v1/plans`<br>`POST /api/v1/plans`<br>`GET /api/v1/plans/{planId}` |
| US-10 | Replace a planned meal | `POST /api/v1/meal-options`<br>`POST /api/v1/plans/{planId}/items/{itemKey}/replacement` |
| US-12 | Schedule a workout | `GET /api/v1/plans`<br>`POST /api/v1/plans`<br>`GET /api/v1/plans/{planId}`<br>`GET /api/v1/exercises` |
| US-13 | Mark workouts completed/skipped/changed | `POST /api/v1/plans/{planId}/items/{itemKey}/status`<br>`GET /api/v1/activity-entries`<br>`POST /api/v1/activity-entries` |
| US-14 | Replace an exercise | `POST /api/v1/exercise-options`<br>`POST /api/v1/plans/{planId}/items/{itemKey}/replacement` |
| US-15 | Identify significant deviations | `PUT /api/v1/constraints/{date}`<br>`POST /api/v1/food-entries`<br>`PATCH /api/v1/food-entries/{entryId}`<br>`POST /api/v1/plans/{planId}/items/{itemKey}/status`<br>`GET /api/v1/plans/{planId}/deviations`<br>`POST /api/v1/activity-entries` |
| US-16 | Revised remaining-day plan | `POST /api/v1/plans/{planId}/recovery-proposals`<br>`GET /api/v1/recovery-proposals/{proposalId}` |
| US-17 | Reason per proposed change | `POST /api/v1/plans/{planId}/recovery-proposals`<br>`GET /api/v1/recovery-proposals/{proposalId}` |
| US-18 | Accept, edit or reject a proposal | `GET /api/v1/plans/{planId}`<br>`GET /api/v1/recovery-proposals/{proposalId}`<br>`POST /api/v1/recovery-proposals/{proposalId}/decision` |
| US-19 | Explanation if recovery is impossible | `POST /api/v1/plans/{planId}/recovery-proposals` |
| US-20 | Daily dashboard | `GET /api/v1/plans`<br>`GET /api/v1/plans/{planId}`<br>`GET /api/v1/activity-entries`<br>`GET /api/v1/days/{date}/summary` |
| US-23 | Responsive layout | Frontend only (responsive layout). |
| US-24 | Loading, empty and error states | Frontend; relies on the documented error format (Problem Details) and status codes. |
| US-26 | Keyboard and readable controls | Frontend only (accessibility). |
| US-29 | Documented API contract | `GET /v3/api-docs` |
| US-30 | Server-side authorization | `POST /api/v1/auth/login`<br>`GET /api/v1/me` |
| US-31 | Controlled provider failures | `GET /api/v1/foods` |
| US-32 | Reproducible startup | `GET /actuator/health`<br>`GET /actuator/health/liveness`<br>`GET /actuator/health/readiness` |
| US-33 | Automated checks in CI | CI pipeline, no endpoint. |

Cross-cutting: LEG-2 consents (Q7) and LEG-4 export (Q6) are covered by the identity operations;
LEG-3/US-30 ownership by every authenticated operation; LEG-5 (hashing, HTTPS, no secrets in logs) by the
backend implementation.

### Not in the MVP contract (Should/Could)

| Story | Priority | Title | Handling |
|---|---|---|---|
| US-11 | Should | Browse exercises by muscle, difficulty, equipment | Only basic `GET /api/v1/exercises` without US-11 filters. |
| US-21 | Should | Browse historical plans and decisions | No version/decision browsing endpoint (Q10). Only the active version number is returned. |
| US-22 | Could | Recurring deviation summary | Not included. |
| US-25 | Should | Help correcting forms | Frontend; field errors are already part of VALIDATION_FAILED. |
| US-27 | Should | Photo nutrition estimate | BA Table 2.8 lists `POST /api/v1/nutrition/photo-recognition` - excluded from the MVP contract. |
| US-28 | Should | Correct recognition results | Not included. |
| US-34 | Should | Favourite saved meals | Not included. |
| US-35 | Should | Filter activity history | Not included. |
| US-36 | Could | CSV export | Not included; LEG-4 JSON export (Q6) is `GET /api/v1/me/export`. |
| US-37 | Could | Calendar view | Not included. |
| US-38 | Could | Light/dark theme | Not included. |

## Open questions

| ID | Topic | Impact / proposal |
|---|---|---|
| AQ-1 (= BA Q12) | Meaning of EDIT in US-18 | **Blocks** the EDIT variant of `POST /api/v1/recovery-proposals/{proposalId}/decision`. The database supports applying a subset of changes (`selectedChangeIds`); BA Fig. B.10 shows an Edit action per proposed item (value editing). ACCEPT and REJECT are not affected. |
| AQ-2 | 403 or 404 for another user's resource | US-30/NFR-08 require 401/403. Contract: 403 `ACCESS_DENIED` for an existing resource of another user, 404 for a non-existing id. The database routines report "not found or not owned" as `VY404`, so the API must check ownership before calling them. Needs backend confirmation. |
| AQ-3 | Adding/removing items of an existing plan | **Blocked functionality (no endpoint).** The BA defines only initial planning (US-09/US-12), replacements (US-10/US-14) and recovery. Adding a workout to an already saved plan is not specified and the database has no version reason for it. Until decided, all items are sent when the plan is saved. |
| AQ-4 | Consents at registration | Which consent documents are mandatory at registration (PROPOSED: both current versions) and what happens after the health-data consent is withdrawn (LEG-2). |
| AQ-5 | Token lifetime, refresh, logout, storage | PROPOSED: access token 60 min, no refresh token, logout = discard the token in the client. Where the client stores the token (memory vs. storage) is a frontend decision with security impact. |
| AQ-6 | Duplicate e-mail at registration | PROPOSED 409 `EMAIL_ALREADY_REGISTERED` (usable UX, but reveals that an account exists). Alternative: neutral 201/202 with an e-mail message (needs e-mail infrastructure, not in the BA). |
| AQ-7 | Food provider fallback | PROPOSED: 200 with `providerStatus = UNAVAILABLE` when local results exist; 502/504 with `manualEntryAllowed` when nothing can be shown (US-05, US-31, R1). |
| AQ-8 | Proposed conventions | All paths not in BA Table 2.8, camelCase JSON, `/me` for the current user, `{date}` = local plan date, problem `type` URIs, error codes. |
| AQ-9 | Empty states | Day summary for a day without a plan (`plan: null`), constraints of a day without saved values (empty lists), profile not yet created (404 `PROFILE_NOT_FOUND`). |
| AQ-10 | Deleting a food entry | PROPOSED as part of "log and correct" (US-06); the BA does not mention deletion explicitly. |
| AQ-11 | Recovery requested when not required | Contract: 409 `RECOVERY_NOT_REQUIRED` (state conflict). The database raises `VY400` for this case; the API maps it. |
| AQ-12 | Meal plan: suggestion vs. save | PROPOSED two steps for US-09: `POST /api/v1/meal-plan-suggestions` (nothing saved) and `POST /api/v1/plans` (explicit save, version 1), so no plan is stored without the user's confirmation. |
| AQ-13 | Rule for "a constraint makes a future item infeasible" (US-15 rule 3) | IMPLEMENTED as PROPOSED, needs approval: an item still PLANNED in the active version is infeasible if a MEAL contains an ingredient excluded for the day, a MEAL needs more preparation minutes or a WORKOUT more minutes than `availableTimeMin`, or a WORKOUT needs equipment missing from `availableEquipment`. `availableIngredients` is stored but not used for infeasibility (the BA does not say whether a meal must consist only of available ingredients). |
| AQ-14 | Acceptable target range of a suggested menu (US-09) | IMPLEMENTED as PROPOSED, needs approval: a suggested menu is accepted only if its energy is within +/- `veya.planning.target-tolerance-pct` (default 20 %) of the profile target; otherwise 422 NO_FEASIBLE_MEAL_PLAN with a NUTRITION_TARGET blocking constraint. Macros are reported, not constrained. |

## Error codes

| HTTP | `code` | When |
|---|---|---|
| 400 | VALIDATION_FAILED | Invalid parameters/fields; `errors[]` lists the fields. Nothing changed. |
| 400 | MALFORMED_REQUEST | Body is not valid JSON. |
| 400 | CONFIRMATION_REQUIRED | Account deletion without `confirm=true`. |
| 400 | INVALID_EDIT | EDIT selection is empty, duplicated or not part of the proposal (US-18). |
| 401 | AUTHENTICATION_REQUIRED | Missing, invalid or expired token; deleted account. |
| 401 | INVALID_CREDENTIALS | Sign-in failed (same answer for unknown e-mail and wrong password). |
| 403 | ACCESS_DENIED | Resource of another user (US-30). |
| 404 | RESOURCE_NOT_FOUND / PROFILE_NOT_FOUND / PLAN_NOT_FOUND | Unknown id; no profile; no plan for the date. |
| 409 | STALE_PLAN_VERSION | `baseVersion` is not the active version (NFR-10); `currentVersion` is returned. |
| 409 | PROPOSAL_ALREADY_DECIDED | Second decision for the same proposal. |
| 409 | RECOVERY_NOT_REQUIRED | Recovery requested without an open significant deviation (AQ-11). |
| 409 | ITEM_FROZEN / STATUS_ALREADY_RECORDED / IMMUTABLE_HISTORY | Completed items and superseded versions cannot change. |
| 409 | PLAN_ALREADY_EXISTS / EMAIL_ALREADY_REGISTERED / SAVED_MEAL_NAME_TAKEN / CONSENT_ALREADY_ACTIVE / CONSENT_ALREADY_WITHDRAWN | Uniqueness or state conflicts. |
| 422 | RECOVERY_NOT_FEASIBLE | No feasible recovery combination; `blockingConstraints[]` (US-19). No new version. |
| 422 | NO_FEASIBLE_ALTERNATIVE / NO_FEASIBLE_MEAL_PLAN | No compliant meal/exercise alternative or meal plan; `blockingConstraints[]`. |
| 422 | HARD_CONSTRAINT_VIOLATION | A requested item breaks an exclusion, time or equipment constraint. |
| 502 | PROVIDER_UNAVAILABLE | External provider error without local results; `manualEntryAllowed` (US-31). |
| 504 | PROVIDER_TIMEOUT | External provider timeout without local results; `manualEntryAllowed` (US-05). |
| 500 | INTERNAL_ERROR | Unexpected error; no internal details are returned. |

Database error codes are translated by the API: `VY400` -> 400, `VY404` -> 403/404 (after the ownership
check, AQ-2), `VY409` -> 409 `STALE_PLAN_VERSION`/`PROPOSAL_ALREADY_DECIDED`, `VY001` -> 409 `IMMUTABLE_HISTORY`;
constraint violations (23xxx) -> 400/409. Database details (table names, SQL) are never returned.

## Shared / system

### `GET /actuator/health`

**Application health** — operationId `getHealth`

| | |
|---|---|
| Status | **IMPLEMENTED** |
| Module | `shared` |
| BA stories | US-32 |
| Acceptance criteria | US-32: given a clean machine with Docker, when Compose starts, PostgreSQL becomes healthy, migrations run and the API is ready. NFR-11 availability probe. |
| Contract source | Spring Boot Actuator (configured in application.yml) |
| Authentication | Public |
| Authorization / ownership | Public. |
| Pagination / idempotency / notes | Verified by HealthEndpointTests (actuator/health, liveness, readiness return 200 UP; env/beans/configprops are not exposed). |

Aggregated health of the API including the database connection. Details are never exposed.

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 200 | Application is up. | `Health` (`application/json`) |
| 503 | Application or database is down. | `Health` (`application/json`) |

Response 200

```json
{
  "groups": [
    "liveness",
    "readiness"
  ],
  "status": "UP"
}
```

Response 503

```json
{
  "status": "DOWN"
}
```

### `GET /actuator/health/liveness`

**Liveness probe** — operationId `getLiveness`

| | |
|---|---|
| Status | **IMPLEMENTED** |
| Module | `shared` |
| BA stories | US-32 |
| Acceptance criteria | US-32 / NFR-17: health checks before readiness. |
| Contract source | Spring Boot Actuator |
| Authentication | Public |
| Authorization / ownership | Public. |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 200 | Process is alive. | `Health` (`application/json`) |

Response 200

```json
{
  "status": "UP"
}
```

### `GET /actuator/health/readiness`

**Readiness probe** — operationId `getReadiness`

| | |
|---|---|
| Status | **IMPLEMENTED** |
| Module | `shared` |
| BA stories | US-32 |
| Acceptance criteria | US-32 / NFR-17: the API is ready only after migrations. |
| Contract source | Spring Boot Actuator |
| Authentication | Public |
| Authorization / ownership | Public. |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 200 | Ready to serve traffic. | `Health` (`application/json`) |

Response 200

```json
{
  "status": "UP"
}
```

### `GET /v3/api-docs`

**OpenAPI document of the running API** — operationId `getApiDocs`

| | |
|---|---|
| Status | **IMPLEMENTED** |
| Module | `shared` |
| BA stories | US-29 |
| Acceptance criteria | US-29: given a published build, when contract docs are generated, then every client-used method specifies path, auth, schemas, success and main error responses in OpenAPI. |
| Contract source | PROPOSED (springdoc default path; served from this file) |
| Authentication | Public |
| Authorization / ownership | Public in development; may be disabled in production (PROPOSED). |

This contract (`docs/api/openapi.yaml`, packaged into the backend at build time) served as JSON. Nothing is generated from the code; API tests check that the implemented controllers match it.

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 200 | OpenAPI document. | object (`application/json`) |

Response 200

```json
{
  "openapi": "3.1.0",
  "info": {
    "title": "VEYA REST API",
    "version": "0.1.0"
  }
}
```

### `GET /api/v1/reference-data`

**Reference vocabularies for forms** — operationId `getReferenceData`

| | |
|---|---|
| Status | **IMPLEMENTED** |
| Module | `shared` |
| BA stories | US-02, US-03, US-06 |
| Acceptance criteria | Supports US-02 (goal), US-03 (exclusions, equipment), US-06 (meal slot) forms with valid codes. |
| Contract source | PROPOSED |
| Authentication | JWT bearer token |
| Authorization / ownership | Any authenticated user; data is not personal. |
| Pagination / idempotency / notes | Small and stable; the client may cache it for the session. |

Goals, meal slots, equipment and ingredients used by profile, constraint, diary and plan forms.

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 200 | Reference vocabularies. | `ReferenceData` (`application/json`) |
| 401 | Missing, invalid or expired token, or the account no longer exists. | `Problem` (`application/problem+json`) |

Response 200

```json
{
  "goals": [
    {
      "code": "LOSE_WEIGHT",
      "name": "Lose weight"
    },
    {
      "code": "MAINTAIN_WEIGHT",
      "name": "Maintain weight"
    },
    {
      "code": "GAIN_WEIGHT",
      "name": "Gain weight / build muscle"
    }
  ],
  "mealSlots": [
    {
      "code": "BREAKFAST",
      "name": "Breakfast",
      "sortOrder": 1
    },
    {
      "code": "LUNCH",
      "name": "Lunch",
      "sortOrder": 2
    },
    {
      "code": "DINNER",
      "name": "Dinner",
      "sortOrder": 3
    },
    {
      "code": "SNACK",
      "name": "Snack",
      "sortOrder": 4
    }
  ],
  "equipment": [
    {
      "code": "DUMBBELLS",
      "name": "Dumbbells"
    },
    {
      "code": "YOGA_MAT",
      "name": "Yoga mat"
    }
  ],
  "ingredients": [
    {
      "code": "PEANUT",
      "name": "Peanut"
    },
    {
      "code": "FISH",
      "name": "Fish"
    },
    {
      "code": "MILK",
      "name": "Milk / dairy"
    }
  ]
}
```

Response 401

```json
{
  "type": "https://veya.example/problems/authentication-required",
  "title": "Authentication required",
  "status": 401,
  "code": "AUTHENTICATION_REQUIRED",
  "detail": "A valid bearer token is required.",
  "instance": "/api/v1/me"
}
```

## Identity, consent and data access (LEG-2, LEG-4)

### `POST /api/v1/auth/register`

**Register a new account** — operationId `register`

| | |
|---|---|
| Status | **IMPLEMENTED** |
| Module | `identity` |
| BA stories | US-01 |
| Acceptance criteria | US-01: given valid registration data, when submitted, then the account is created with a securely hashed password. |
| Contract source | PROPOSED |
| Authentication | Public |
| Authorization / ownership | Public. |
| Pagination / idempotency / notes | Not idempotent: a repeated request with the same e-mail returns 409. Password 8-72 characters (bcrypt limit) - PROPOSED. |
| Open questions | AQ-4 consents required at registration; AQ-6 duplicate e-mail disclosure |

Creates an account with a securely hashed password (bcrypt/Argon2id). Registration requires the explicit
"I am at least 18" confirmation (Q8, Q13) and acceptance of the consent documents listed by
`GET /api/v1/consent-documents` (LEG-2, Q7). Does not sign the user in.

**Request body** (required) — `RegisterRequest`

| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| `email` | string (email) | yes | max length `254` |  |
| `password` | string | yes | min length `8`; max length `72` | Plain password over HTTPS; stored only as a bcrypt/Argon2id hash. Length rule PROPOSED. |
| `adultConfirmed` | boolean = `True` | yes |  | Explicit "I am at least 18" confirmation (Q8, Q13). Stored as adultConfirmedAt. |
| `consentDocumentIds` | array of integer | yes | min items `1`; unique | Accepted documents from GET /api/v1/consent-documents. Which ones are mandatory is AQ-4 (PROPOSED - both current versions). |

Request example: Valid registration

```json
{
  "email": "anna.demo@example.org",
  "password": "<your-password>",
  "adultConfirmed": true,
  "consentDocumentIds": [
    1,
    2
  ]
}
```

Request example: Adult confirmation missing (400)

```json
{
  "email": "teen@example.org",
  "password": "<your-password>",
  "adultConfirmed": false,
  "consentDocumentIds": [
    1,
    2
  ]
}
```

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 201 | Account created. | `Account` (`application/json`) |
| 400 | Malformed JSON, invalid parameter or field values (nothing was changed). | `Problem` (`application/problem+json`) |
| 409 | E-mail already registered (PROPOSED - see AQ-6). | `Problem` (`application/problem+json`) |

Response 201

```json
{
  "userId": "a0000000-0000-4000-8000-000000000001",
  "email": "anna.demo@example.org",
  "adultConfirmedAt": "2026-11-22T09:00:00Z",
  "createdAt": "2026-11-22T09:00:00Z",
  "hasProfile": false
}
```

Response 400

```json
{
  "type": "https://veya.example/problems/validation-failed",
  "title": "Request is not valid",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "detail": "One or more fields are invalid.",
  "instance": "/api/v1/me/profile",
  "errors": [
    {
      "field": "energyTargetKcal",
      "message": "must be between 500 and 10000",
      "rejectedValue": 100
    }
  ]
}
```

Response 409

```json
{
  "type": "https://veya.example/problems/email-already-registered",
  "title": "E-mail already registered",
  "status": 409,
  "code": "EMAIL_ALREADY_REGISTERED",
  "detail": "An account with this e-mail already exists.",
  "instance": "/api/v1/auth/register"
}
```

### `POST /api/v1/auth/login`

**Sign in and obtain a JWT** — operationId `login`

| | |
|---|---|
| Status | **IMPLEMENTED** |
| Module | `identity` |
| BA stories | US-01, US-30 |
| Acceptance criteria | US-01: invalid credentials return 401 without disclosing the account state. |
| Contract source | PROPOSED |
| Authentication | Public |
| Authorization / ownership | Public. |
| Pagination / idempotency / notes | The same 401 response for an unknown e-mail and a wrong password. Token lifetime and refresh are open (AQ-5). |
| Open questions | AQ-5 token lifetime and refresh |

**Request body** (required) — `LoginRequest`

| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| `email` | string (email) | yes | max length `254` |  |
| `password` | string | yes | min length `1`; max length `72` |  |

Request example: Valid credentials

```json
{
  "email": "anna.demo@example.org",
  "password": "<your-password>"
}
```

Request example: Wrong password (401)

```json
{
  "email": "anna.demo@example.org",
  "password": "definitely-wrong"
}
```

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 200 | Signed in. | `TokenResponse` (`application/json`) |
| 400 | Malformed JSON, invalid parameter or field values (nothing was changed). | `Problem` (`application/problem+json`) |
| 401 | Invalid credentials. The response is identical whether the account exists or not. | `Problem` (`application/problem+json`) |

Response 200

```json
{
  "accessToken": "<jwt>",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

Response 400

```json
{
  "type": "https://veya.example/problems/validation-failed",
  "title": "Request is not valid",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "detail": "One or more fields are invalid.",
  "instance": "/api/v1/me/profile",
  "errors": [
    {
      "field": "energyTargetKcal",
      "message": "must be between 500 and 10000",
      "rejectedValue": 100
    }
  ]
}
```

Response 401

```json
{
  "type": "https://veya.example/problems/invalid-credentials",
  "title": "Invalid credentials",
  "status": 401,
  "code": "INVALID_CREDENTIALS",
  "detail": "E-mail or password is incorrect.",
  "instance": "/api/v1/auth/login"
}
```

### `GET /api/v1/me`

**Current account** — operationId `getMe`

| | |
|---|---|
| Status | **IMPLEMENTED** |
| Module | `identity` |
| BA stories | US-01, US-30 |
| Acceptance criteria | US-30: missing/invalid token returns 401 without disclosing personal data. |
| Contract source | PROPOSED |
| Authentication | JWT bearer token |
| Authorization / ownership | Authenticated user; returns only the caller's own account. Never returns the password hash. |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 200 | Account of the authenticated user. | `Account` (`application/json`) |
| 401 | Missing, invalid or expired token, or the account no longer exists. | `Problem` (`application/problem+json`) |

Response 200

```json
{
  "userId": "a0000000-0000-4000-8000-000000000001",
  "email": "anna.demo@example.org",
  "adultConfirmedAt": "2026-11-01T08:00:00Z",
  "createdAt": "2026-11-01T08:00:00Z",
  "hasProfile": true
}
```

Response 401

```json
{
  "type": "https://veya.example/problems/authentication-required",
  "title": "Authentication required",
  "status": 401,
  "code": "AUTHENTICATION_REQUIRED",
  "detail": "A valid bearer token is required.",
  "instance": "/api/v1/me"
}
```

### `DELETE /api/v1/me`

**Delete my account and personal records** — operationId `deleteMyAccount`

| | |
|---|---|
| Status | **IMPLEMENTED** |
| Module | `identity` |
| BA stories | US-04 |
| Acceptance criteria | US-04: given explicit confirmation, when deletion completes, then associated personal records are deleted under the retention policy and the previous session cannot access them. |
| Contract source | PROPOSED |
| Authentication | JWT bearer token |
| Authorization / ownership | Authenticated user; deletes only the caller's own account. |
| Pagination / idempotency / notes | Idempotent from the client view: after deletion the token is invalid, so a repeat returns 401. LEG-4 backup expiry (90 days) is operational, not part of the API. |

Erases the account and all personal records (profile, consents, constraints, diary, plans, proposals,
decisions) through ON DELETE CASCADE. Requires explicit confirmation `confirm=true`, which the client sends
only after the user confirmed in the UI. Afterwards every previously issued token is rejected with 401.

**Parameters**

| Name | In | Type | Required | Constraints | Description |
|---|---|---|---|---|---|
| `confirm` | query | boolean = `True` | yes |  | Must be `true` - explicit confirmation given by the user. |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 204 | Account and personal records deleted. | — |
| 400 | Confirmation missing. | `Problem` (`application/problem+json`) |
| 401 | Missing, invalid or expired token, or the account no longer exists. | `Problem` (`application/problem+json`) |

Response 400

```json
{
  "type": "https://veya.example/problems/confirmation-required",
  "title": "Confirmation required",
  "status": 400,
  "code": "CONFIRMATION_REQUIRED",
  "detail": "Account deletion requires confirm=true.",
  "instance": "/api/v1/me"
}
```

Response 401

```json
{
  "type": "https://veya.example/problems/authentication-required",
  "title": "Authentication required",
  "status": 401,
  "code": "AUTHENTICATION_REQUIRED",
  "detail": "A valid bearer token is required.",
  "instance": "/api/v1/me"
}
```

### `GET /api/v1/me/export`

**Export my data as JSON (LEG-4)** — operationId `exportMyData`

| | |
|---|---|
| Status | **IMPLEMENTED** |
| Module | `identity` |
| BA stories | US-04 |
| Acceptance criteria | LEG-4: support access, correction, export and deletion (agreed decision Q6: minimal JSON export). This is not US-36 (CSV, Could). |
| Contract source | PROPOSED (decision Q6) |
| Authentication | JWT bearer token |
| Authorization / ownership | Authenticated user; only own data. |

Minimal JSON export of the caller's own records (Q6), produced by the database function `export_user_data`. Contains no password hash.

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 200 | Export document. | `DataExport` (`application/json`) |
| 401 | Missing, invalid or expired token, or the account no longer exists. | `Problem` (`application/problem+json`) |

Response 200

```json
{
  "exportedAt": "2026-11-22T10:00:00Z",
  "account": {
    "id": "a0000000-0000-4000-8000-000000000001",
    "email": "anna.demo@example.org",
    "createdAt": "2026-11-01T08:00:00Z",
    "adultConfirmedAt": "2026-11-01T08:00:00Z"
  },
  "profile": {
    "goal_code": "LOSE_WEIGHT",
    "energy_target_kcal": 1900
  },
  "foodExclusions": [
    "PEANUT"
  ],
  "consents": [
    {
      "type": "PRIVACY_NOTICE",
      "version": "1.0",
      "grantedAt": "2026-11-01T08:00:00Z",
      "withdrawnAt": null
    }
  ],
  "dailyConstraints": [],
  "foodEntries": [],
  "activityEntries": [],
  "savedMeals": [],
  "plans": []
}
```

Response 401

```json
{
  "type": "https://veya.example/problems/authentication-required",
  "title": "Authentication required",
  "status": 401,
  "code": "AUTHENTICATION_REQUIRED",
  "detail": "A valid bearer token is required.",
  "instance": "/api/v1/me"
}
```

### `GET /api/v1/consent-documents`

**Current privacy notice and consent texts** — operationId `listConsentDocuments`

| | |
|---|---|
| Status | **IMPLEMENTED** |
| Module | `identity` |
| BA stories | US-01 |
| Acceptance criteria | LEG-2: display a clear privacy notice; obtain explicit, separately recorded and versioned consent. |
| Contract source | PROPOSED (decision Q7) |
| Authentication | Public |
| Authorization / ownership | Public. |

Versioned documents the user accepts at registration (LEG-2, Q7). Public, needed before sign-up.

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 200 | Current document versions. | array of `ConsentDocument` (`application/json`) |

Response 200

```json
[
  {
    "consentDocumentId": 1,
    "consentType": "PRIVACY_NOTICE",
    "version": "1.0",
    "publishedOn": "2026-11-01",
    "summary": "VEYA privacy notice: purposes, legal bases (Art. 6(1)(b)), retention and user rights."
  },
  {
    "consentDocumentId": 2,
    "consentType": "HEALTH_DATA_CONSENT",
    "version": "1.0",
    "publishedOn": "2026-11-01",
    "summary": "Explicit consent to process nutrition and activity records that may be health data (Art. 9(2)(a))."
  }
]
```

### `GET /api/v1/me/consents`

**My consent records** — operationId `listMyConsents`

| | |
|---|---|
| Status | **IMPLEMENTED** |
| Module | `identity` |
| BA stories | US-01 |
| Acceptance criteria | LEG-2 / Q7: separate consent record with version and time. |
| Contract source | PROPOSED (decision Q7) |
| Authentication | JWT bearer token |
| Authorization / ownership | Authenticated user; only own consents. |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 200 | Consent records, active and withdrawn. | array of `UserConsent` (`application/json`) |
| 401 | Missing, invalid or expired token, or the account no longer exists. | `Problem` (`application/problem+json`) |

Response 200

```json
[
  {
    "consentId": "6f1c2a9e-3b7d-4e8f-a1b2-c3d4e5f60718",
    "consentDocumentId": 1,
    "consentType": "PRIVACY_NOTICE",
    "version": "1.0",
    "grantedAt": "2026-11-01T08:00:00Z",
    "withdrawnAt": null
  },
  {
    "consentId": "7a2d3b0f-4c8e-4f90-b2c3-d4e5f6071829",
    "consentDocumentId": 2,
    "consentType": "HEALTH_DATA_CONSENT",
    "version": "1.0",
    "grantedAt": "2026-11-01T08:01:00Z",
    "withdrawnAt": null
  }
]
```

Response 401

```json
{
  "type": "https://veya.example/problems/authentication-required",
  "title": "Authentication required",
  "status": 401,
  "code": "AUTHENTICATION_REQUIRED",
  "detail": "A valid bearer token is required.",
  "instance": "/api/v1/me"
}
```

### `POST /api/v1/me/consents`

**Accept a consent document version** — operationId `grantConsent`

| | |
|---|---|
| Status | **IMPLEMENTED** |
| Module | `identity` |
| BA stories | US-01 |
| Acceptance criteria | LEG-2: explicit, separately recorded and versioned consent. |
| Contract source | PROPOSED (decision Q7) |
| Authentication | JWT bearer token |
| Authorization / ownership | Authenticated user; records consent only for the caller. |

Records acceptance of a (new) document version, e.g. after the privacy notice was updated.

**Request body** (required) — `GrantConsentRequest`

| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| `consentDocumentId` | integer | yes | >= `1` |  |

Request example

```json
{
  "consentDocumentId": 2
}
```

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 201 | Consent recorded. | `UserConsent` (`application/json`) |
| 400 | Malformed JSON, invalid parameter or field values (nothing was changed). | `Problem` (`application/problem+json`) |
| 401 | Missing, invalid or expired token, or the account no longer exists. | `Problem` (`application/problem+json`) |
| 404 | The resource does not exist. | `Problem` (`application/problem+json`) |
| 409 | An active consent for this document version already exists. | `Problem` (`application/problem+json`) |

Response 201

```json
{
  "consentId": "8b3e4c1a-5d9f-4a01-c3d4-e5f60718293a",
  "consentDocumentId": 2,
  "consentType": "HEALTH_DATA_CONSENT",
  "version": "1.0",
  "grantedAt": "2026-11-22T10:05:00Z",
  "withdrawnAt": null
}
```

Response 400

```json
{
  "type": "https://veya.example/problems/validation-failed",
  "title": "Request is not valid",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "detail": "One or more fields are invalid.",
  "instance": "/api/v1/me/profile",
  "errors": [
    {
      "field": "energyTargetKcal",
      "message": "must be between 500 and 10000",
      "rejectedValue": 100
    }
  ]
}
```

Response 401

```json
{
  "type": "https://veya.example/problems/authentication-required",
  "title": "Authentication required",
  "status": 401,
  "code": "AUTHENTICATION_REQUIRED",
  "detail": "A valid bearer token is required.",
  "instance": "/api/v1/me"
}
```

Response 404

```json
{
  "type": "https://veya.example/problems/resource-not-found",
  "title": "Not found",
  "status": 404,
  "code": "RESOURCE_NOT_FOUND",
  "detail": "The requested resource does not exist.",
  "instance": "/api/v1/plans/00000000-0000-4000-8000-000000000000"
}
```

Response 409

```json
{
  "type": "https://veya.example/problems/consent-already-active",
  "title": "Consent already active",
  "status": 409,
  "code": "CONSENT_ALREADY_ACTIVE",
  "detail": "Consent to HEALTH_DATA_CONSENT 1.0 is already active.",
  "instance": "/api/v1/me/consents"
}
```

### `POST /api/v1/me/consents/{consentId}/withdrawal`

**Withdraw a consent** — operationId `withdrawConsent`

| | |
|---|---|
| Status | **IMPLEMENTED** |
| Module | `identity` |
| BA stories | US-01 |
| Acceptance criteria | LEG-2 consent; GDPR withdrawal. Database allows exactly one withdrawal per record. |
| Contract source | PROPOSED (decision Q7) |
| Authentication | JWT bearer token |
| Authorization / ownership | Authenticated user; only own consent records (another user's record -> 403). |
| Open questions | AQ-4 consequences of withdrawing health-data consent |

Records the withdrawal time (allowed once). What the application does after the health-data consent is withdrawn is an open question (AQ-4).

**Parameters**

| Name | In | Type | Required | Constraints | Description |
|---|---|---|---|---|---|
| `consentId` | path | string (uuid) | yes |  |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 200 | Consent withdrawn. | `UserConsent` (`application/json`) |
| 401 | Missing, invalid or expired token, or the account no longer exists. | `Problem` (`application/problem+json`) |
| 403 | The resource belongs to another user (US-30, NFR-08). No data of that user is disclosed. OPEN (AQ-2): 403 for another user's existing resource vs 404 - the database routines report "not found or not owned" as VY404, so the API must check ownership first; to be confirmed. | `Problem` (`application/problem+json`) |
| 404 | The resource does not exist. | `Problem` (`application/problem+json`) |
| 409 | Already withdrawn. | `Problem` (`application/problem+json`) |

Response 200

```json
{
  "consentId": "7a2d3b0f-4c8e-4f90-b2c3-d4e5f6071829",
  "consentDocumentId": 2,
  "consentType": "HEALTH_DATA_CONSENT",
  "version": "1.0",
  "grantedAt": "2026-11-01T08:01:00Z",
  "withdrawnAt": "2026-11-22T10:10:00Z"
}
```

Response 401

```json
{
  "type": "https://veya.example/problems/authentication-required",
  "title": "Authentication required",
  "status": 401,
  "code": "AUTHENTICATION_REQUIRED",
  "detail": "A valid bearer token is required.",
  "instance": "/api/v1/me"
}
```

Response 403

```json
{
  "type": "https://veya.example/problems/access-denied",
  "title": "Access denied",
  "status": 403,
  "code": "ACCESS_DENIED",
  "detail": "You cannot access this resource.",
  "instance": "/api/v1/plans/5e0c7f4a-1b2d-4c3e-9f8a-7b6c5d4e3f21"
}
```

Response 404

```json
{
  "type": "https://veya.example/problems/resource-not-found",
  "title": "Not found",
  "status": 404,
  "code": "RESOURCE_NOT_FOUND",
  "detail": "The requested resource does not exist.",
  "instance": "/api/v1/plans/00000000-0000-4000-8000-000000000000"
}
```

Response 409

```json
{
  "type": "https://veya.example/problems/consent-already-withdrawn",
  "title": "Consent already withdrawn",
  "status": 409,
  "code": "CONSENT_ALREADY_WITHDRAWN",
  "detail": "This consent was withdrawn on 2026-11-22.",
  "instance": "/api/v1/me/consents/7a2d3b0f-4c8e-4f90-b2c3-d4e5f6071829/withdrawal"
}
```

## Profile and planning constraints

### `GET /api/v1/me/profile`

**My profile and goal** — operationId `getMyProfile`

| | |
|---|---|
| Status | **IMPLEMENTED** |
| Module | `profile` |
| BA stories | US-02 |
| Acceptance criteria | US-02: given authenticated, valid values, when saved, then the profile appears on subsequent requests. |
| Contract source | PROPOSED |
| Authentication | JWT bearer token |
| Authorization / ownership | Authenticated user; own profile only. |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 200 | Profile. | `Profile` (`application/json`) |
| 401 | Missing, invalid or expired token, or the account no longer exists. | `Problem` (`application/problem+json`) |
| 404 | The user has not created a profile yet. | `Problem` (`application/problem+json`) |

Response 200

```json
{
  "goal": "LOSE_WEIGHT",
  "energyTargetKcal": 1900,
  "proteinTargetG": 110.0,
  "fatTargetG": 60.0,
  "carbsTargetG": 220.0,
  "foodExclusions": [
    "PEANUT"
  ],
  "updatedAt": "2026-11-01T08:05:00Z"
}
```

Response 401

```json
{
  "type": "https://veya.example/problems/authentication-required",
  "title": "Authentication required",
  "status": 401,
  "code": "AUTHENTICATION_REQUIRED",
  "detail": "A valid bearer token is required.",
  "instance": "/api/v1/me"
}
```

Response 404

```json
{
  "type": "https://veya.example/problems/profile-not-found",
  "title": "Profile not found",
  "status": 404,
  "code": "PROFILE_NOT_FOUND",
  "detail": "Create the profile with PUT /api/v1/me/profile.",
  "instance": "/api/v1/me/profile"
}
```

### `PUT /api/v1/me/profile`

**Create or update my profile, goal and permanent exclusions** — operationId `saveMyProfile`

| | |
|---|---|
| Status | **IMPLEMENTED** |
| Module | `profile` |
| BA stories | US-02, US-03 |
| Acceptance criteria | US-02: invalid ranges return a field error and do not change stored data. US-03: exclusions are mandatory conditions for meal, activity and recovery planning. |
| Contract source | PROPOSED |
| Authentication | JWT bearer token |
| Authorization / ownership | Authenticated user; own profile only. |
| Pagination / idempotency / notes | Idempotent (PUT replaces the whole profile including the exclusion list). |

Stores the goal, daily targets entered by the user (not calculated - no BMR/TDEE) and permanent food
exclusions (Q5). Invalid ranges return field errors and do not change stored data.

**Request body** (required) — `ProfileRequest`

| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| `goal` | `Goal` | yes | one of `LOSE_WEIGHT`, `MAINTAIN_WEIGHT`, `GAIN_WEIGHT` | Codes from `goal_type`. |
| `energyTargetKcal` | integer | yes | >= `500`; <= `10000` | Daily target entered by the user (not calculated). |
| `proteinTargetG` | number | yes | >= `0`; <= `1000` |  |
| `fatTargetG` | number | yes | >= `0`; <= `1000` |  |
| `carbsTargetG` | number | yes | >= `0`; <= `1000` |  |
| `foodExclusions` | array of `Code` | yes | unique | Permanent exclusions - ingredient codes (Q5). |

Request example: Valid profile

```json
{
  "goal": "LOSE_WEIGHT",
  "energyTargetKcal": 1900,
  "proteinTargetG": 110,
  "fatTargetG": 60,
  "carbsTargetG": 220,
  "foodExclusions": [
    "PEANUT"
  ]
}
```

Request example: Energy target out of range (400)

```json
{
  "goal": "LOSE_WEIGHT",
  "energyTargetKcal": 100,
  "proteinTargetG": 110,
  "fatTargetG": 60,
  "carbsTargetG": 220,
  "foodExclusions": []
}
```

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 200 | Profile saved. | `Profile` (`application/json`) |
| 400 | Malformed JSON, invalid parameter or field values (nothing was changed). | `Problem` (`application/problem+json`) |
| 401 | Missing, invalid or expired token, or the account no longer exists. | `Problem` (`application/problem+json`) |

Response 200

```json
{
  "goal": "LOSE_WEIGHT",
  "energyTargetKcal": 1900,
  "proteinTargetG": 110.0,
  "fatTargetG": 60.0,
  "carbsTargetG": 220.0,
  "foodExclusions": [
    "PEANUT"
  ],
  "updatedAt": "2026-11-22T10:15:00Z"
}
```

Response 400

```json
{
  "type": "https://veya.example/problems/validation-failed",
  "title": "Request is not valid",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "detail": "One or more fields are invalid.",
  "instance": "/api/v1/me/profile",
  "errors": [
    {
      "field": "energyTargetKcal",
      "message": "must be between 500 and 10000",
      "rejectedValue": 100
    }
  ]
}
```

Response 401

```json
{
  "type": "https://veya.example/problems/authentication-required",
  "title": "Authentication required",
  "status": 401,
  "code": "AUTHENTICATION_REQUIRED",
  "detail": "A valid bearer token is required.",
  "instance": "/api/v1/me"
}
```

### `GET /api/v1/constraints/{date}`

**Planning constraints of a day** — operationId `getDailyConstraints`

| | |
|---|---|
| Status | **IMPLEMENTED** |
| Module | `profile` |
| BA stories | US-03 |
| Acceptance criteria | US-03: given valid daily constraints, when saved, then they are stored and treated as mandatory conditions. |
| Contract source | PROPOSED |
| Authentication | JWT bearer token |
| Authorization / ownership | Authenticated user; own constraints only. |

Returns the day's constraints; a day without saved constraints returns empty lists and `availableTimeMin = null` (PROPOSED).

**Parameters**

| Name | In | Type | Required | Constraints | Description |
|---|---|---|---|---|---|
| `date` | path | string (date) | yes |  | Local plan date (`YYYY-MM-DD`). |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 200 | Constraints of the day. | `DailyConstraints` (`application/json`) |
| 400 | Malformed JSON, invalid parameter or field values (nothing was changed). | `Problem` (`application/problem+json`) |
| 401 | Missing, invalid or expired token, or the account no longer exists. | `Problem` (`application/problem+json`) |

Response 200

```json
{
  "date": "2026-11-16",
  "availableTimeMin": 40,
  "availableEquipment": [],
  "availableIngredients": [],
  "excludedIngredients": [
    "FISH"
  ]
}
```

Response 400

```json
{
  "type": "https://veya.example/problems/validation-failed",
  "title": "Request is not valid",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "detail": "One or more fields are invalid.",
  "instance": "/api/v1/me/profile",
  "errors": [
    {
      "field": "energyTargetKcal",
      "message": "must be between 500 and 10000",
      "rejectedValue": 100
    }
  ]
}
```

Response 401

```json
{
  "type": "https://veya.example/problems/authentication-required",
  "title": "Authentication required",
  "status": 401,
  "code": "AUTHENTICATION_REQUIRED",
  "detail": "A valid bearer token is required.",
  "instance": "/api/v1/me"
}
```

### `PUT /api/v1/constraints/{date}`

**Save planning constraints of a day** — operationId `saveDailyConstraints`

| | |
|---|---|
| Status | **IMPLEMENTED** |
| Module | `profile` |
| BA stories | US-03, US-15 |
| Acceptance criteria | US-03 (stored, mandatory conditions); US-15: changed time/ingredients/equipment that make a future slot infeasible set recoveryRequired. |
| Contract source | PROPOSED |
| Authentication | JWT bearer token |
| Authorization / ownership | Authenticated user; own constraints only. |
| Pagination / idempotency / notes | Idempotent (PUT replaces the day's constraints; a deviation is recorded once per item). Infeasibility rule implemented as PROPOSED in AQ-13 (needs approval). |
| Open questions | AQ-13 rule for infeasible future items |

Saves available time, equipment, available ingredients and temporary exclusions (Q3, Q5). If the change
makes a remaining planned item infeasible, the backend records a `CONSTRAINT_INFEASIBLE` deviation and the
response reports `recoveryRequired = true` with the affected items (US-15 rule 3). The plan itself is not
changed.

**Parameters**

| Name | In | Type | Required | Constraints | Description |
|---|---|---|---|---|---|
| `date` | path | string (date) | yes |  | Local plan date (`YYYY-MM-DD`). |

**Request body** (required) — `DailyConstraintsRequest`

| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| `availableTimeMin` | integer or null | yes | >= `0`; <= `1440` | Minutes available for remaining activity/preparation; null = not restricted. |
| `availableEquipment` | array of `Code` | yes | unique | Equipment available today; empty = none. |
| `availableIngredients` | array of `Code` | yes | unique | Ingredients the user has today (Q3). |
| `excludedIngredients` | array of `Code` | yes | unique | Temporary exclusions for this day (Q5); must not overlap availableIngredients. |

Request example: Fish unavailable, 40 minutes, no equipment

```json
{
  "availableTimeMin": 40,
  "availableEquipment": [],
  "availableIngredients": [],
  "excludedIngredients": [
    "FISH"
  ]
}
```

Request example: Negative available time (400)

```json
{
  "availableTimeMin": -5,
  "availableEquipment": [],
  "availableIngredients": [],
  "excludedIngredients": []
}
```

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 200 | Constraints saved; impact on the active plan of that day. | `DailyConstraintsResult` (`application/json`) |
| 400 | Malformed JSON, invalid parameter or field values (nothing was changed). | `Problem` (`application/problem+json`) |
| 401 | Missing, invalid or expired token, or the account no longer exists. | `Problem` (`application/problem+json`) |

Response 200

```json
{
  "constraints": {
    "date": "2026-11-16",
    "availableTimeMin": 40,
    "availableEquipment": [],
    "availableIngredients": [],
    "excludedIngredients": [
      "FISH"
    ]
  },
  "planImpact": {
    "planId": "5e0c7f4a-1b2d-4c3e-9f8a-7b6c5d4e3f21",
    "recoveryRequired": true,
    "infeasibleItems": [
      {
        "itemKey": "b0000000-0000-4000-8000-000000000203",
        "reason": "Planned dinner contains fish, which is excluded for 2026-11-16."
      }
    ]
  }
}
```

Response 400

```json
{
  "type": "https://veya.example/problems/validation-failed",
  "title": "Request is not valid",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "detail": "One or more fields are invalid.",
  "instance": "/api/v1/me/profile",
  "errors": [
    {
      "field": "energyTargetKcal",
      "message": "must be between 500 and 10000",
      "rejectedValue": 100
    }
  ]
}
```

Response 401

```json
{
  "type": "https://veya.example/problems/authentication-required",
  "title": "Authentication required",
  "status": 401,
  "code": "AUTHENTICATION_REQUIRED",
  "detail": "A valid bearer token is required.",
  "instance": "/api/v1/me"
}
```

## Nutrition and food catalogue

### `GET /api/v1/foods`

**Search the food catalogue** — operationId `searchFoods`

| | |
|---|---|
| Status | **IMPLEMENTED** |
| Module | `nutrition` |
| BA stories | US-05, US-31 |
| Acceptance criteria | US-05: given a reachable provider, when the user searches, then normalized foods show calories, macros and source; timeout produces a documented error and manual-input option. US-31: stable error code and safe retry/manual route. |
| Contract source | PROPOSED |
| Authentication | JWT bearer token |
| Authorization / ownership | Authenticated user; catalogue data is not personal. |
| Pagination / idempotency / notes | Provider: Open Food Facts adapter (2 s timeout), disabled by default - enable with VEYA_FOOD_PROVIDER_ENABLED=true (AQ-7: provider choice open). Provider products are cached in the local catalogue (source PROVIDER, attribution set). Fallback as described above, verified against a stub provider in the tests; without a configured provider the result is local only with providerStatus UNAVAILABLE. Sorted by relevance (names starting with the text first, then name). `limit` 1-50, default 20; no pagination (bounded result). |
| Open questions | AQ-7 provider fallback behaviour |

Case-insensitive substring search over the local catalogue and the external food provider, normalized to
calories and macros per 100 g with the source (US-05). If the provider fails but local results exist, the
response is 200 with `providerStatus = UNAVAILABLE` (R1 fallback, PROPOSED). If the provider times out and
there are no local results, 504 with `manualEntryAllowed = true` (US-05, US-31).

**Parameters**

| Name | In | Type | Required | Constraints | Description |
|---|---|---|---|---|---|
| `q` | query | string | yes | min length `2`; max length `100` | Search text, 2-100 characters. |
| `limit` | query | integer | no | >= `1`; <= `50`; default `20` |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 200 | Matching foods. | `FoodSearchResult` (`application/json`) |
| 400 | Malformed JSON, invalid parameter or field values (nothing was changed). | `Problem` (`application/problem+json`) |
| 401 | Missing, invalid or expired token, or the account no longer exists. | `Problem` (`application/problem+json`) |
| 502 | The external provider failed (5xx) and there are no local results (US-31). Manual entry stays available. | `Problem` (`application/problem+json`) |
| 504 | The external provider timed out and there are no local results (US-05, US-31). | `Problem` (`application/problem+json`) |

Response 200

```json
{
  "providerStatus": "OK",
  "items": [
    {
      "foodId": 4,
      "name": "Chicken breast, cooked",
      "source": "LOCAL",
      "kcalPer100g": 165.0,
      "proteinPer100g": 31.0,
      "fatPer100g": 3.6,
      "carbsPer100g": 0.0,
      "attribution": null
    }
  ]
}
```

Response 400

```json
{
  "type": "https://veya.example/problems/validation-failed",
  "title": "Request is not valid",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "detail": "One or more fields are invalid.",
  "instance": "/api/v1/me/profile",
  "errors": [
    {
      "field": "energyTargetKcal",
      "message": "must be between 500 and 10000",
      "rejectedValue": 100
    }
  ]
}
```

Response 401

```json
{
  "type": "https://veya.example/problems/authentication-required",
  "title": "Authentication required",
  "status": 401,
  "code": "AUTHENTICATION_REQUIRED",
  "detail": "A valid bearer token is required.",
  "instance": "/api/v1/me"
}
```

Response 502

```json
{
  "type": "https://veya.example/problems/provider-unavailable",
  "title": "Food provider unavailable",
  "status": 502,
  "code": "PROVIDER_UNAVAILABLE",
  "detail": "The food provider is not available. You can enter the food manually.",
  "instance": "/api/v1/foods?q=quinoa",
  "manualEntryAllowed": true
}
```

Response 504

```json
{
  "type": "https://veya.example/problems/provider-timeout",
  "title": "Food provider timed out",
  "status": 504,
  "code": "PROVIDER_TIMEOUT",
  "detail": "The food provider did not answer in time. Retry or enter the food manually.",
  "instance": "/api/v1/foods?q=quinoa",
  "manualEntryAllowed": true
}
```

### `GET /api/v1/meals`

**Candidate meals of the catalogue** — operationId `listMeals`

| | |
|---|---|
| Status | **IMPLEMENTED** |
| Module | `nutrition` |
| BA stories | US-09 |
| Acceptance criteria | US-09: the proposed menu excludes prohibited items. |
| Contract source | PROPOSED |
| Authentication | JWT bearer token |
| Authorization / ownership | Authenticated user. |
| Pagination / idempotency / notes | `limit` 1-100 (NFR-02 bounded candidate set), default 50. Sorted by name. |

Meals with derived nutrition and ingredient codes, used to build a plan manually (US-09). Meals containing the user's permanent exclusions are not returned.

**Parameters**

| Name | In | Type | Required | Constraints | Description |
|---|---|---|---|---|---|
| `mealSlot` | query | `MealSlot` | no | one of `BREAKFAST`, `LUNCH`, `DINNER`, `SNACK` | Only meals suitable for this slot (meals without a preferred slot are always included). |
| `q` | query | string | no | min length `2`; max length `100` |  |
| `limit` | query | integer | no | >= `1`; <= `100`; default `50` |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 200 | Candidate meals. | array of `MealCandidate` (`application/json`) |
| 400 | Malformed JSON, invalid parameter or field values (nothing was changed). | `Problem` (`application/problem+json`) |
| 401 | Missing, invalid or expired token, or the account no longer exists. | `Problem` (`application/problem+json`) |

Response 200

```json
[
  {
    "mealId": 4,
    "name": "Chicken rice bowl",
    "mealSlot": "LUNCH",
    "prepTimeMin": 20,
    "kcal": 629.9,
    "proteinG": 54.7,
    "fatG": 16.4,
    "carbsG": 63.0,
    "ingredientCodes": [
      "BROCCOLI",
      "CHICKEN",
      "RICE"
    ]
  },
  {
    "mealId": 6,
    "name": "Lentil soup",
    "mealSlot": null,
    "prepTimeMin": 35,
    "kcal": 475.9,
    "proteinG": 26.4,
    "fatG": 11.4,
    "carbsG": 69.9,
    "ingredientCodes": [
      "LENTIL",
      "VEGETABLES"
    ]
  }
]
```

Response 400

```json
{
  "type": "https://veya.example/problems/validation-failed",
  "title": "Request is not valid",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "detail": "One or more fields are invalid.",
  "instance": "/api/v1/me/profile",
  "errors": [
    {
      "field": "energyTargetKcal",
      "message": "must be between 500 and 10000",
      "rejectedValue": 100
    }
  ]
}
```

Response 401

```json
{
  "type": "https://veya.example/problems/authentication-required",
  "title": "Authentication required",
  "status": 401,
  "code": "AUTHENTICATION_REQUIRED",
  "detail": "A valid bearer token is required.",
  "instance": "/api/v1/me"
}
```

### `GET /api/v1/food-entries`

**Diary entries of a day** — operationId `listFoodEntries`

| | |
|---|---|
| Status | **IMPLEMENTED** |
| Module | `nutrition` |
| BA stories | US-06, US-07 |
| Acceptance criteria | US-06: confirmed entries are persisted unchanged. |
| Contract source | PROPOSED |
| Authentication | JWT bearer token |
| Authorization / ownership | Authenticated user; own entries only. |
| Pagination / idempotency / notes | Sorted by meal slot order, then creation time. No pagination (one day). |

**Parameters**

| Name | In | Type | Required | Constraints | Description |
|---|---|---|---|---|---|
| `date` | query | string (date) | yes |  | Local plan date (`YYYY-MM-DD`). |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 200 | Entries of the day. | array of `FoodEntry` (`application/json`) |
| 400 | Malformed JSON, invalid parameter or field values (nothing was changed). | `Problem` (`application/problem+json`) |
| 401 | Missing, invalid or expired token, or the account no longer exists. | `Problem` (`application/problem+json`) |

Response 200

```json
[
  {
    "entryId": "9d4e5f60-7182-4930-a4b5-c6d7e8f90a1b",
    "date": "2026-11-16",
    "mealSlot": "LUNCH",
    "inputSource": "MANUAL",
    "foodId": null,
    "savedMealId": null,
    "planItemKey": "b0000000-0000-4000-8000-000000000202",
    "foodName": "Office pizza (3 slices)",
    "portionG": 330.0,
    "kcal": 879.9,
    "proteinG": 32.0,
    "fatG": 38.0,
    "carbsG": 110.0,
    "createdAt": "2026-11-16T12:40:00Z",
    "updatedAt": "2026-11-16T12:40:00Z"
  }
]
```

Response 400

```json
{
  "type": "https://veya.example/problems/validation-failed",
  "title": "Request is not valid",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "detail": "One or more fields are invalid.",
  "instance": "/api/v1/me/profile",
  "errors": [
    {
      "field": "energyTargetKcal",
      "message": "must be between 500 and 10000",
      "rejectedValue": 100
    }
  ]
}
```

Response 401

```json
{
  "type": "https://veya.example/problems/authentication-required",
  "title": "Authentication required",
  "status": 401,
  "code": "AUTHENTICATION_REQUIRED",
  "detail": "A valid bearer token is required.",
  "instance": "/api/v1/me"
}
```

### `POST /api/v1/food-entries`

**Log a confirmed food entry** — operationId `createFoodEntry`

| | |
|---|---|
| Status | **IMPLEMENTED** |
| Module | `nutrition` |
| BA stories | US-06, US-15 |
| Acceptance criteria | US-06: given valid confirmed food values, when added, then the entry is persisted unchanged; invalid values are rejected without affecting totals. |
| Contract source | PROPOSED |
| Authentication | JWT bearer token |
| Authorization / ownership | Authenticated user; `planItemKey` must belong to the caller's own plan (otherwise 403). |
| Pagination / idempotency / notes | Not idempotent - each call creates an entry. Saved-meal reuse has its own endpoint. |

Stores the values the user confirmed (catalogue item or manual entry). Optionally linked to a planned MEAL
of the active plan version of the same date (`planItemKey`). The response tells whether the day now needs
recovery (the deviation is evaluated when the planned meal is confirmed, see the item status endpoint).

**Request body** (required) — `FoodEntryRequest`

| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| `date` | `LocalDate` | yes |  |  |
| `mealSlot` | `MealSlot` | yes | one of `BREAKFAST`, `LUNCH`, `DINNER`, `SNACK` | Codes from `meal_slot`. |
| `inputSource` | string enum | yes | one of `CATALOG`, `MANUAL` | SAVED_MEAL entries are created by POST /saved-meals/{id}/entries. Photo input is post-MVP. |
| `foodId` | integer or null | no | >= `1` | Required when inputSource = CATALOG. |
| `planItemKey` | string or null (uuid) | no |  | Planned MEAL of the active version on the same date. |
| `foodName` | string | yes | min length `1`; max length `200` |  |
| `portionG` | number | yes | <= `5000`; > `0` |  |
| `kcal` | `Kcal` | yes | >= `0`; <= `10000` | kcal, stored with one decimal place (numeric(7,1)). |
| `proteinG` | `Grams` | yes | >= `0`; <= `1000` | grams, stored with one decimal place. |
| `fatG` | `Grams` | yes | >= `0`; <= `1000` | grams, stored with one decimal place. |
| `carbsG` | `Grams` | yes | >= `0`; <= `1000` | grams, stored with one decimal place. |

Request example: Manual entry linked to the planned lunch

```json
{
  "date": "2026-11-16",
  "mealSlot": "LUNCH",
  "inputSource": "MANUAL",
  "foodId": null,
  "planItemKey": "b0000000-0000-4000-8000-000000000202",
  "foodName": "Office pizza (3 slices)",
  "portionG": 330,
  "kcal": 879.9,
  "proteinG": 32,
  "fatG": 38,
  "carbsG": 110
}
```

Request example: Zero portion (400)

```json
{
  "date": "2026-11-16",
  "mealSlot": "SNACK",
  "inputSource": "MANUAL",
  "foodName": "Apple",
  "portionG": 0,
  "kcal": 50,
  "proteinG": 0,
  "fatG": 0,
  "carbsG": 12
}
```

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 201 | Entry stored. | `FoodEntryResult` (`application/json`) |
| 400 | Malformed JSON, invalid parameter or field values (nothing was changed). | `Problem` (`application/problem+json`) |
| 401 | Missing, invalid or expired token, or the account no longer exists. | `Problem` (`application/problem+json`) |
| 403 | The resource belongs to another user (US-30, NFR-08). No data of that user is disclosed. OPEN (AQ-2): 403 for another user's existing resource vs 404 - the database routines report "not found or not owned" as VY404, so the API must check ownership first; to be confirmed. | `Problem` (`application/problem+json`) |
| 409 | The request tried to link to or change a superseded plan version. | `Problem` (`application/problem+json`) |

Response 201

```json
{
  "entry": {
    "entryId": "9d4e5f60-7182-4930-a4b5-c6d7e8f90a1b",
    "date": "2026-11-16",
    "mealSlot": "LUNCH",
    "inputSource": "MANUAL",
    "foodId": null,
    "savedMealId": null,
    "planItemKey": "b0000000-0000-4000-8000-000000000202",
    "foodName": "Office pizza (3 slices)",
    "portionG": 330.0,
    "kcal": 879.9,
    "proteinG": 32.0,
    "fatG": 38.0,
    "carbsG": 110.0,
    "createdAt": "2026-11-16T12:40:00Z",
    "updatedAt": "2026-11-16T12:40:00Z"
  },
  "recoveryRequired": false
}
```

Response 400

```json
{
  "type": "https://veya.example/problems/validation-failed",
  "title": "Request is not valid",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "detail": "One or more fields are invalid.",
  "instance": "/api/v1/me/profile",
  "errors": [
    {
      "field": "energyTargetKcal",
      "message": "must be between 500 and 10000",
      "rejectedValue": 100
    }
  ]
}
```

Response 401

```json
{
  "type": "https://veya.example/problems/authentication-required",
  "title": "Authentication required",
  "status": 401,
  "code": "AUTHENTICATION_REQUIRED",
  "detail": "A valid bearer token is required.",
  "instance": "/api/v1/me"
}
```

Response 403

```json
{
  "type": "https://veya.example/problems/access-denied",
  "title": "Access denied",
  "status": 403,
  "code": "ACCESS_DENIED",
  "detail": "You cannot access this resource.",
  "instance": "/api/v1/plans/5e0c7f4a-1b2d-4c3e-9f8a-7b6c5d4e3f21"
}
```

Response 409

```json
{
  "type": "https://veya.example/problems/immutable-history",
  "title": "History cannot be changed",
  "status": 409,
  "code": "IMMUTABLE_HISTORY",
  "detail": "Entries can only be linked to items of the active plan version.",
  "instance": "/api/v1/food-entries"
}
```

### `PATCH /api/v1/food-entries/{entryId}`

**Correct a food entry** — operationId `updateFoodEntry`

| | |
|---|---|
| Status | **IMPLEMENTED** |
| Module | `nutrition` |
| BA stories | US-06, US-15 |
| Acceptance criteria | US-06: given valid confirmed food values, when edited, then the entry is persisted unchanged; invalid values are rejected without affecting totals. |
| Contract source | PROPOSED |
| Authentication | JWT bearer token |
| Authorization / ownership | Authenticated user; another user's entry -> 403. |
| Pagination / idempotency / notes | JSON merge-patch semantics: only sent fields change. Idempotent for the same body. |

Partial update of a confirmed entry (portion, values, name, meal slot). Correcting an entry of an already confirmed planned meal re-evaluates the 200 kcal deviation.

**Parameters**

| Name | In | Type | Required | Constraints | Description |
|---|---|---|---|---|---|
| `entryId` | path | string (uuid) | yes |  |  |

**Request body** (required) — `FoodEntryPatch`

| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| `mealSlot` | `MealSlot` | no | one of `BREAKFAST`, `LUNCH`, `DINNER`, `SNACK` | Codes from `meal_slot`. |
| `foodName` | string | no | min length `1`; max length `200` |  |
| `portionG` | number | no | <= `5000`; > `0` |  |
| `kcal` | `Kcal` | no | >= `0`; <= `10000` | kcal, stored with one decimal place (numeric(7,1)). |
| `proteinG` | `Grams` | no | >= `0`; <= `1000` | grams, stored with one decimal place. |
| `fatG` | `Grams` | no | >= `0`; <= `1000` | grams, stored with one decimal place. |
| `carbsG` | `Grams` | no | >= `0`; <= `1000` | grams, stored with one decimal place. |

Request example

```json
{
  "portionG": 300,
  "kcal": 799.9
}
```

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 200 | Entry corrected. | `FoodEntryResult` (`application/json`) |
| 400 | Malformed JSON, invalid parameter or field values (nothing was changed). | `Problem` (`application/problem+json`) |
| 401 | Missing, invalid or expired token, or the account no longer exists. | `Problem` (`application/problem+json`) |
| 403 | The resource belongs to another user (US-30, NFR-08). No data of that user is disclosed. OPEN (AQ-2): 403 for another user's existing resource vs 404 - the database routines report "not found or not owned" as VY404, so the API must check ownership first; to be confirmed. | `Problem` (`application/problem+json`) |
| 404 | The resource does not exist. | `Problem` (`application/problem+json`) |

Response 200

```json
{
  "entry": {
    "entryId": "9d4e5f60-7182-4930-a4b5-c6d7e8f90a1b",
    "date": "2026-11-16",
    "mealSlot": "LUNCH",
    "inputSource": "MANUAL",
    "foodId": null,
    "savedMealId": null,
    "planItemKey": "b0000000-0000-4000-8000-000000000202",
    "foodName": "Office pizza (3 slices)",
    "portionG": 300.0,
    "kcal": 799.9,
    "proteinG": 32.0,
    "fatG": 38.0,
    "carbsG": 110.0,
    "createdAt": "2026-11-16T12:40:00Z",
    "updatedAt": "2026-11-16T12:55:00Z"
  },
  "recoveryRequired": false
}
```

Response 400

```json
{
  "type": "https://veya.example/problems/validation-failed",
  "title": "Request is not valid",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "detail": "One or more fields are invalid.",
  "instance": "/api/v1/me/profile",
  "errors": [
    {
      "field": "energyTargetKcal",
      "message": "must be between 500 and 10000",
      "rejectedValue": 100
    }
  ]
}
```

Response 401

```json
{
  "type": "https://veya.example/problems/authentication-required",
  "title": "Authentication required",
  "status": 401,
  "code": "AUTHENTICATION_REQUIRED",
  "detail": "A valid bearer token is required.",
  "instance": "/api/v1/me"
}
```

Response 403

```json
{
  "type": "https://veya.example/problems/access-denied",
  "title": "Access denied",
  "status": 403,
  "code": "ACCESS_DENIED",
  "detail": "You cannot access this resource.",
  "instance": "/api/v1/plans/5e0c7f4a-1b2d-4c3e-9f8a-7b6c5d4e3f21"
}
```

Response 404

```json
{
  "type": "https://veya.example/problems/resource-not-found",
  "title": "Not found",
  "status": 404,
  "code": "RESOURCE_NOT_FOUND",
  "detail": "The requested resource does not exist.",
  "instance": "/api/v1/plans/00000000-0000-4000-8000-000000000000"
}
```

### `DELETE /api/v1/food-entries/{entryId}`

**Remove a wrong food entry** — operationId `deleteFoodEntry`

| | |
|---|---|
| Status | **IMPLEMENTED** |
| Module | `nutrition` |
| BA stories | US-06 |
| Acceptance criteria | US-06: log and correct food; invalid values are rejected without affecting totals. |
| Contract source | PROPOSED |
| Authentication | JWT bearer token |
| Authorization / ownership | Authenticated user; another user's entry -> 403. |
| Open questions | AQ-10 deletion as part of correction |

Removes an entry the user logged by mistake (correction). PROPOSED interpretation of "log and correct" (AQ-10).

**Parameters**

| Name | In | Type | Required | Constraints | Description |
|---|---|---|---|---|---|
| `entryId` | path | string (uuid) | yes |  |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 204 | Entry removed. | — |
| 401 | Missing, invalid or expired token, or the account no longer exists. | `Problem` (`application/problem+json`) |
| 403 | The resource belongs to another user (US-30, NFR-08). No data of that user is disclosed. OPEN (AQ-2): 403 for another user's existing resource vs 404 - the database routines report "not found or not owned" as VY404, so the API must check ownership first; to be confirmed. | `Problem` (`application/problem+json`) |
| 404 | The resource does not exist. | `Problem` (`application/problem+json`) |

Response 401

```json
{
  "type": "https://veya.example/problems/authentication-required",
  "title": "Authentication required",
  "status": 401,
  "code": "AUTHENTICATION_REQUIRED",
  "detail": "A valid bearer token is required.",
  "instance": "/api/v1/me"
}
```

Response 403

```json
{
  "type": "https://veya.example/problems/access-denied",
  "title": "Access denied",
  "status": 403,
  "code": "ACCESS_DENIED",
  "detail": "You cannot access this resource.",
  "instance": "/api/v1/plans/5e0c7f4a-1b2d-4c3e-9f8a-7b6c5d4e3f21"
}
```

Response 404

```json
{
  "type": "https://veya.example/problems/resource-not-found",
  "title": "Not found",
  "status": 404,
  "code": "RESOURCE_NOT_FOUND",
  "detail": "The requested resource does not exist.",
  "instance": "/api/v1/plans/00000000-0000-4000-8000-000000000000"
}
```

### `GET /api/v1/food-entries/recent`

**Recent foods for quick re-logging** — operationId `listRecentFoodEntries`

| | |
|---|---|
| Status | **IMPLEMENTED** |
| Module | `nutrition` |
| BA stories | US-08 |
| Acceptance criteria | US-08: given an existing saved/recent item, when selected, then a new entry is created within at most three user actions without altering its original template. |
| Contract source | PROPOSED |
| Authentication | JWT bearer token |
| Authorization / ownership | Authenticated user; own entries only. |
| Pagination / idempotency / notes | Newest first, distinct by food name. `limit` 1-20, default 10. |

**Parameters**

| Name | In | Type | Required | Constraints | Description |
|---|---|---|---|---|---|
| `limit` | query | integer | no | >= `1`; <= `20`; default `10` |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 200 | Recent entries. | array of `FoodEntry` (`application/json`) |
| 400 | Malformed JSON, invalid parameter or field values (nothing was changed). | `Problem` (`application/problem+json`) |
| 401 | Missing, invalid or expired token, or the account no longer exists. | `Problem` (`application/problem+json`) |

Response 200

```json
[
  {
    "entryId": "2b3c4d5e-6f70-4812-93a4-b5c6d7e8f901",
    "date": "2026-11-15",
    "mealSlot": "BREAKFAST",
    "inputSource": "SAVED_MEAL",
    "foodId": 12,
    "savedMealId": "e0000000-0000-4000-8000-000000000001",
    "planItemKey": "b0000000-0000-4000-8000-000000000101",
    "foodName": "Eggs",
    "portionG": 120.0,
    "kcal": 171.6,
    "proteinG": 15.1,
    "fatG": 11.4,
    "carbsG": 0.8,
    "createdAt": "2026-11-15T07:10:00Z",
    "updatedAt": "2026-11-15T07:10:00Z"
  }
]
```

Response 400

```json
{
  "type": "https://veya.example/problems/validation-failed",
  "title": "Request is not valid",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "detail": "One or more fields are invalid.",
  "instance": "/api/v1/me/profile",
  "errors": [
    {
      "field": "energyTargetKcal",
      "message": "must be between 500 and 10000",
      "rejectedValue": 100
    }
  ]
}
```

Response 401

```json
{
  "type": "https://veya.example/problems/authentication-required",
  "title": "Authentication required",
  "status": 401,
  "code": "AUTHENTICATION_REQUIRED",
  "detail": "A valid bearer token is required.",
  "instance": "/api/v1/me"
}
```

### `GET /api/v1/saved-meals`

**My saved meal templates** — operationId `listSavedMeals`

| | |
|---|---|
| Status | **IMPLEMENTED** |
| Module | `nutrition` |
| BA stories | US-08 |
| Acceptance criteria | US-08: reuse saved or recent meals. |
| Contract source | PROPOSED |
| Authentication | JWT bearer token |
| Authorization / ownership | Authenticated user; own templates only. |
| Pagination / idempotency / notes | Sorted by name. No pagination. |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 200 | Templates. | array of `SavedMeal` (`application/json`) |
| 401 | Missing, invalid or expired token, or the account no longer exists. | `Problem` (`application/problem+json`) |

Response 200

```json
[
  {
    "savedMealId": "e0000000-0000-4000-8000-000000000001",
    "name": "My usual breakfast",
    "createdAt": "2026-11-01T08:10:00Z",
    "items": [
      {
        "lineNo": 1,
        "foodId": 12,
        "foodName": "Eggs",
        "portionG": 120.0,
        "kcal": 171.6,
        "proteinG": 15.1,
        "fatG": 11.4,
        "carbsG": 0.8
      },
      {
        "lineNo": 2,
        "foodId": 13,
        "foodName": "Whole wheat bread",
        "portionG": 60.0,
        "kcal": 148.2,
        "proteinG": 7.8,
        "fatG": 2.0,
        "carbsG": 24.6
      }
    ],
    "totals": {
      "kcal": 319.8,
      "proteinG": 22.9,
      "fatG": 13.4,
      "carbsG": 25.4
    }
  }
]
```

Response 401

```json
{
  "type": "https://veya.example/problems/authentication-required",
  "title": "Authentication required",
  "status": 401,
  "code": "AUTHENTICATION_REQUIRED",
  "detail": "A valid bearer token is required.",
  "instance": "/api/v1/me"
}
```

### `POST /api/v1/saved-meals`

**Save a meal template** — operationId `createSavedMeal`

| | |
|---|---|
| Status | **IMPLEMENTED** |
| Module | `nutrition` |
| BA stories | US-08 |
| Acceptance criteria | US-08: saved meals can be reused without altering the original template. |
| Contract source | PROPOSED |
| Authentication | JWT bearer token |
| Authorization / ownership | Authenticated user; the template belongs to the caller. |

**Request body** (required) — `SavedMealRequest`

| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| `name` | string | yes | min length `1`; max length `100` |  |
| `items` | array of `SavedMealLine` | yes | min items `1`; max items `30` |  |

Request example

```json
{
  "name": "My usual breakfast",
  "items": [
    {
      "foodId": 12,
      "foodName": "Eggs",
      "portionG": 120,
      "kcal": 171.6,
      "proteinG": 15.1,
      "fatG": 11.4,
      "carbsG": 0.8
    },
    {
      "foodId": 13,
      "foodName": "Whole wheat bread",
      "portionG": 60,
      "kcal": 148.2,
      "proteinG": 7.8,
      "fatG": 2.0,
      "carbsG": 24.6
    }
  ]
}
```

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 201 | Template saved. | `SavedMeal` (`application/json`) |
| 400 | Malformed JSON, invalid parameter or field values (nothing was changed). | `Problem` (`application/problem+json`) |
| 401 | Missing, invalid or expired token, or the account no longer exists. | `Problem` (`application/problem+json`) |
| 409 | A template with this name already exists for the user. | `Problem` (`application/problem+json`) |

Response 201

```json
{
  "savedMealId": "e0000000-0000-4000-8000-000000000001",
  "name": "My usual breakfast",
  "createdAt": "2026-11-22T10:20:00Z",
  "items": [
    {
      "lineNo": 1,
      "foodId": 12,
      "foodName": "Eggs",
      "portionG": 120.0,
      "kcal": 171.6,
      "proteinG": 15.1,
      "fatG": 11.4,
      "carbsG": 0.8
    },
    {
      "lineNo": 2,
      "foodId": 13,
      "foodName": "Whole wheat bread",
      "portionG": 60.0,
      "kcal": 148.2,
      "proteinG": 7.8,
      "fatG": 2.0,
      "carbsG": 24.6
    }
  ],
  "totals": {
    "kcal": 319.8,
    "proteinG": 22.9,
    "fatG": 13.4,
    "carbsG": 25.4
  }
}
```

Response 400

```json
{
  "type": "https://veya.example/problems/validation-failed",
  "title": "Request is not valid",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "detail": "One or more fields are invalid.",
  "instance": "/api/v1/me/profile",
  "errors": [
    {
      "field": "energyTargetKcal",
      "message": "must be between 500 and 10000",
      "rejectedValue": 100
    }
  ]
}
```

Response 401

```json
{
  "type": "https://veya.example/problems/authentication-required",
  "title": "Authentication required",
  "status": 401,
  "code": "AUTHENTICATION_REQUIRED",
  "detail": "A valid bearer token is required.",
  "instance": "/api/v1/me"
}
```

Response 409

```json
{
  "type": "https://veya.example/problems/saved-meal-name-taken",
  "title": "Name already used",
  "status": 409,
  "code": "SAVED_MEAL_NAME_TAKEN",
  "detail": "You already have a saved meal called \"My usual breakfast\".",
  "instance": "/api/v1/saved-meals"
}
```

### `POST /api/v1/saved-meals/{savedMealId}/entries`

**Log a saved meal into the diary** — operationId `logSavedMeal`

| | |
|---|---|
| Status | **IMPLEMENTED** |
| Module | `nutrition` |
| BA stories | US-08 |
| Acceptance criteria | US-08: a new entry is created within at most three user actions without altering its original template. |
| Contract source | PROPOSED |
| Authentication | JWT bearer token |
| Authorization / ownership | Authenticated user; another user's template -> 403. |
| Pagination / idempotency / notes | Not idempotent - each call logs the meal again. |

Creates one diary entry per template line (input source SAVED_MEAL). The template is not changed. Together with choosing the template and confirming, this keeps re-logging within three user actions (US-08, NFR-07).

**Parameters**

| Name | In | Type | Required | Constraints | Description |
|---|---|---|---|---|---|
| `savedMealId` | path | string (uuid) | yes |  |  |

**Request body** (required) — `LogSavedMealRequest`

| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| `date` | `LocalDate` | yes |  |  |
| `mealSlot` | `MealSlot` | yes | one of `BREAKFAST`, `LUNCH`, `DINNER`, `SNACK` | Codes from `meal_slot`. |
| `planItemKey` | string or null (uuid) | no |  |  |

Request example

```json
{
  "date": "2026-11-17",
  "mealSlot": "BREAKFAST",
  "planItemKey": null
}
```

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 201 | Entries created. | array of `FoodEntry` (`application/json`) |
| 400 | Malformed JSON, invalid parameter or field values (nothing was changed). | `Problem` (`application/problem+json`) |
| 401 | Missing, invalid or expired token, or the account no longer exists. | `Problem` (`application/problem+json`) |
| 403 | The resource belongs to another user (US-30, NFR-08). No data of that user is disclosed. OPEN (AQ-2): 403 for another user's existing resource vs 404 - the database routines report "not found or not owned" as VY404, so the API must check ownership first; to be confirmed. | `Problem` (`application/problem+json`) |
| 404 | The resource does not exist. | `Problem` (`application/problem+json`) |

Response 201

```json
[
  {
    "entryId": "3c4d5e6f-7081-4923-a4b5-c6d7e8f90a12",
    "date": "2026-11-17",
    "mealSlot": "BREAKFAST",
    "inputSource": "SAVED_MEAL",
    "foodId": 12,
    "savedMealId": "e0000000-0000-4000-8000-000000000001",
    "planItemKey": null,
    "foodName": "Eggs",
    "portionG": 120.0,
    "kcal": 171.6,
    "proteinG": 15.1,
    "fatG": 11.4,
    "carbsG": 0.8,
    "createdAt": "2026-11-17T07:00:00Z",
    "updatedAt": "2026-11-17T07:00:00Z"
  },
  {
    "entryId": "4d5e6f70-8192-4a34-b5c6-d7e8f90a1b23",
    "date": "2026-11-17",
    "mealSlot": "BREAKFAST",
    "inputSource": "SAVED_MEAL",
    "foodId": 13,
    "savedMealId": "e0000000-0000-4000-8000-000000000001",
    "planItemKey": null,
    "foodName": "Whole wheat bread",
    "portionG": 60.0,
    "kcal": 148.2,
    "proteinG": 7.8,
    "fatG": 2.0,
    "carbsG": 24.6,
    "createdAt": "2026-11-17T07:00:00Z",
    "updatedAt": "2026-11-17T07:00:00Z"
  }
]
```

Response 400

```json
{
  "type": "https://veya.example/problems/validation-failed",
  "title": "Request is not valid",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "detail": "One or more fields are invalid.",
  "instance": "/api/v1/me/profile",
  "errors": [
    {
      "field": "energyTargetKcal",
      "message": "must be between 500 and 10000",
      "rejectedValue": 100
    }
  ]
}
```

Response 401

```json
{
  "type": "https://veya.example/problems/authentication-required",
  "title": "Authentication required",
  "status": 401,
  "code": "AUTHENTICATION_REQUIRED",
  "detail": "A valid bearer token is required.",
  "instance": "/api/v1/me"
}
```

Response 403

```json
{
  "type": "https://veya.example/problems/access-denied",
  "title": "Access denied",
  "status": 403,
  "code": "ACCESS_DENIED",
  "detail": "You cannot access this resource.",
  "instance": "/api/v1/plans/5e0c7f4a-1b2d-4c3e-9f8a-7b6c5d4e3f21"
}
```

Response 404

```json
{
  "type": "https://veya.example/problems/resource-not-found",
  "title": "Not found",
  "status": 404,
  "code": "RESOURCE_NOT_FOUND",
  "detail": "The requested resource does not exist.",
  "instance": "/api/v1/plans/00000000-0000-4000-8000-000000000000"
}
```

### `GET /api/v1/days/{date}/nutrition`

**Consumed, target and remaining nutrition of a day** — operationId `getNutritionSummary`

| | |
|---|---|
| Status | **IMPLEMENTED** |
| Module | `nutrition` |
| BA stories | US-07 |
| Acceptance criteria | US-07: given saved records and a daily goal, when the summary is opened, then calories and macros match persisted confirmed entries and remaining targets. |
| Contract source | PROPOSED |
| Authentication | JWT bearer token |
| Authorization / ownership | Authenticated user; own data only. |

Totals of confirmed entries compared with the profile targets (view `v_daily_nutrition_summary`). Without a profile the targets and remaining values are null.

**Parameters**

| Name | In | Type | Required | Constraints | Description |
|---|---|---|---|---|---|
| `date` | path | string (date) | yes |  | Local plan date (`YYYY-MM-DD`). |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 200 | Nutrition summary. | `NutritionSummary` (`application/json`) |
| 400 | Malformed JSON, invalid parameter or field values (nothing was changed). | `Problem` (`application/problem+json`) |
| 401 | Missing, invalid or expired token, or the account no longer exists. | `Problem` (`application/problem+json`) |

Response 200

```json
{
  "date": "2026-11-16",
  "consumed": {
    "kcal": 1306.3,
    "proteinG": 49.2,
    "fatG": 47.4,
    "carbsG": 182.0
  },
  "target": {
    "kcal": 1900,
    "proteinG": 110.0,
    "fatG": 60.0,
    "carbsG": 220.0
  },
  "remaining": {
    "kcal": 593.7,
    "proteinG": 60.8,
    "fatG": 12.6,
    "carbsG": 38.0
  },
  "entryCount": 4
}
```

Response 400

```json
{
  "type": "https://veya.example/problems/validation-failed",
  "title": "Request is not valid",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "detail": "One or more fields are invalid.",
  "instance": "/api/v1/me/profile",
  "errors": [
    {
      "field": "energyTargetKcal",
      "message": "must be between 500 and 10000",
      "rejectedValue": 100
    }
  ]
}
```

Response 401

```json
{
  "type": "https://veya.example/problems/authentication-required",
  "title": "Authentication required",
  "status": 401,
  "code": "AUTHENTICATION_REQUIRED",
  "detail": "A valid bearer token is required.",
  "instance": "/api/v1/me"
}
```

## Daily plans and plan items

### `POST /api/v1/meal-plan-suggestions`

**Suggest a daily meal plan (nothing is saved)** — operationId `suggestMealPlan`

| | |
|---|---|
| Status | **IMPLEMENTED** |
| Module | `planning` |
| BA stories | US-09 |
| Acceptance criteria | US-09: given available candidate meals and goal/constraints, when requested, then the proposed menu excludes prohibited items and meets configured acceptable target ranges. |
| Contract source | PROPOSED |
| Authentication | JWT bearer token |
| Authorization / ownership | Authenticated user; uses the caller's profile and constraints. |
| Pagination / idempotency / notes | Safe (no side effects) although POST is used for the request body. Deterministic for the same data. The combination of distinct meals closest to the energy target is accepted only within +/- veya.planning.target-tolerance-pct (default 20 %, AQ-14 PROPOSED). plannedTime is not suggested (omitted). |
| Open questions | AQ-14 acceptable target range of a suggested menu |

Returns a proposed menu that excludes prohibited items and fits the targets. Nothing is stored; the user
reviews it and saves the plan explicitly with `POST /api/v1/plans`.

**Request body** (required) — `MealPlanSuggestionRequest`

| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| `date` | `LocalDate` | yes |  |  |
| `mealSlots` | array of `MealSlot` | no | min items `1`; unique | Default: BREAKFAST, LUNCH, DINNER. |

Request example

```json
{
  "date": "2026-11-17",
  "mealSlots": [
    "BREAKFAST",
    "LUNCH",
    "DINNER"
  ]
}
```

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 200 | Suggested items and totals. | `MealPlanSuggestion` (`application/json`) |
| 400 | Malformed JSON, invalid parameter or field values (nothing was changed). | `Problem` (`application/problem+json`) |
| 401 | Missing, invalid or expired token, or the account no longer exists. | `Problem` (`application/problem+json`) |
| 422 | No combination of candidate meals satisfies the exclusions and targets. | `Problem` (`application/problem+json`) |

Response 200

```json
{
  "date": "2026-11-17",
  "items": [
    {
      "itemType": "MEAL",
      "mealSlot": "BREAKFAST",
      "plannedTime": "08:00",
      "mealId": 1,
      "mealName": "Oatmeal with banana",
      "kcal": 426.4,
      "proteinG": 17.2,
      "fatG": 9.4,
      "carbsG": 72.0
    },
    {
      "itemType": "MEAL",
      "mealSlot": "LUNCH",
      "plannedTime": "13:00",
      "mealId": 4,
      "mealName": "Chicken rice bowl",
      "kcal": 629.9,
      "proteinG": 54.7,
      "fatG": 16.4,
      "carbsG": 63.0
    },
    {
      "itemType": "MEAL",
      "mealSlot": "DINNER",
      "plannedTime": "19:00",
      "mealId": 9,
      "mealName": "Buckwheat with chicken",
      "kcal": 447.0,
      "proteinG": 46.6,
      "fatG": 5.8,
      "carbsG": 52.9
    }
  ],
  "totals": {
    "kcal": 1503.3,
    "proteinG": 118.5,
    "fatG": 31.6,
    "carbsG": 187.9
  },
  "target": {
    "kcal": 1900,
    "proteinG": 110.0,
    "fatG": 60.0,
    "carbsG": 220.0
  }
}
```

Response 400

```json
{
  "type": "https://veya.example/problems/validation-failed",
  "title": "Request is not valid",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "detail": "One or more fields are invalid.",
  "instance": "/api/v1/me/profile",
  "errors": [
    {
      "field": "energyTargetKcal",
      "message": "must be between 500 and 10000",
      "rejectedValue": 100
    }
  ]
}
```

Response 401

```json
{
  "type": "https://veya.example/problems/authentication-required",
  "title": "Authentication required",
  "status": 401,
  "code": "AUTHENTICATION_REQUIRED",
  "detail": "A valid bearer token is required.",
  "instance": "/api/v1/me"
}
```

Response 422

```json
{
  "type": "https://veya.example/problems/no-feasible-meal-plan",
  "title": "No feasible meal plan",
  "status": 422,
  "code": "NO_FEASIBLE_MEAL_PLAN",
  "detail": "No candidate meals satisfy your exclusions for DINNER.",
  "instance": "/api/v1/meal-plan-suggestions",
  "blockingConstraints": [
    {
      "type": "FOOD_EXCLUSION",
      "message": "All dinner candidates contain excluded ingredients.",
      "itemKey": null
    }
  ]
}
```

### `GET /api/v1/plans`

**Active plan of a date** — operationId `getPlanByDate`

| | |
|---|---|
| Status | **IMPLEMENTED** |
| Module | `planning` |
| BA stories | US-09, US-12, US-20 |
| Acceptance criteria | US-12: scheduled items appear in the active day. |
| Contract source | PROPOSED |
| Authentication | JWT bearer token |
| Authorization / ownership | Authenticated user; returns only the caller's plan. |

**Parameters**

| Name | In | Type | Required | Constraints | Description |
|---|---|---|---|---|---|
| `date` | query | string (date) | yes |  | Local plan date (`YYYY-MM-DD`). |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 200 | Active version of the plan. | `Plan` (`application/json`) |
| 400 | Malformed JSON, invalid parameter or field values (nothing was changed). | `Problem` (`application/problem+json`) |
| 401 | Missing, invalid or expired token, or the account no longer exists. | `Problem` (`application/problem+json`) |
| 404 | No plan for this date. | `Problem` (`application/problem+json`) |

Response 200 - Plan version 1 (Anna, 2026-11-16 before the deviation)

```json
{
  "planId": "5e0c7f4a-1b2d-4c3e-9f8a-7b6c5d4e3f21",
  "date": "2026-11-16",
  "activeVersion": 1,
  "changeReason": "INITIAL_PLAN",
  "versionCreatedAt": "2026-11-15T20:00:00Z",
  "recoveryRequired": false,
  "items": [
    {
      "itemKey": "b0000000-0000-4000-8000-000000000201",
      "itemType": "MEAL",
      "mealSlot": "BREAKFAST",
      "plannedTime": "08:00",
      "meal": {
        "mealId": 1,
        "name": "Oatmeal with banana"
      },
      "exercise": null,
      "plannedKcal": 426.4,
      "plannedProteinG": 17.2,
      "plannedFatG": 9.4,
      "plannedCarbsG": 72.0,
      "durationMin": null,
      "status": "PLANNED"
    },
    {
      "itemKey": "b0000000-0000-4000-8000-000000000202",
      "itemType": "MEAL",
      "mealSlot": "LUNCH",
      "plannedTime": "13:00",
      "meal": {
        "mealId": 4,
        "name": "Chicken rice bowl"
      },
      "exercise": null,
      "plannedKcal": 629.9,
      "plannedProteinG": 54.7,
      "plannedFatG": 16.4,
      "plannedCarbsG": 63.0,
      "durationMin": null,
      "status": "PLANNED"
    },
    {
      "itemKey": "b0000000-0000-4000-8000-000000000204",
      "itemType": "WORKOUT",
      "mealSlot": null,
      "plannedTime": "18:00",
      "meal": null,
      "exercise": {
        "exerciseId": 1,
        "name": "Running, 10 km/h"
      },
      "plannedKcal": null,
      "plannedProteinG": null,
      "plannedFatG": null,
      "plannedCarbsG": null,
      "durationMin": 30,
      "status": "PLANNED"
    },
    {
      "itemKey": "b0000000-0000-4000-8000-000000000203",
      "itemType": "MEAL",
      "mealSlot": "DINNER",
      "plannedTime": "19:30",
      "meal": {
        "mealId": 7,
        "name": "Salmon with potatoes"
      },
      "exercise": null,
      "plannedKcal": 560.5,
      "plannedProteinG": 40.7,
      "plannedFatG": 19.2,
      "plannedCarbsG": 56.9,
      "durationMin": null,
      "status": "PLANNED"
    }
  ]
}
```

Response 400

```json
{
  "type": "https://veya.example/problems/validation-failed",
  "title": "Request is not valid",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "detail": "One or more fields are invalid.",
  "instance": "/api/v1/me/profile",
  "errors": [
    {
      "field": "energyTargetKcal",
      "message": "must be between 500 and 10000",
      "rejectedValue": 100
    }
  ]
}
```

Response 401

```json
{
  "type": "https://veya.example/problems/authentication-required",
  "title": "Authentication required",
  "status": 401,
  "code": "AUTHENTICATION_REQUIRED",
  "detail": "A valid bearer token is required.",
  "instance": "/api/v1/me"
}
```

Response 404

```json
{
  "type": "https://veya.example/problems/plan-not-found",
  "title": "Plan not found",
  "status": 404,
  "code": "PLAN_NOT_FOUND",
  "detail": "There is no plan for 2026-11-20.",
  "instance": "/api/v1/plans?date=2026-11-20"
}
```

### `POST /api/v1/plans`

**Save the plan of a day (version 1)** — operationId `createPlan`

| | |
|---|---|
| Status | **IMPLEMENTED** |
| Module | `planning` |
| BA stories | US-09, US-12 |
| Acceptance criteria | US-09: the proposed menu excludes prohibited items. US-12: given valid exercises and time, when the plan is saved, then scheduled items appear in the active day and summary/recovery calculations. |
| Contract source | PROPOSED |
| Authentication | JWT bearer token |
| Authorization / ownership | Authenticated user; the plan is created for the caller. |
| Pagination / idempotency / notes | One plan per user and date: a second POST for the same date returns 409. Adding items to an existing plan is not defined by the BA (AQ-3). |
| Open questions | AQ-3 adding items to an existing plan |

Explicitly saves the user's plan for a date: meals (from a suggestion or chosen manually) and scheduled
workouts with duration (US-12). Planned nutrition is taken from the catalogue at save time (snapshot).
Creates version 1 (`create_plan_version`, reason INITIAL_PLAN). Items that break hard constraints
(exclusions, available time, equipment) are rejected with 422.

**Request body** (required) — `CreatePlanRequest`

| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| `date` | `LocalDate` | yes |  |  |
| `items` | array of `PlanItemInput` | yes | min items `1`; max items `20` |  |

Request example: Three meals and a workout

```json
{
  "date": "2026-11-17",
  "items": [
    {
      "itemType": "MEAL",
      "mealSlot": "BREAKFAST",
      "plannedTime": "08:00",
      "mealId": 1
    },
    {
      "itemType": "MEAL",
      "mealSlot": "LUNCH",
      "plannedTime": "13:00",
      "mealId": 4
    },
    {
      "itemType": "MEAL",
      "mealSlot": "DINNER",
      "plannedTime": "19:30",
      "mealId": 7
    },
    {
      "itemType": "WORKOUT",
      "plannedTime": "18:00",
      "exerciseId": 1,
      "durationMin": 30
    }
  ]
}
```

Request example: Workout of 0 minutes (400)

```json
{
  "date": "2026-11-18",
  "items": [
    {
      "itemType": "WORKOUT",
      "plannedTime": "18:00",
      "exerciseId": 1,
      "durationMin": 0
    }
  ]
}
```

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 201 | Plan saved as version 1. | `Plan` (`application/json`) |
| 400 | Malformed JSON, invalid parameter or field values (nothing was changed). | `Problem` (`application/problem+json`) |
| 401 | Missing, invalid or expired token, or the account no longer exists. | `Problem` (`application/problem+json`) |
| 409 | A plan for this date already exists. | `Problem` (`application/problem+json`) |
| 422 | The requested item breaks a hard constraint (exclusion, ingredient, time or equipment). | `Problem` (`application/problem+json`) |

Response 201 - Plan version 1 (Anna, 2026-11-16 before the deviation)

```json
{
  "planId": "5e0c7f4a-1b2d-4c3e-9f8a-7b6c5d4e3f21",
  "date": "2026-11-16",
  "activeVersion": 1,
  "changeReason": "INITIAL_PLAN",
  "versionCreatedAt": "2026-11-15T20:00:00Z",
  "recoveryRequired": false,
  "items": [
    {
      "itemKey": "b0000000-0000-4000-8000-000000000201",
      "itemType": "MEAL",
      "mealSlot": "BREAKFAST",
      "plannedTime": "08:00",
      "meal": {
        "mealId": 1,
        "name": "Oatmeal with banana"
      },
      "exercise": null,
      "plannedKcal": 426.4,
      "plannedProteinG": 17.2,
      "plannedFatG": 9.4,
      "plannedCarbsG": 72.0,
      "durationMin": null,
      "status": "PLANNED"
    },
    {
      "itemKey": "b0000000-0000-4000-8000-000000000202",
      "itemType": "MEAL",
      "mealSlot": "LUNCH",
      "plannedTime": "13:00",
      "meal": {
        "mealId": 4,
        "name": "Chicken rice bowl"
      },
      "exercise": null,
      "plannedKcal": 629.9,
      "plannedProteinG": 54.7,
      "plannedFatG": 16.4,
      "plannedCarbsG": 63.0,
      "durationMin": null,
      "status": "PLANNED"
    },
    {
      "itemKey": "b0000000-0000-4000-8000-000000000204",
      "itemType": "WORKOUT",
      "mealSlot": null,
      "plannedTime": "18:00",
      "meal": null,
      "exercise": {
        "exerciseId": 1,
        "name": "Running, 10 km/h"
      },
      "plannedKcal": null,
      "plannedProteinG": null,
      "plannedFatG": null,
      "plannedCarbsG": null,
      "durationMin": 30,
      "status": "PLANNED"
    },
    {
      "itemKey": "b0000000-0000-4000-8000-000000000203",
      "itemType": "MEAL",
      "mealSlot": "DINNER",
      "plannedTime": "19:30",
      "meal": {
        "mealId": 7,
        "name": "Salmon with potatoes"
      },
      "exercise": null,
      "plannedKcal": 560.5,
      "plannedProteinG": 40.7,
      "plannedFatG": 19.2,
      "plannedCarbsG": 56.9,
      "durationMin": null,
      "status": "PLANNED"
    }
  ]
}
```

Response 400

```json
{
  "type": "https://veya.example/problems/validation-failed",
  "title": "Request is not valid",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "detail": "One or more fields are invalid.",
  "instance": "/api/v1/me/profile",
  "errors": [
    {
      "field": "energyTargetKcal",
      "message": "must be between 500 and 10000",
      "rejectedValue": 100
    }
  ]
}
```

Response 401

```json
{
  "type": "https://veya.example/problems/authentication-required",
  "title": "Authentication required",
  "status": 401,
  "code": "AUTHENTICATION_REQUIRED",
  "detail": "A valid bearer token is required.",
  "instance": "/api/v1/me"
}
```

Response 409

```json
{
  "type": "https://veya.example/problems/plan-already-exists",
  "title": "Plan already exists",
  "status": 409,
  "code": "PLAN_ALREADY_EXISTS",
  "detail": "A plan for 2026-11-17 already exists.",
  "instance": "/api/v1/plans"
}
```

Response 422

```json
{
  "type": "https://veya.example/problems/hard-constraint-violation",
  "title": "Hard constraint violated",
  "status": 422,
  "code": "HARD_CONSTRAINT_VIOLATION",
  "detail": "Peanut butter toast contains PEANUT, which is excluded in your profile.",
  "instance": "/api/v1/plans",
  "blockingConstraints": [
    {
      "type": "FOOD_EXCLUSION",
      "message": "Contains PEANUT.",
      "itemKey": null
    }
  ]
}
```

### `GET /api/v1/plans/{planId}`

**Active version of a plan** — operationId `getPlan`

| | |
|---|---|
| Status | **IMPLEMENTED** |
| Module | `planning` |
| BA stories | US-09, US-12, US-18, US-20 |
| Acceptance criteria | US-18: the response exposes the active version used as baseVersion. |
| Contract source | PROPOSED |
| Authentication | JWT bearer token |
| Authorization / ownership | Authenticated user; another user's plan -> 403. |
| Pagination / idempotency / notes | Earlier versions are not browsable in the MVP (US-21 is Should). |

**Parameters**

| Name | In | Type | Required | Constraints | Description |
|---|---|---|---|---|---|
| `planId` | path | string (uuid) | yes |  |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 200 | Active version. | `Plan` (`application/json`) |
| 401 | Missing, invalid or expired token, or the account no longer exists. | `Problem` (`application/problem+json`) |
| 403 | The resource belongs to another user (US-30, NFR-08). No data of that user is disclosed. OPEN (AQ-2): 403 for another user's existing resource vs 404 - the database routines report "not found or not owned" as VY404, so the API must check ownership first; to be confirmed. | `Problem` (`application/problem+json`) |
| 404 | The resource does not exist. | `Problem` (`application/problem+json`) |

Response 200 - Plan version 1 (Anna, 2026-11-16 before the deviation)

```json
{
  "planId": "5e0c7f4a-1b2d-4c3e-9f8a-7b6c5d4e3f21",
  "date": "2026-11-16",
  "activeVersion": 1,
  "changeReason": "INITIAL_PLAN",
  "versionCreatedAt": "2026-11-15T20:00:00Z",
  "recoveryRequired": false,
  "items": [
    {
      "itemKey": "b0000000-0000-4000-8000-000000000201",
      "itemType": "MEAL",
      "mealSlot": "BREAKFAST",
      "plannedTime": "08:00",
      "meal": {
        "mealId": 1,
        "name": "Oatmeal with banana"
      },
      "exercise": null,
      "plannedKcal": 426.4,
      "plannedProteinG": 17.2,
      "plannedFatG": 9.4,
      "plannedCarbsG": 72.0,
      "durationMin": null,
      "status": "PLANNED"
    },
    {
      "itemKey": "b0000000-0000-4000-8000-000000000202",
      "itemType": "MEAL",
      "mealSlot": "LUNCH",
      "plannedTime": "13:00",
      "meal": {
        "mealId": 4,
        "name": "Chicken rice bowl"
      },
      "exercise": null,
      "plannedKcal": 629.9,
      "plannedProteinG": 54.7,
      "plannedFatG": 16.4,
      "plannedCarbsG": 63.0,
      "durationMin": null,
      "status": "PLANNED"
    },
    {
      "itemKey": "b0000000-0000-4000-8000-000000000204",
      "itemType": "WORKOUT",
      "mealSlot": null,
      "plannedTime": "18:00",
      "meal": null,
      "exercise": {
        "exerciseId": 1,
        "name": "Running, 10 km/h"
      },
      "plannedKcal": null,
      "plannedProteinG": null,
      "plannedFatG": null,
      "plannedCarbsG": null,
      "durationMin": 30,
      "status": "PLANNED"
    },
    {
      "itemKey": "b0000000-0000-4000-8000-000000000203",
      "itemType": "MEAL",
      "mealSlot": "DINNER",
      "plannedTime": "19:30",
      "meal": {
        "mealId": 7,
        "name": "Salmon with potatoes"
      },
      "exercise": null,
      "plannedKcal": 560.5,
      "plannedProteinG": 40.7,
      "plannedFatG": 19.2,
      "plannedCarbsG": 56.9,
      "durationMin": null,
      "status": "PLANNED"
    }
  ]
}
```

Response 401

```json
{
  "type": "https://veya.example/problems/authentication-required",
  "title": "Authentication required",
  "status": 401,
  "code": "AUTHENTICATION_REQUIRED",
  "detail": "A valid bearer token is required.",
  "instance": "/api/v1/me"
}
```

Response 403

```json
{
  "type": "https://veya.example/problems/access-denied",
  "title": "Access denied",
  "status": 403,
  "code": "ACCESS_DENIED",
  "detail": "You cannot access this resource.",
  "instance": "/api/v1/plans/5e0c7f4a-1b2d-4c3e-9f8a-7b6c5d4e3f21"
}
```

Response 404

```json
{
  "type": "https://veya.example/problems/resource-not-found",
  "title": "Not found",
  "status": 404,
  "code": "RESOURCE_NOT_FOUND",
  "detail": "The requested resource does not exist.",
  "instance": "/api/v1/plans/00000000-0000-4000-8000-000000000000"
}
```

### `POST /api/v1/meal-options`

**Feasible alternatives for a planned meal (nothing is saved)** — operationId `getMealOptions`

| | |
|---|---|
| Status | **IMPLEMENTED** |
| Module | `planning` |
| BA stories | US-10 |
| Acceptance criteria | US-10: given an active planned meal, when replacement is requested, then only hard-constraint-compliant candidates and reasons are displayed. |
| Contract source | BA Table 2.8 (POST /api/v1/meal-options, 200 candidates/reasons, 422 if no feasible alternative); request body PROPOSED |
| Authentication | JWT bearer token |
| Authorization / ownership | Authenticated user; the plan must be the caller's (otherwise 403). |
| Pagination / idempotency / notes | Safe (no side effects). At most 100 candidates are considered (NFR-02). |

Returns only hard-constraint-compliant candidates (exclusions, ingredients, time) for a PLANNED meal of the
active version, each with reasons. Nothing changes until the user confirms a replacement.

**Request body** (required) — `ReplacementOptionsRequest`

| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| `planId` | string (uuid) | yes |  |  |
| `itemKey` | string (uuid) | yes |  | A PLANNED item of the active version (MEAL for meal-options, WORKOUT for exercise-options). |

Request example: Alternatives for the planned dinner

```json
{
  "planId": "5e0c7f4a-1b2d-4c3e-9f8a-7b6c5d4e3f21",
  "itemKey": "b0000000-0000-4000-8000-000000000203"
}
```

Request example: Every candidate breaks a constraint (422)

```json
{
  "planId": "5e0c7f4a-1b2d-4c3e-9f8a-7b6c5d4e3f21",
  "itemKey": "b0000000-0000-4000-8000-000000000203"
}
```

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 200 | Feasible alternatives with reasons. | `MealOptions` (`application/json`) |
| 400 | Malformed JSON, invalid parameter or field values (nothing was changed). | `Problem` (`application/problem+json`) |
| 401 | Missing, invalid or expired token, or the account no longer exists. | `Problem` (`application/problem+json`) |
| 403 | The resource belongs to another user (US-30, NFR-08). No data of that user is disclosed. OPEN (AQ-2): 403 for another user's existing resource vs 404 - the database routines report "not found or not owned" as VY404, so the API must check ownership first; to be confirmed. | `Problem` (`application/problem+json`) |
| 404 | The resource does not exist. | `Problem` (`application/problem+json`) |
| 409 | The item is already completed/skipped/missed and is frozen (US-16). | `Problem` (`application/problem+json`) |
| 422 | No compliant alternative exists (US-10 / US-14); the old plan stays active. | `Problem` (`application/problem+json`) |

Response 200

```json
{
  "planId": "5e0c7f4a-1b2d-4c3e-9f8a-7b6c5d4e3f21",
  "itemKey": "b0000000-0000-4000-8000-000000000203",
  "baseVersion": 1,
  "options": [
    {
      "mealId": 8,
      "name": "Tofu vegetable stir-fry",
      "kcal": 565.4,
      "proteinG": 25.4,
      "fatG": 20.6,
      "carbsG": 72.3,
      "prepTimeMin": 20,
      "reasons": [
        {
          "category": "INGREDIENT",
          "text": "Contains no fish, which is excluded today."
        },
        {
          "category": "TIME",
          "text": "Takes 20 minutes; 40 minutes are available."
        }
      ]
    },
    {
      "mealId": 6,
      "name": "Lentil soup",
      "kcal": 475.9,
      "proteinG": 26.4,
      "fatG": 11.4,
      "carbsG": 69.9,
      "prepTimeMin": 35,
      "reasons": [
        {
          "category": "NUTRITION",
          "text": "85 kcal lighter than the planned dinner."
        }
      ]
    }
  ]
}
```

Response 400

```json
{
  "type": "https://veya.example/problems/validation-failed",
  "title": "Request is not valid",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "detail": "One or more fields are invalid.",
  "instance": "/api/v1/me/profile",
  "errors": [
    {
      "field": "energyTargetKcal",
      "message": "must be between 500 and 10000",
      "rejectedValue": 100
    }
  ]
}
```

Response 401

```json
{
  "type": "https://veya.example/problems/authentication-required",
  "title": "Authentication required",
  "status": 401,
  "code": "AUTHENTICATION_REQUIRED",
  "detail": "A valid bearer token is required.",
  "instance": "/api/v1/me"
}
```

Response 403

```json
{
  "type": "https://veya.example/problems/access-denied",
  "title": "Access denied",
  "status": 403,
  "code": "ACCESS_DENIED",
  "detail": "You cannot access this resource.",
  "instance": "/api/v1/plans/5e0c7f4a-1b2d-4c3e-9f8a-7b6c5d4e3f21"
}
```

Response 404

```json
{
  "type": "https://veya.example/problems/resource-not-found",
  "title": "Not found",
  "status": 404,
  "code": "RESOURCE_NOT_FOUND",
  "detail": "The requested resource does not exist.",
  "instance": "/api/v1/plans/00000000-0000-4000-8000-000000000000"
}
```

Response 409

```json
{
  "type": "https://veya.example/problems/item-frozen",
  "title": "Item cannot be changed",
  "status": 409,
  "code": "ITEM_FROZEN",
  "detail": "Completed or recorded items are frozen and cannot be replaced.",
  "instance": "/api/v1/meal-options"
}
```

Response 422

```json
{
  "type": "https://veya.example/problems/no-feasible-alternative",
  "title": "No feasible alternative",
  "status": 422,
  "code": "NO_FEASIBLE_ALTERNATIVE",
  "detail": "No meal fits the remaining time and exclusions.",
  "instance": "/api/v1/meal-options",
  "blockingConstraints": [
    {
      "type": "TIME",
      "message": "Only 10 minutes are available for dinner preparation.",
      "itemKey": "b0000000-0000-4000-8000-000000000203"
    }
  ]
}
```

### `POST /api/v1/exercise-options`

**Feasible alternatives for a planned workout (nothing is saved)** — operationId `getExerciseOptions`

| | |
|---|---|
| Status | **IMPLEMENTED** |
| Module | `activity` |
| BA stories | US-14 |
| Acceptance criteria | US-14: given changed time/equipment, when requested, then compliant alternatives or a reason for none are shown. |
| Contract source | PROPOSED (mirrors BA meal-options) |
| Authentication | JWT bearer token |
| Authorization / ownership | Authenticated user; the plan must be the caller's. |
| Pagination / idempotency / notes | Safe (no side effects). At most 100 candidates (NFR-02). |

Compliant exercises for the available time and equipment, with reasons, or a reason why none exists (422).

**Request body** (required) — `ReplacementOptionsRequest`

| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| `planId` | string (uuid) | yes |  |  |
| `itemKey` | string (uuid) | yes |  | A PLANNED item of the active version (MEAL for meal-options, WORKOUT for exercise-options). |

Request example

```json
{
  "planId": "5e0c7f4a-1b2d-4c3e-9f8a-7b6c5d4e3f21",
  "itemKey": "b0000000-0000-4000-8000-000000000204"
}
```

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 200 | Feasible alternatives with reasons. | `ExerciseOptions` (`application/json`) |
| 400 | Malformed JSON, invalid parameter or field values (nothing was changed). | `Problem` (`application/problem+json`) |
| 401 | Missing, invalid or expired token, or the account no longer exists. | `Problem` (`application/problem+json`) |
| 403 | The resource belongs to another user (US-30, NFR-08). No data of that user is disclosed. OPEN (AQ-2): 403 for another user's existing resource vs 404 - the database routines report "not found or not owned" as VY404, so the API must check ownership first; to be confirmed. | `Problem` (`application/problem+json`) |
| 404 | The resource does not exist. | `Problem` (`application/problem+json`) |
| 409 | The item is already completed/skipped/missed and is frozen (US-16). | `Problem` (`application/problem+json`) |
| 422 | No compliant alternative exists (US-10 / US-14); the old plan stays active. | `Problem` (`application/problem+json`) |

Response 200

```json
{
  "planId": "5e0c7f4a-1b2d-4c3e-9f8a-7b6c5d4e3f21",
  "itemKey": "b0000000-0000-4000-8000-000000000204",
  "baseVersion": 1,
  "options": [
    {
      "exerciseId": 2,
      "name": "Brisk walking",
      "durationMin": 25,
      "reasons": [
        {
          "category": "EQUIPMENT",
          "text": "Needs no equipment."
        },
        {
          "category": "TIME",
          "text": "Fits the 30 available minutes."
        }
      ]
    }
  ]
}
```

Response 400

```json
{
  "type": "https://veya.example/problems/validation-failed",
  "title": "Request is not valid",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "detail": "One or more fields are invalid.",
  "instance": "/api/v1/me/profile",
  "errors": [
    {
      "field": "energyTargetKcal",
      "message": "must be between 500 and 10000",
      "rejectedValue": 100
    }
  ]
}
```

Response 401

```json
{
  "type": "https://veya.example/problems/authentication-required",
  "title": "Authentication required",
  "status": 401,
  "code": "AUTHENTICATION_REQUIRED",
  "detail": "A valid bearer token is required.",
  "instance": "/api/v1/me"
}
```

Response 403

```json
{
  "type": "https://veya.example/problems/access-denied",
  "title": "Access denied",
  "status": 403,
  "code": "ACCESS_DENIED",
  "detail": "You cannot access this resource.",
  "instance": "/api/v1/plans/5e0c7f4a-1b2d-4c3e-9f8a-7b6c5d4e3f21"
}
```

Response 404

```json
{
  "type": "https://veya.example/problems/resource-not-found",
  "title": "Not found",
  "status": 404,
  "code": "RESOURCE_NOT_FOUND",
  "detail": "The requested resource does not exist.",
  "instance": "/api/v1/plans/00000000-0000-4000-8000-000000000000"
}
```

Response 409

```json
{
  "type": "https://veya.example/problems/item-frozen",
  "title": "Item cannot be changed",
  "status": 409,
  "code": "ITEM_FROZEN",
  "detail": "Completed or recorded items are frozen and cannot be replaced.",
  "instance": "/api/v1/meal-options"
}
```

Response 422

```json
{
  "type": "https://veya.example/problems/no-feasible-alternative",
  "title": "No feasible alternative",
  "status": 422,
  "code": "NO_FEASIBLE_ALTERNATIVE",
  "detail": "No meal fits the remaining time and exclusions.",
  "instance": "/api/v1/meal-options",
  "blockingConstraints": [
    {
      "type": "TIME",
      "message": "Only 10 minutes are available for dinner preparation.",
      "itemKey": "b0000000-0000-4000-8000-000000000203"
    }
  ]
}
```

### `POST /api/v1/plans/{planId}/items/{itemKey}/replacement`

**Confirm a meal or exercise replacement (creates the next version)** — operationId `replacePlanItem`

| | |
|---|---|
| Status | **IMPLEMENTED** |
| Module | `planning` |
| BA stories | US-10, US-14 |
| Acceptance criteria | US-10: confirmation creates the next plan version and updates totals. US-14: confirmed choice updates the plan and history. |
| Contract source | PROPOSED (BA requires confirmation and a new version; path not given) |
| Authentication | JWT bearer token |
| Authorization / ownership | Authenticated user; another user's plan -> 403. |
| Pagination / idempotency / notes | Optimistic locking by `baseVersion`: a stale version returns 409 and nothing changes (NFR-10). Not idempotent - repeat with the old baseVersion returns 409. |

Confirms the option the user chose. The server checks ownership, that `baseVersion` equals the active
version and that the item is still PLANNED, then creates the next immutable version
(MEAL_REPLACEMENT / EXERCISE_REPLACEMENT) in one transaction. History keeps the earlier version (UC-02).

**Parameters**

| Name | In | Type | Required | Constraints | Description |
|---|---|---|---|---|---|
| `planId` | path | string (uuid) | yes |  |  |
| `itemKey` | path | string (uuid) | yes |  | Logical identity of a plan item; stays the same across versions. |

**Request body** (required) — `ReplacementRequest`

| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| `baseVersion` | integer | yes | >= `1` |  |
| `mealId` | integer | no | >= `1` |  |
| `exerciseId` | integer | no | >= `1` |  |
| `durationMin` | integer | no | >= `1`; <= `600` |  |

Request example: Replace a meal

```json
{
  "baseVersion": 1,
  "mealId": 8
}
```

Request example: Outdated baseVersion (409)

```json
{
  "baseVersion": 1,
  "mealId": 6
}
```

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 200 | New active version. | `Plan` (`application/json`) |
| 400 | Malformed JSON, invalid parameter or field values (nothing was changed). | `Problem` (`application/problem+json`) |
| 401 | Missing, invalid or expired token, or the account no longer exists. | `Problem` (`application/problem+json`) |
| 403 | The resource belongs to another user (US-30, NFR-08). No data of that user is disclosed. OPEN (AQ-2): 403 for another user's existing resource vs 404 - the database routines report "not found or not owned" as VY404, so the API must check ownership first; to be confirmed. | `Problem` (`application/problem+json`) |
| 404 | The resource does not exist. | `Problem` (`application/problem+json`) |
| 409 | baseVersion is not the active plan version (NFR-10). Nothing changed; reload the plan and retry. | `Problem` (`application/problem+json`) |
| 422 | The requested item breaks a hard constraint (exclusion, ingredient, time or equipment). | `Problem` (`application/problem+json`) |

Response 200 - Version 2 after replacing the dinner (MEAL_REPLACEMENT)

```json
{
  "planId": "5e0c7f4a-1b2d-4c3e-9f8a-7b6c5d4e3f21",
  "date": "2026-11-16",
  "activeVersion": 2,
  "changeReason": "MEAL_REPLACEMENT",
  "versionCreatedAt": "2026-11-16T15:00:00Z",
  "recoveryRequired": false,
  "items": [
    {
      "itemKey": "b0000000-0000-4000-8000-000000000201",
      "itemType": "MEAL",
      "mealSlot": "BREAKFAST",
      "plannedTime": "08:00",
      "meal": {
        "mealId": 1,
        "name": "Oatmeal with banana"
      },
      "exercise": null,
      "plannedKcal": 426.4,
      "plannedProteinG": 17.2,
      "plannedFatG": 9.4,
      "plannedCarbsG": 72.0,
      "durationMin": null,
      "status": "COMPLETED"
    },
    {
      "itemKey": "b0000000-0000-4000-8000-000000000202",
      "itemType": "MEAL",
      "mealSlot": "LUNCH",
      "plannedTime": "13:00",
      "meal": {
        "mealId": 4,
        "name": "Chicken rice bowl"
      },
      "exercise": null,
      "plannedKcal": 629.9,
      "plannedProteinG": 54.7,
      "plannedFatG": 16.4,
      "plannedCarbsG": 63.0,
      "durationMin": null,
      "status": "PLANNED"
    },
    {
      "itemKey": "b0000000-0000-4000-8000-000000000204",
      "itemType": "WORKOUT",
      "mealSlot": null,
      "plannedTime": "18:00",
      "meal": null,
      "exercise": {
        "exerciseId": 1,
        "name": "Running, 10 km/h"
      },
      "plannedKcal": null,
      "plannedProteinG": null,
      "plannedFatG": null,
      "plannedCarbsG": null,
      "durationMin": 30,
      "status": "PLANNED"
    },
    {
      "itemKey": "b0000000-0000-4000-8000-000000000203",
      "itemType": "MEAL",
      "mealSlot": "DINNER",
      "plannedTime": "19:30",
      "meal": {
        "mealId": 8,
        "name": "Tofu vegetable stir-fry"
      },
      "exercise": null,
      "plannedKcal": 565.4,
      "plannedProteinG": 25.4,
      "plannedFatG": 20.6,
      "plannedCarbsG": 72.3,
      "durationMin": null,
      "status": "PLANNED"
    }
  ]
}
```

Response 400

```json
{
  "type": "https://veya.example/problems/validation-failed",
  "title": "Request is not valid",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "detail": "One or more fields are invalid.",
  "instance": "/api/v1/me/profile",
  "errors": [
    {
      "field": "energyTargetKcal",
      "message": "must be between 500 and 10000",
      "rejectedValue": 100
    }
  ]
}
```

Response 401

```json
{
  "type": "https://veya.example/problems/authentication-required",
  "title": "Authentication required",
  "status": 401,
  "code": "AUTHENTICATION_REQUIRED",
  "detail": "A valid bearer token is required.",
  "instance": "/api/v1/me"
}
```

Response 403

```json
{
  "type": "https://veya.example/problems/access-denied",
  "title": "Access denied",
  "status": 403,
  "code": "ACCESS_DENIED",
  "detail": "You cannot access this resource.",
  "instance": "/api/v1/plans/5e0c7f4a-1b2d-4c3e-9f8a-7b6c5d4e3f21"
}
```

Response 404

```json
{
  "type": "https://veya.example/problems/resource-not-found",
  "title": "Not found",
  "status": 404,
  "code": "RESOURCE_NOT_FOUND",
  "detail": "The requested resource does not exist.",
  "instance": "/api/v1/plans/00000000-0000-4000-8000-000000000000"
}
```

Response 409

```json
{
  "type": "https://veya.example/problems/stale-plan-version",
  "title": "Plan version is outdated",
  "status": 409,
  "code": "STALE_PLAN_VERSION",
  "detail": "Stale plan version: expected 1, current 2.",
  "instance": "/api/v1/plans/5e0c7f4a-1b2d-4c3e-9f8a-7b6c5d4e3f21/items/b0000000-0000-4000-8000-000000000203/replacement",
  "currentVersion": 2
}
```

Response 422

```json
{
  "type": "https://veya.example/problems/hard-constraint-violation",
  "title": "Hard constraint violated",
  "status": 422,
  "code": "HARD_CONSTRAINT_VIOLATION",
  "detail": "Peanut butter toast contains PEANUT, which is excluded in your profile.",
  "instance": "/api/v1/plans",
  "blockingConstraints": [
    {
      "type": "FOOD_EXCLUSION",
      "message": "Contains PEANUT.",
      "itemKey": null
    }
  ]
}
```

### `POST /api/v1/plans/{planId}/items/{itemKey}/status`

**Confirm, skip or mark a planned item as missed** — operationId `recordPlanItemStatus`

| | |
|---|---|
| Status | **IMPLEMENTED** |
| Module | `planning` |
| BA stories | US-13, US-15 |
| Acceptance criteria | US-15: a confirmed meal differing by >= 200 kcal or a planned meal/workout marked missed/skipped sets recoveryRequired; smaller differences do not. |
| Contract source | PROPOSED |
| Authentication | JWT bearer token |
| Authorization / ownership | Authenticated user; another user's plan -> 403. |
| Pagination / idempotency / notes | Not idempotent - a second status for the same item returns 409 STATUS_ALREADY_RECORDED. |

Records what happened to a planned item of the active version: a MEAL can be COMPLETED (confirmed - the
linked diary entries are compared with the plan, 200 kcal rule), SKIPPED or MISSED; a WORKOUT can be
MISSED here (completed/skipped/changed workouts are recorded with `POST /api/v1/activity-entries`).
SKIPPED/MISSED or a deviation >= 200 kcal sets `recoveryRequired`. A status can be recorded only once.

**Parameters**

| Name | In | Type | Required | Constraints | Description |
|---|---|---|---|---|---|
| `planId` | path | string (uuid) | yes |  |  |
| `itemKey` | path | string (uuid) | yes |  | Logical identity of a plan item; stays the same across versions. |

**Request body** (required) — `ItemStatusRequest`

| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| `status` | string enum | yes | one of `COMPLETED`, `SKIPPED`, `MISSED` | MEAL: COMPLETED/SKIPPED/MISSED. WORKOUT: MISSED (other workout outcomes via activity-entries). |

Request example

```json
{
  "status": "COMPLETED"
}
```

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 200 | Status recorded; deviation result. | `ItemStatusResult` (`application/json`) |
| 400 | Malformed JSON, invalid parameter or field values (nothing was changed). | `Problem` (`application/problem+json`) |
| 401 | Missing, invalid or expired token, or the account no longer exists. | `Problem` (`application/problem+json`) |
| 403 | The resource belongs to another user (US-30, NFR-08). No data of that user is disclosed. OPEN (AQ-2): 403 for another user's existing resource vs 404 - the database routines report "not found or not owned" as VY404, so the API must check ownership first; to be confirmed. | `Problem` (`application/problem+json`) |
| 404 | The resource does not exist. | `Problem` (`application/problem+json`) |
| 409 | Status already recorded, or the item belongs to a superseded version. | `Problem` (`application/problem+json`) |

Response 200

```json
{
  "planId": "5e0c7f4a-1b2d-4c3e-9f8a-7b6c5d4e3f21",
  "item": {
    "itemKey": "b0000000-0000-4000-8000-000000000202",
    "itemType": "MEAL",
    "mealSlot": "LUNCH",
    "plannedTime": "13:00",
    "meal": {
      "mealId": 4,
      "name": "Chicken rice bowl"
    },
    "exercise": null,
    "plannedKcal": 629.9,
    "plannedProteinG": 54.7,
    "plannedFatG": 16.4,
    "plannedCarbsG": 63.0,
    "durationMin": null,
    "status": "COMPLETED"
  },
  "recoveryRequired": true,
  "newDeviations": [
    {
      "deviationId": "1a2b3c4d-5e6f-4708-9a1b-2c3d4e5f6a7b",
      "type": "KCAL_DIFFERENCE",
      "itemKey": "b0000000-0000-4000-8000-000000000202",
      "kcalDifference": 250.0,
      "details": null,
      "detectedAt": "2026-11-16T12:45:00Z"
    }
  ]
}
```

Response 400

```json
{
  "type": "https://veya.example/problems/validation-failed",
  "title": "Request is not valid",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "detail": "One or more fields are invalid.",
  "instance": "/api/v1/me/profile",
  "errors": [
    {
      "field": "energyTargetKcal",
      "message": "must be between 500 and 10000",
      "rejectedValue": 100
    }
  ]
}
```

Response 401

```json
{
  "type": "https://veya.example/problems/authentication-required",
  "title": "Authentication required",
  "status": 401,
  "code": "AUTHENTICATION_REQUIRED",
  "detail": "A valid bearer token is required.",
  "instance": "/api/v1/me"
}
```

Response 403

```json
{
  "type": "https://veya.example/problems/access-denied",
  "title": "Access denied",
  "status": 403,
  "code": "ACCESS_DENIED",
  "detail": "You cannot access this resource.",
  "instance": "/api/v1/plans/5e0c7f4a-1b2d-4c3e-9f8a-7b6c5d4e3f21"
}
```

Response 404

```json
{
  "type": "https://veya.example/problems/resource-not-found",
  "title": "Not found",
  "status": 404,
  "code": "RESOURCE_NOT_FOUND",
  "detail": "The requested resource does not exist.",
  "instance": "/api/v1/plans/00000000-0000-4000-8000-000000000000"
}
```

Response 409

```json
{
  "type": "https://veya.example/problems/status-already-recorded",
  "title": "Status already recorded",
  "status": 409,
  "code": "STATUS_ALREADY_RECORDED",
  "detail": "The status of this item is already COMPLETED.",
  "instance": "/api/v1/plans/5e0c7f4a-1b2d-4c3e-9f8a-7b6c5d4e3f21/items/b0000000-0000-4000-8000-000000000202/status"
}
```

### `GET /api/v1/plans/{planId}/deviations`

**Open deviations of a plan (why recovery is needed)** — operationId `listOpenDeviations`

| | |
|---|---|
| Status | **IMPLEMENTED** |
| Module | `recovery` |
| BA stories | US-15 |
| Acceptance criteria | US-15: I know when recovery is needed. |
| Contract source | PROPOSED |
| Authentication | JWT bearer token |
| Authorization / ownership | Authenticated user; another user's plan -> 403. |

Shows why `recoveryRequired` is set ("Reason for review" in Fig. B.10). Resolved deviations are not listed.

**Parameters**

| Name | In | Type | Required | Constraints | Description |
|---|---|---|---|---|---|
| `planId` | path | string (uuid) | yes |  |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 200 | Open deviations, oldest first. | array of `Deviation` (`application/json`) |
| 401 | Missing, invalid or expired token, or the account no longer exists. | `Problem` (`application/problem+json`) |
| 403 | The resource belongs to another user (US-30, NFR-08). No data of that user is disclosed. OPEN (AQ-2): 403 for another user's existing resource vs 404 - the database routines report "not found or not owned" as VY404, so the API must check ownership first; to be confirmed. | `Problem` (`application/problem+json`) |
| 404 | The resource does not exist. | `Problem` (`application/problem+json`) |

Response 200

```json
[
  {
    "deviationId": "1a2b3c4d-5e6f-4708-9a1b-2c3d4e5f6a7b",
    "type": "KCAL_DIFFERENCE",
    "itemKey": "b0000000-0000-4000-8000-000000000202",
    "kcalDifference": 250.0,
    "details": null,
    "detectedAt": "2026-11-16T12:45:00Z"
  },
  {
    "deviationId": "2b3c4d5e-6f70-4819-a2b3-c4d5e6f70819",
    "type": "ITEM_SKIPPED",
    "itemKey": "b0000000-0000-4000-8000-000000000204",
    "kcalDifference": null,
    "details": null,
    "detectedAt": "2026-11-16T17:30:00Z"
  }
]
```

Response 401

```json
{
  "type": "https://veya.example/problems/authentication-required",
  "title": "Authentication required",
  "status": 401,
  "code": "AUTHENTICATION_REQUIRED",
  "detail": "A valid bearer token is required.",
  "instance": "/api/v1/me"
}
```

Response 403

```json
{
  "type": "https://veya.example/problems/access-denied",
  "title": "Access denied",
  "status": 403,
  "code": "ACCESS_DENIED",
  "detail": "You cannot access this resource.",
  "instance": "/api/v1/plans/5e0c7f4a-1b2d-4c3e-9f8a-7b6c5d4e3f21"
}
```

Response 404

```json
{
  "type": "https://veya.example/problems/resource-not-found",
  "title": "Not found",
  "status": 404,
  "code": "RESOURCE_NOT_FOUND",
  "detail": "The requested resource does not exist.",
  "instance": "/api/v1/plans/00000000-0000-4000-8000-000000000000"
}
```

## Physical activity and workouts

### `GET /api/v1/exercises`

**Exercises for scheduling a workout** — operationId `listExercises`

| | |
|---|---|
| Status | **IMPLEMENTED** |
| Module | `activity` |
| BA stories | US-12 |
| Acceptance criteria | US-12: given valid exercises and time, the plan can be saved. |
| Contract source | PROPOSED |
| Authentication | JWT bearer token |
| Authorization / ownership | Authenticated user. |
| Pagination / idempotency / notes | `limit` 1-100, default 50; sorted by name. |

Basic exercise selection with required equipment. Filtering by muscle/difficulty/equipment (US-11) is Should and not part of the MVP.

**Parameters**

| Name | In | Type | Required | Constraints | Description |
|---|---|---|---|---|---|
| `q` | query | string | no | min length `2`; max length `100` | Name contains (case-insensitive), 2-100 characters. |
| `limit` | query | integer | no | >= `1`; <= `100`; default `50` |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 200 | Exercises. | array of `Exercise` (`application/json`) |
| 400 | Malformed JSON, invalid parameter or field values (nothing was changed). | `Problem` (`application/problem+json`) |
| 401 | Missing, invalid or expired token, or the account no longer exists. | `Problem` (`application/problem+json`) |

Response 200

```json
[
  {
    "exerciseId": 3,
    "name": "Bodyweight circuit",
    "metValue": 8.0,
    "requiredEquipment": []
  },
  {
    "exerciseId": 5,
    "name": "Dumbbell strength training",
    "metValue": 5.0,
    "requiredEquipment": [
      "DUMBBELLS"
    ]
  }
]
```

Response 400

```json
{
  "type": "https://veya.example/problems/validation-failed",
  "title": "Request is not valid",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "detail": "One or more fields are invalid.",
  "instance": "/api/v1/me/profile",
  "errors": [
    {
      "field": "energyTargetKcal",
      "message": "must be between 500 and 10000",
      "rejectedValue": 100
    }
  ]
}
```

Response 401

```json
{
  "type": "https://veya.example/problems/authentication-required",
  "title": "Authentication required",
  "status": 401,
  "code": "AUTHENTICATION_REQUIRED",
  "detail": "A valid bearer token is required.",
  "instance": "/api/v1/me"
}
```

### `GET /api/v1/activity-entries`

**Workout records of a day** — operationId `listActivityEntries`

| | |
|---|---|
| Status | **IMPLEMENTED** |
| Module | `activity` |
| BA stories | US-13, US-20 |
| Acceptance criteria | US-13: the actual event persists. |
| Contract source | PROPOSED |
| Authentication | JWT bearer token |
| Authorization / ownership | Authenticated user; own records only. |

**Parameters**

| Name | In | Type | Required | Constraints | Description |
|---|---|---|---|---|---|
| `date` | query | string (date) | yes |  | Local plan date (`YYYY-MM-DD`). |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 200 | Records of the day. | array of `ActivityEntry` (`application/json`) |
| 400 | Malformed JSON, invalid parameter or field values (nothing was changed). | `Problem` (`application/problem+json`) |
| 401 | Missing, invalid or expired token, or the account no longer exists. | `Problem` (`application/problem+json`) |

Response 200

```json
[
  {
    "entryId": "5e6f7081-92a3-4b45-c6d7-e8f90a1b2c34",
    "date": "2026-11-16",
    "exerciseId": 1,
    "exerciseName": "Running, 10 km/h",
    "planItemKey": "b0000000-0000-4000-8000-000000000204",
    "status": "SKIPPED",
    "durationMin": null,
    "createdAt": "2026-11-16T17:30:00Z"
  }
]
```

Response 400

```json
{
  "type": "https://veya.example/problems/validation-failed",
  "title": "Request is not valid",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "detail": "One or more fields are invalid.",
  "instance": "/api/v1/me/profile",
  "errors": [
    {
      "field": "energyTargetKcal",
      "message": "must be between 500 and 10000",
      "rejectedValue": 100
    }
  ]
}
```

Response 401

```json
{
  "type": "https://veya.example/problems/authentication-required",
  "title": "Authentication required",
  "status": 401,
  "code": "AUTHENTICATION_REQUIRED",
  "detail": "A valid bearer token is required.",
  "instance": "/api/v1/me"
}
```

### `POST /api/v1/activity-entries`

**Record a workout as completed, skipped or changed** — operationId `createActivityEntry`

| | |
|---|---|
| Status | **IMPLEMENTED** |
| Module | `activity` |
| BA stories | US-13, US-15 |
| Acceptance criteria | US-13: given a planned activity, when its status is recorded, then the actual event persists and deviation detection evaluates the changed plan state. |
| Contract source | PROPOSED |
| Authentication | JWT bearer token |
| Authorization / ownership | Authenticated user; `planItemKey` must be the caller's (otherwise 403). |
| Pagination / idempotency / notes | Not idempotent; a second record for the same planned workout returns 409. |

Stores the actual workout event. If it refers to a planned WORKOUT (`planItemKey`), the planned item's status
is set to the same value; SKIPPED sets `recoveryRequired` (US-15). One record per planned workout.

**Request body** (required) — `ActivityEntryRequest`

| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| `date` | `LocalDate` | yes |  |  |
| `exerciseId` | integer | yes | >= `1` |  |
| `planItemKey` | string or null (uuid) | no |  | Planned WORKOUT of the active version on the same date. |
| `status` | string enum | yes | one of `COMPLETED`, `SKIPPED`, `CHANGED` |  |
| `durationMin` | integer or null | no | >= `1`; <= `600` | Required for COMPLETED/CHANGED; must be null for SKIPPED. |

Request example: Planned run skipped

```json
{
  "date": "2026-11-16",
  "exerciseId": 1,
  "planItemKey": "b0000000-0000-4000-8000-000000000204",
  "status": "SKIPPED",
  "durationMin": null
}
```

Request example: COMPLETED without duration (400)

```json
{
  "date": "2026-11-16",
  "exerciseId": 2,
  "planItemKey": null,
  "status": "COMPLETED",
  "durationMin": null
}
```

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 201 | Record stored. | `ActivityEntryResult` (`application/json`) |
| 400 | Malformed JSON, invalid parameter or field values (nothing was changed). | `Problem` (`application/problem+json`) |
| 401 | Missing, invalid or expired token, or the account no longer exists. | `Problem` (`application/problem+json`) |
| 403 | The resource belongs to another user (US-30, NFR-08). No data of that user is disclosed. OPEN (AQ-2): 403 for another user's existing resource vs 404 - the database routines report "not found or not owned" as VY404, so the API must check ownership first; to be confirmed. | `Problem` (`application/problem+json`) |
| 409 | A status/record for this planned item already exists. | `Problem` (`application/problem+json`) |

Response 201

```json
{
  "entry": {
    "entryId": "5e6f7081-92a3-4b45-c6d7-e8f90a1b2c34",
    "date": "2026-11-16",
    "exerciseId": 1,
    "exerciseName": "Running, 10 km/h",
    "planItemKey": "b0000000-0000-4000-8000-000000000204",
    "status": "SKIPPED",
    "durationMin": null,
    "createdAt": "2026-11-16T17:30:00Z"
  },
  "recoveryRequired": true
}
```

Response 400

```json
{
  "type": "https://veya.example/problems/validation-failed",
  "title": "Request is not valid",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "detail": "One or more fields are invalid.",
  "instance": "/api/v1/me/profile",
  "errors": [
    {
      "field": "energyTargetKcal",
      "message": "must be between 500 and 10000",
      "rejectedValue": 100
    }
  ]
}
```

Response 401

```json
{
  "type": "https://veya.example/problems/authentication-required",
  "title": "Authentication required",
  "status": 401,
  "code": "AUTHENTICATION_REQUIRED",
  "detail": "A valid bearer token is required.",
  "instance": "/api/v1/me"
}
```

Response 403

```json
{
  "type": "https://veya.example/problems/access-denied",
  "title": "Access denied",
  "status": 403,
  "code": "ACCESS_DENIED",
  "detail": "You cannot access this resource.",
  "instance": "/api/v1/plans/5e0c7f4a-1b2d-4c3e-9f8a-7b6c5d4e3f21"
}
```

Response 409

```json
{
  "type": "https://veya.example/problems/status-already-recorded",
  "title": "Status already recorded",
  "status": 409,
  "code": "STATUS_ALREADY_RECORDED",
  "detail": "Status of this workout is already recorded.",
  "instance": "/api/v1/activity-entries"
}
```

## Dynamic Plan Recovery

### `POST /api/v1/plans/{planId}/recovery-proposals`

**Generate a recovery proposal for the remaining day** — operationId `createRecoveryProposal`

| | |
|---|---|
| Status | **IMPLEMENTED** |
| Module | `recovery` |
| BA stories | US-16, US-17, US-19 |
| Acceptance criteria | US-16: given a recoveryRequired plan, when requested, then completed items are frozen and feasible future combinations are returned with totals or an explicit no-solution response. US-17: each changed item has a human-readable reason. US-19: no feasible combination -> blockingConstraints and 422, no new plan version. |
| Contract source | BA Table 2.8 (201 proposalId, baseVersion, changed items, totals and reasons; 422 if impossible) + Q1 (201 Created) |
| Authentication | JWT bearer token |
| Authorization / ownership | Authenticated user; another user's plan -> 403. |
| Pagination / idempotency / notes | Not idempotent - each call stores a new proposal. Response time target p95 <= 2 s (NFR-02), visible <= 3 s (KPI-4). The request body is optional. Engine: frozen items kept; remaining kcal target = profile target - confirmed diary kcal; meals ranked by |remaining target - planned kcal| + 50 kcal per changed meal; infeasible workouts replaced or removed; a skipped/missed workout gets one feasible ADD (plannedTime null). 422 RECOVERY_NOT_FEASIBLE also when no feasible change improves the remaining plan. Totals are recomputed from current confirmed records when the proposal is read. |

UC-01 steps 4-5. The engine loads the active version, freezes completed items, derives remaining targets
from confirmed facts, filters candidates by hard constraints and ranks feasible combinations. The proposal
is stored with `baseVersion` and a human-readable reason per changed item. **The active plan does not
change** - it changes only after an explicit decision. No feasible combination -> 422 with
`blockingConstraints` and no new version (US-19).

**Parameters**

| Name | In | Type | Required | Constraints | Description |
|---|---|---|---|---|---|
| `planId` | path | string (uuid) | yes |  |  |

**Request body** (optional) — `CreateRecoveryProposalRequest`

| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| `baseVersion` | integer | no | >= `1` | Optional - the version the user is looking at; if it is not the active version, 409. |

Request example

```json
{
  "baseVersion": 1
}
```

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 201 | Proposal stored; the plan is unchanged. | `RecoveryProposal` (`application/json`) |
| 400 | Malformed JSON, invalid parameter or field values (nothing was changed). | `Problem` (`application/problem+json`) |
| 401 | Missing, invalid or expired token, or the account no longer exists. | `Problem` (`application/problem+json`) |
| 403 | The resource belongs to another user (US-30, NFR-08). No data of that user is disclosed. OPEN (AQ-2): 403 for another user's existing resource vs 404 - the database routines report "not found or not owned" as VY404, so the API must check ownership first; to be confirmed. | `Problem` (`application/problem+json`) |
| 404 | The resource does not exist. | `Problem` (`application/problem+json`) |
| 409 | Recovery is not required for this plan, or the client's baseVersion is stale. | `Problem` (`application/problem+json`) |
| 422 | No feasible combination (US-19). No proposal and no new plan version are created. | `Problem` (`application/problem+json`) |

Response 201 - Proposal for Anna's 2026-11-16 (lunch +250 kcal, run skipped, fish unavailable)

```json
{
  "proposalId": "c0000000-0000-4000-8000-000000000001",
  "planId": "5e0c7f4a-1b2d-4c3e-9f8a-7b6c5d4e3f21",
  "baseVersion": 1,
  "status": "PENDING",
  "createdAt": "2026-11-16T18:00:00Z",
  "changes": [
    {
      "changeId": "f1a2b3c4-d5e6-4f70-8192-a3b4c5d6e7f8",
      "changeNo": 1,
      "changeType": "REPLACE",
      "targetItemKey": "b0000000-0000-4000-8000-000000000203",
      "before": {
        "itemKey": "b0000000-0000-4000-8000-000000000203",
        "itemType": "MEAL",
        "mealSlot": "DINNER",
        "plannedTime": "19:30",
        "meal": {
          "mealId": 7,
          "name": "Salmon with potatoes"
        },
        "exercise": null,
        "plannedKcal": 560.5,
        "plannedProteinG": 40.7,
        "plannedFatG": 19.2,
        "plannedCarbsG": 56.9,
        "durationMin": null,
        "status": "PLANNED"
      },
      "after": {
        "itemType": "MEAL",
        "mealSlot": "DINNER",
        "plannedTime": "19:30",
        "meal": {
          "mealId": 8,
          "name": "Tofu vegetable stir-fry"
        },
        "exercise": null,
        "plannedKcal": 565.4,
        "plannedProteinG": 25.4,
        "plannedFatG": 20.6,
        "plannedCarbsG": 72.3,
        "durationMin": null
      },
      "reason": {
        "category": "INGREDIENT",
        "text": "Salmon is unavailable today; the tofu stir-fry contains no fish, takes 20 minutes and keeps the remaining protein target."
      }
    },
    {
      "changeId": "a2b3c4d5-e6f7-4081-92a3-b4c5d6e7f809",
      "changeNo": 2,
      "changeType": "ADD",
      "targetItemKey": null,
      "before": null,
      "after": {
        "itemType": "WORKOUT",
        "mealSlot": null,
        "plannedTime": "20:30",
        "meal": null,
        "exercise": {
          "exerciseId": 3,
          "name": "Bodyweight circuit"
        },
        "plannedKcal": null,
        "plannedProteinG": null,
        "plannedFatG": null,
        "plannedCarbsG": null,
        "durationMin": 20
      },
      "reason": {
        "category": "TIME",
        "text": "The 30-minute run was skipped; a 20-minute bodyweight circuit fits the 40 available minutes and needs no equipment."
      }
    }
  ],
  "totals": {
    "targetKcal": 1900,
    "consumedKcal": 1306.3,
    "remainingPlannedKcalBefore": 560.5,
    "remainingPlannedKcalAfter": 565.4,
    "forecastKcal": 1871.7
  }
}
```

Response 400

```json
{
  "type": "https://veya.example/problems/validation-failed",
  "title": "Request is not valid",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "detail": "One or more fields are invalid.",
  "instance": "/api/v1/me/profile",
  "errors": [
    {
      "field": "energyTargetKcal",
      "message": "must be between 500 and 10000",
      "rejectedValue": 100
    }
  ]
}
```

Response 401

```json
{
  "type": "https://veya.example/problems/authentication-required",
  "title": "Authentication required",
  "status": 401,
  "code": "AUTHENTICATION_REQUIRED",
  "detail": "A valid bearer token is required.",
  "instance": "/api/v1/me"
}
```

Response 403

```json
{
  "type": "https://veya.example/problems/access-denied",
  "title": "Access denied",
  "status": 403,
  "code": "ACCESS_DENIED",
  "detail": "You cannot access this resource.",
  "instance": "/api/v1/plans/5e0c7f4a-1b2d-4c3e-9f8a-7b6c5d4e3f21"
}
```

Response 404

```json
{
  "type": "https://veya.example/problems/resource-not-found",
  "title": "Not found",
  "status": 404,
  "code": "RESOURCE_NOT_FOUND",
  "detail": "The requested resource does not exist.",
  "instance": "/api/v1/plans/00000000-0000-4000-8000-000000000000"
}
```

Response 409 - notRequired

```json
{
  "type": "https://veya.example/problems/recovery-not-required",
  "title": "Recovery not required",
  "status": 409,
  "code": "RECOVERY_NOT_REQUIRED",
  "detail": "No significant deviation is open for this plan.",
  "instance": "/api/v1/plans/5e0c7f4a-1b2d-4c3e-9f8a-7b6c5d4e3f21/recovery-proposals"
}
```

Response 409 - stale

```json
{
  "type": "https://veya.example/problems/stale-plan-version",
  "title": "Plan version is outdated",
  "status": 409,
  "code": "STALE_PLAN_VERSION",
  "detail": "Stale baseVersion 1: active plan version is 2.",
  "instance": "/api/v1/plans/5e0c7f4a-1b2d-4c3e-9f8a-7b6c5d4e3f21/recovery-proposals",
  "currentVersion": 2
}
```

Response 422

```json
{
  "type": "https://veya.example/problems/recovery-not-feasible",
  "title": "No feasible recovery",
  "status": 422,
  "code": "RECOVERY_NOT_FEASIBLE",
  "detail": "No combination of remaining meals and workouts satisfies your constraints.",
  "instance": "/api/v1/plans/5e0c7f4a-1b2d-4c3e-9f8a-7b6c5d4e3f21/recovery-proposals",
  "blockingConstraints": [
    {
      "type": "TIME",
      "message": "Only 10 minutes are available; every remaining item needs at least 20.",
      "itemKey": null
    },
    {
      "type": "FOOD_EXCLUSION",
      "message": "All dinner candidates contain fish or peanut.",
      "itemKey": "b0000000-0000-4000-8000-000000000203"
    }
  ]
}
```

### `GET /api/v1/recovery-proposals/{proposalId}`

**A recovery proposal with its current status** — operationId `getRecoveryProposal`

| | |
|---|---|
| Status | **IMPLEMENTED** |
| Module | `recovery` |
| BA stories | US-16, US-17, US-18 |
| Acceptance criteria | US-17: given a proposal, when displayed, then each changed item has a human-readable reason. |
| Contract source | PROPOSED |
| Authentication | JWT bearer token |
| Authorization / ownership | Authenticated user; another user's proposal -> 403. |

Status is derived: PENDING, STALE (the plan moved to a newer version), ACCEPTED, EDITED or REJECTED.

**Parameters**

| Name | In | Type | Required | Constraints | Description |
|---|---|---|---|---|---|
| `proposalId` | path | string (uuid) | yes |  |  |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 200 | Proposal. | `RecoveryProposal` (`application/json`) |
| 401 | Missing, invalid or expired token, or the account no longer exists. | `Problem` (`application/problem+json`) |
| 403 | The resource belongs to another user (US-30, NFR-08). No data of that user is disclosed. OPEN (AQ-2): 403 for another user's existing resource vs 404 - the database routines report "not found or not owned" as VY404, so the API must check ownership first; to be confirmed. | `Problem` (`application/problem+json`) |
| 404 | The resource does not exist. | `Problem` (`application/problem+json`) |

Response 200 - Proposal for Anna's 2026-11-16 (lunch +250 kcal, run skipped, fish unavailable)

```json
{
  "proposalId": "c0000000-0000-4000-8000-000000000001",
  "planId": "5e0c7f4a-1b2d-4c3e-9f8a-7b6c5d4e3f21",
  "baseVersion": 1,
  "status": "PENDING",
  "createdAt": "2026-11-16T18:00:00Z",
  "changes": [
    {
      "changeId": "f1a2b3c4-d5e6-4f70-8192-a3b4c5d6e7f8",
      "changeNo": 1,
      "changeType": "REPLACE",
      "targetItemKey": "b0000000-0000-4000-8000-000000000203",
      "before": {
        "itemKey": "b0000000-0000-4000-8000-000000000203",
        "itemType": "MEAL",
        "mealSlot": "DINNER",
        "plannedTime": "19:30",
        "meal": {
          "mealId": 7,
          "name": "Salmon with potatoes"
        },
        "exercise": null,
        "plannedKcal": 560.5,
        "plannedProteinG": 40.7,
        "plannedFatG": 19.2,
        "plannedCarbsG": 56.9,
        "durationMin": null,
        "status": "PLANNED"
      },
      "after": {
        "itemType": "MEAL",
        "mealSlot": "DINNER",
        "plannedTime": "19:30",
        "meal": {
          "mealId": 8,
          "name": "Tofu vegetable stir-fry"
        },
        "exercise": null,
        "plannedKcal": 565.4,
        "plannedProteinG": 25.4,
        "plannedFatG": 20.6,
        "plannedCarbsG": 72.3,
        "durationMin": null
      },
      "reason": {
        "category": "INGREDIENT",
        "text": "Salmon is unavailable today; the tofu stir-fry contains no fish, takes 20 minutes and keeps the remaining protein target."
      }
    },
    {
      "changeId": "a2b3c4d5-e6f7-4081-92a3-b4c5d6e7f809",
      "changeNo": 2,
      "changeType": "ADD",
      "targetItemKey": null,
      "before": null,
      "after": {
        "itemType": "WORKOUT",
        "mealSlot": null,
        "plannedTime": "20:30",
        "meal": null,
        "exercise": {
          "exerciseId": 3,
          "name": "Bodyweight circuit"
        },
        "plannedKcal": null,
        "plannedProteinG": null,
        "plannedFatG": null,
        "plannedCarbsG": null,
        "durationMin": 20
      },
      "reason": {
        "category": "TIME",
        "text": "The 30-minute run was skipped; a 20-minute bodyweight circuit fits the 40 available minutes and needs no equipment."
      }
    }
  ],
  "totals": {
    "targetKcal": 1900,
    "consumedKcal": 1306.3,
    "remainingPlannedKcalBefore": 560.5,
    "remainingPlannedKcalAfter": 565.4,
    "forecastKcal": 1871.7
  }
}
```

Response 401

```json
{
  "type": "https://veya.example/problems/authentication-required",
  "title": "Authentication required",
  "status": 401,
  "code": "AUTHENTICATION_REQUIRED",
  "detail": "A valid bearer token is required.",
  "instance": "/api/v1/me"
}
```

Response 403

```json
{
  "type": "https://veya.example/problems/access-denied",
  "title": "Access denied",
  "status": 403,
  "code": "ACCESS_DENIED",
  "detail": "You cannot access this resource.",
  "instance": "/api/v1/plans/5e0c7f4a-1b2d-4c3e-9f8a-7b6c5d4e3f21"
}
```

Response 404

```json
{
  "type": "https://veya.example/problems/resource-not-found",
  "title": "Not found",
  "status": 404,
  "code": "RESOURCE_NOT_FOUND",
  "detail": "The requested resource does not exist.",
  "instance": "/api/v1/plans/00000000-0000-4000-8000-000000000000"
}
```

### `POST /api/v1/recovery-proposals/{proposalId}/decision`

**ACCEPT or REJECT a proposal (EDIT is BLOCKED by Q12)** — operationId `decideRecoveryProposal`

| | |
|---|---|
| Status | **PARTIAL** — EDIT variant BLOCKED (Q12) |
| Module | `recovery` |
| BA stories | US-18 |
| Acceptance criteria | US-18: given a proposal with baseVersion, when a decision is confirmed, then the server checks ownership and version and transactionally applies ACCEPT/EDIT; REJECT leaves active plan unchanged; stale version returns 409. |
| Contract source | BA Table 2.8 (200 active plan/version; 409 stale version; 400 invalid edit) |
| Authentication | JWT bearer token |
| Authorization / ownership | Authenticated user; another user's proposal -> 403. |
| Pagination / idempotency / notes | PARTIAL - ACCEPT and REJECT are implemented; EDIT returns 400 INVALID_EDIT until Q12 is decided. A proposal is decided once: a repeated decision returns 409 PROPOSAL_ALREADY_DECIDED. Concurrent decisions are serialized; exactly one wins (tested). |
| Open questions | Q12 meaning of EDIT (BLOCKED) |

Explicit user decision (US-18). The server checks ownership and that `baseVersion` equals both the proposal's
base version and the active plan version, then:

* **ACCEPT** - saves the decision **and** the new plan version in one transaction (NFR-09); open deviations are
  resolved and `recoveryRequired` becomes false;
* **REJECT** - records the decision; the active plan stays unchanged and `recoveryRequired` stays true;
* **EDIT** - **BLOCKED (Q12 OPEN)**. The database can currently apply a selected subset of the proposal's changes
  (`selectedChangeIds`), but the BA mockup (Fig. B.10) also shows editing the values of individual proposed
  items. The EDIT request schema is therefore not final and must not be implemented by the client yet.

Stale `baseVersion` -> 409 and nothing changes (NFR-10). Invalid decision or edit -> 400.

**Parameters**

| Name | In | Type | Required | Constraints | Description |
|---|---|---|---|---|---|
| `proposalId` | path | string (uuid) | yes |  |  |

**Request body** (required) — `DecisionRequest`

| Field | Type | Required | Constraints | Description |
|---|---|---|---|---|
| `decision` | string enum | yes | one of `ACCEPT`, `EDIT`, `REJECT` | EDIT is BLOCKED until Q12 is decided. |
| `baseVersion` | integer | yes | >= `1` |  |
| `selectedChangeIds` | array of string (uuid) | no | min items `1`; unique | PROVISIONAL, EDIT only (Q12 OPEN): changes of this proposal to apply. Must be absent for ACCEPT and REJECT. Editing values of proposed items is not specified yet. |

Request example: Accept the whole proposal

```json
{
  "decision": "ACCEPT",
  "baseVersion": 1
}
```

Request example: Reject - plan stays unchanged

```json
{
  "decision": "REJECT",
  "baseVersion": 1
}
```

Request example: Accept with an outdated base version (409)

```json
{
  "decision": "ACCEPT",
  "baseVersion": 1
}
```

Request example: Unknown decision (400)

```json
{
  "decision": "MAYBE",
  "baseVersion": 1
}
```

Request example: EDIT - provisional shape, BLOCKED by Q12

```json
{
  "decision": "EDIT",
  "baseVersion": 1,
  "selectedChangeIds": [
    "f1a2b3c4-d5e6-4f70-8192-a3b4c5d6e7f8"
  ]
}
```

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 200 | Decision recorded; the active plan (new version for ACCEPT, unchanged for REJECT). | `DecisionResult` (`application/json`) |
| 400 | Invalid decision or invalid edit. | `Problem` (`application/problem+json`) |
| 401 | Missing, invalid or expired token, or the account no longer exists. | `Problem` (`application/problem+json`) |
| 403 | The resource belongs to another user (US-30, NFR-08). No data of that user is disclosed. OPEN (AQ-2): 403 for another user's existing resource vs 404 - the database routines report "not found or not owned" as VY404, so the API must check ownership first; to be confirmed. | `Problem` (`application/problem+json`) |
| 404 | The resource does not exist. | `Problem` (`application/problem+json`) |
| 409 | Stale baseVersion, or the proposal was already decided. Nothing changed. | `Problem` (`application/problem+json`) |

Response 200 - accepted

```json
{
  "proposalId": "c0000000-0000-4000-8000-000000000001",
  "decision": "ACCEPT",
  "decidedAt": "2026-11-16T18:05:00Z",
  "plan": {
    "planId": "5e0c7f4a-1b2d-4c3e-9f8a-7b6c5d4e3f21",
    "date": "2026-11-16",
    "activeVersion": 2,
    "changeReason": "RECOVERY_ACCEPTED",
    "versionCreatedAt": "2026-11-16T18:05:00Z",
    "recoveryRequired": false,
    "items": [
      {
        "itemKey": "b0000000-0000-4000-8000-000000000201",
        "itemType": "MEAL",
        "mealSlot": "BREAKFAST",
        "plannedTime": "08:00",
        "meal": {
          "mealId": 1,
          "name": "Oatmeal with banana"
        },
        "exercise": null,
        "plannedKcal": 426.4,
        "plannedProteinG": 17.2,
        "plannedFatG": 9.4,
        "plannedCarbsG": 72.0,
        "durationMin": null,
        "status": "COMPLETED"
      },
      {
        "itemKey": "b0000000-0000-4000-8000-000000000202",
        "itemType": "MEAL",
        "mealSlot": "LUNCH",
        "plannedTime": "13:00",
        "meal": {
          "mealId": 4,
          "name": "Chicken rice bowl"
        },
        "exercise": null,
        "plannedKcal": 629.9,
        "plannedProteinG": 54.7,
        "plannedFatG": 16.4,
        "plannedCarbsG": 63.0,
        "durationMin": null,
        "status": "COMPLETED"
      },
      {
        "itemKey": "b0000000-0000-4000-8000-000000000204",
        "itemType": "WORKOUT",
        "mealSlot": null,
        "plannedTime": "18:00",
        "meal": null,
        "exercise": {
          "exerciseId": 1,
          "name": "Running, 10 km/h"
        },
        "plannedKcal": null,
        "plannedProteinG": null,
        "plannedFatG": null,
        "plannedCarbsG": null,
        "durationMin": 30,
        "status": "SKIPPED"
      },
      {
        "itemKey": "b0000000-0000-4000-8000-000000000203",
        "itemType": "MEAL",
        "mealSlot": "DINNER",
        "plannedTime": "19:30",
        "meal": {
          "mealId": 8,
          "name": "Tofu vegetable stir-fry"
        },
        "exercise": null,
        "plannedKcal": 565.4,
        "plannedProteinG": 25.4,
        "plannedFatG": 20.6,
        "plannedCarbsG": 72.3,
        "durationMin": null,
        "status": "PLANNED"
      },
      {
        "itemKey": "6c7d8e9f-a0b1-4c2d-8e3f-4a5b6c7d8e9f",
        "itemType": "WORKOUT",
        "mealSlot": null,
        "plannedTime": "20:30",
        "meal": null,
        "exercise": {
          "exerciseId": 3,
          "name": "Bodyweight circuit"
        },
        "plannedKcal": null,
        "plannedProteinG": null,
        "plannedFatG": null,
        "plannedCarbsG": null,
        "durationMin": 20,
        "status": "PLANNED"
      }
    ]
  }
}
```

Response 200 - rejected

```json
{
  "proposalId": "c0000000-0000-4000-8000-000000000003",
  "decision": "REJECT",
  "decidedAt": "2026-11-16T14:10:00Z",
  "plan": {
    "planId": "7d8e9fa0-b1c2-4d3e-8f4a-5b6c7d8e9fa0",
    "date": "2026-11-16",
    "activeVersion": 1,
    "changeReason": "INITIAL_PLAN",
    "versionCreatedAt": "2026-11-15T21:00:00Z",
    "recoveryRequired": true,
    "items": [
      {
        "itemKey": "b0000000-0000-4000-8000-000000000301",
        "itemType": "MEAL",
        "mealSlot": "BREAKFAST",
        "plannedTime": "07:30",
        "meal": {
          "mealId": 1,
          "name": "Oatmeal with banana"
        },
        "exercise": null,
        "plannedKcal": 426.4,
        "plannedProteinG": 17.2,
        "plannedFatG": 9.4,
        "plannedCarbsG": 72.0,
        "durationMin": null,
        "status": "COMPLETED"
      },
      {
        "itemKey": "b0000000-0000-4000-8000-000000000302",
        "itemType": "MEAL",
        "mealSlot": "LUNCH",
        "plannedTime": "12:30",
        "meal": {
          "mealId": 5,
          "name": "Pasta bolognese"
        },
        "exercise": null,
        "plannedKcal": 567.8,
        "plannedProteinG": 39.4,
        "plannedFatG": 13.8,
        "plannedCarbsG": 69.0,
        "durationMin": null,
        "status": "COMPLETED"
      },
      {
        "itemKey": "b0000000-0000-4000-8000-000000000303",
        "itemType": "WORKOUT",
        "mealSlot": null,
        "plannedTime": "17:00",
        "meal": null,
        "exercise": {
          "exerciseId": 5,
          "name": "Dumbbell strength training"
        },
        "plannedKcal": null,
        "plannedProteinG": null,
        "plannedFatG": null,
        "plannedCarbsG": null,
        "durationMin": 45,
        "status": "PLANNED"
      }
    ]
  }
}
```

Response 400 - invalidDecision

```json
{
  "type": "https://veya.example/problems/validation-failed",
  "title": "Request is not valid",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "detail": "One or more fields are invalid.",
  "instance": "/api/v1/recovery-proposals/c0000000-0000-4000-8000-000000000001/decision",
  "errors": [
    {
      "field": "decision",
      "message": "must be one of ACCEPT, EDIT, REJECT",
      "rejectedValue": "MAYBE"
    }
  ]
}
```

Response 400 - invalidEdit

```json
{
  "type": "https://veya.example/problems/invalid-edit",
  "title": "Invalid edit",
  "status": 400,
  "code": "INVALID_EDIT",
  "detail": "Select one or more distinct changes of this proposal.",
  "instance": "/api/v1/recovery-proposals/c0000000-0000-4000-8000-000000000001/decision"
}
```

Response 401

```json
{
  "type": "https://veya.example/problems/authentication-required",
  "title": "Authentication required",
  "status": 401,
  "code": "AUTHENTICATION_REQUIRED",
  "detail": "A valid bearer token is required.",
  "instance": "/api/v1/me"
}
```

Response 403

```json
{
  "type": "https://veya.example/problems/access-denied",
  "title": "Access denied",
  "status": 403,
  "code": "ACCESS_DENIED",
  "detail": "You cannot access this resource.",
  "instance": "/api/v1/plans/5e0c7f4a-1b2d-4c3e-9f8a-7b6c5d4e3f21"
}
```

Response 404

```json
{
  "type": "https://veya.example/problems/resource-not-found",
  "title": "Not found",
  "status": 404,
  "code": "RESOURCE_NOT_FOUND",
  "detail": "The requested resource does not exist.",
  "instance": "/api/v1/plans/00000000-0000-4000-8000-000000000000"
}
```

Response 409 - stale

```json
{
  "type": "https://veya.example/problems/stale-plan-version",
  "title": "Plan version is outdated",
  "status": 409,
  "code": "STALE_PLAN_VERSION",
  "detail": "Stale baseVersion 1: active plan version is 2.",
  "instance": "/api/v1/recovery-proposals/c0000000-0000-4000-8000-000000000002/decision",
  "currentVersion": 2
}
```

Response 409 - alreadyDecided

```json
{
  "type": "https://veya.example/problems/proposal-already-decided",
  "title": "Proposal already decided",
  "status": 409,
  "code": "PROPOSAL_ALREADY_DECIDED",
  "detail": "This proposal was already accepted.",
  "instance": "/api/v1/recovery-proposals/c0000000-0000-4000-8000-000000000001/decision"
}
```

## Daily dashboard

### `GET /api/v1/days/{date}/summary`

**Dashboard of a day** — operationId `getDaySummary`

| | |
|---|---|
| Status | **IMPLEMENTED** |
| Module | `history` |
| BA stories | US-20, US-07 |
| Acceptance criteria | US-20: given the selected date, when opened, then planned and actual nutrition, remaining totals, activity and active plan version are visible. |
| Contract source | BA Table 2.8 (GET /api/v1/days/{date}/summary) + Q10 (no history summary) |
| Authentication | JWT bearer token |
| Authorization / ownership | Authenticated user; own data only. |
| Pagination / idempotency / notes | Performance targets NFR-01 (p95 <= 500 ms) and NFR-03 (dashboard interactive <= 3 s). |

Planned and actual nutrition, remaining totals, activity and the active plan version (view `v_day_summary`).
Per decision Q10 it contains **no history browsing** (US-21 is Should); only the active version number is
returned. For a day without a plan, `plan` is null and nutrition comes from the diary.

**Parameters**

| Name | In | Type | Required | Constraints | Description |
|---|---|---|---|---|---|
| `date` | path | string (date) | yes |  | Local plan date (`YYYY-MM-DD`). |

**Responses**

| Status | Meaning | Body |
|---|---|---|
| 200 | Day summary. | `DaySummary` (`application/json`) |
| 400 | Malformed JSON, invalid parameter or field values (nothing was changed). | `Problem` (`application/problem+json`) |
| 401 | Missing, invalid or expired token, or the account no longer exists. | `Problem` (`application/problem+json`) |

Response 200

```json
{
  "date": "2026-11-16",
  "plan": {
    "planId": "5e0c7f4a-1b2d-4c3e-9f8a-7b6c5d4e3f21",
    "date": "2026-11-16",
    "activeVersion": 2,
    "changeReason": "RECOVERY_ACCEPTED",
    "versionCreatedAt": "2026-11-16T18:05:00Z",
    "recoveryRequired": false,
    "items": [
      {
        "itemKey": "b0000000-0000-4000-8000-000000000201",
        "itemType": "MEAL",
        "mealSlot": "BREAKFAST",
        "plannedTime": "08:00",
        "meal": {
          "mealId": 1,
          "name": "Oatmeal with banana"
        },
        "exercise": null,
        "plannedKcal": 426.4,
        "plannedProteinG": 17.2,
        "plannedFatG": 9.4,
        "plannedCarbsG": 72.0,
        "durationMin": null,
        "status": "COMPLETED"
      },
      {
        "itemKey": "b0000000-0000-4000-8000-000000000202",
        "itemType": "MEAL",
        "mealSlot": "LUNCH",
        "plannedTime": "13:00",
        "meal": {
          "mealId": 4,
          "name": "Chicken rice bowl"
        },
        "exercise": null,
        "plannedKcal": 629.9,
        "plannedProteinG": 54.7,
        "plannedFatG": 16.4,
        "plannedCarbsG": 63.0,
        "durationMin": null,
        "status": "COMPLETED"
      },
      {
        "itemKey": "b0000000-0000-4000-8000-000000000204",
        "itemType": "WORKOUT",
        "mealSlot": null,
        "plannedTime": "18:00",
        "meal": null,
        "exercise": {
          "exerciseId": 1,
          "name": "Running, 10 km/h"
        },
        "plannedKcal": null,
        "plannedProteinG": null,
        "plannedFatG": null,
        "plannedCarbsG": null,
        "durationMin": 30,
        "status": "SKIPPED"
      },
      {
        "itemKey": "b0000000-0000-4000-8000-000000000203",
        "itemType": "MEAL",
        "mealSlot": "DINNER",
        "plannedTime": "19:30",
        "meal": {
          "mealId": 8,
          "name": "Tofu vegetable stir-fry"
        },
        "exercise": null,
        "plannedKcal": 565.4,
        "plannedProteinG": 25.4,
        "plannedFatG": 20.6,
        "plannedCarbsG": 72.3,
        "durationMin": null,
        "status": "PLANNED"
      },
      {
        "itemKey": "6c7d8e9f-a0b1-4c2d-8e3f-4a5b6c7d8e9f",
        "itemType": "WORKOUT",
        "mealSlot": null,
        "plannedTime": "20:30",
        "meal": null,
        "exercise": {
          "exerciseId": 3,
          "name": "Bodyweight circuit"
        },
        "plannedKcal": null,
        "plannedProteinG": null,
        "plannedFatG": null,
        "plannedCarbsG": null,
        "durationMin": 20,
        "status": "PLANNED"
      }
    ]
  },
  "nutrition": {
    "plannedKcal": 1621.7,
    "consumedKcal": 1306.3,
    "consumedProteinG": 49.2,
    "consumedFatG": 47.4,
    "consumedCarbsG": 182.0,
    "targetKcal": 1900,
    "remainingKcal": 593.7
  },
  "activity": {
    "plannedWorkoutMin": 50,
    "completedWorkoutMin": 0
  },
  "openItems": 2
}
```

Response 400

```json
{
  "type": "https://veya.example/problems/validation-failed",
  "title": "Request is not valid",
  "status": 400,
  "code": "VALIDATION_FAILED",
  "detail": "One or more fields are invalid.",
  "instance": "/api/v1/me/profile",
  "errors": [
    {
      "field": "energyTargetKcal",
      "message": "must be between 500 and 10000",
      "rejectedValue": 100
    }
  ]
}
```

Response 401

```json
{
  "type": "https://veya.example/problems/authentication-required",
  "title": "Authentication required",
  "status": 401,
  "code": "AUTHENTICATION_REQUIRED",
  "detail": "A valid bearer token is required.",
  "instance": "/api/v1/me"
}
```

