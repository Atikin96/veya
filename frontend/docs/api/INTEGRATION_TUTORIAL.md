# VEYA API Integration Tutorial

## Dynamic Plan Recovery (UC-01)

**Contract version:** 0.1.0
**OpenAPI:** [openapi.yaml](openapi.yaml)
**Endpoint reference:** [API_ENDPOINTS.md](API_ENDPOINTS.md)
**Status:** Contract-level tutorial. Business endpoints are currently PLANNED.

## 1. Purpose and prerequisites

This tutorial describes how a frontend client integrates the Dynamic Plan Recovery workflow.

The scenario: a user deviates from the daily plan. For example, lunch contains more calories than planned, a workout is skipped, or a planned ingredient is unavailable. VEYA generates an alternative plan for the remaining day.

Prerequisites:

- A VEYA backend environment.
- A valid user account.
- An existing daily plan.
- A JWT access token.

Base URL: `http://localhost:8080`

**Important:** All `/api/v1` endpoints below are currently PLANNED. The requests and responses illustrate the API contract and cannot yet be executed successfully against the backend.

## 2. Step 1 — Authenticate

Send credentials to:

**POST** `/api/v1/auth/login`

Request:

```http
POST /api/v1/auth/login HTTP/1.1
Host: localhost:8080
Content-Type: application/json

{
  "email": "anna.demo@example.org",
  "password": "<your-password>"
}
```

A successful response returns an access token:

```json
{
  "accessToken": "<access-token>",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

Use the token in the `Authorization` header:

```http
Authorization: Bearer <access-token>
```

A `401 Unauthorized` response means the credentials are invalid.

## 3. Step 2 — Retrieve the current plan

Request the user's plan:

**GET** `/api/v1/plans/{planId}`

```http
GET /api/v1/plans/plan-001 HTTP/1.1
Host: localhost:8080
Authorization: Bearer <access-token>
```

After a deviation has been recorded, the response may indicate that recovery is required:

```json
{
  "planId": "plan-001",
  "date": "2026-11-24",
  "activeVersion": 1,
  "recoveryRequired": true
}
```

Store `activeVersion`. It will be used as `baseVersion` when requesting a recovery proposal.

## 4. Step 3 — Request a recovery proposal

Send a request to generate an alternative plan:

**POST** `/api/v1/plans/{planId}/recovery-proposals`

```http
POST /api/v1/plans/plan-001/recovery-proposals HTTP/1.1
Host: localhost:8080
Authorization: Bearer <access-token>
Content-Type: application/json

{
  "baseVersion": 1
}
```

A successful response (`201 Created`) contains a proposal with its ID, status, suggested changes, reasons, and updated totals.

The proposal does not modify the active plan.

## 5. Step 4 — Present the proposal to the user

The frontend should display:

- The original and proposed plan items.
- The reason for each suggested change.
- Updated calorie and macronutrient totals.
- Any relevant constraints or warnings.

Do not apply the proposal automatically. The user must explicitly accept or reject it.

## 6. Step 5 — Submit the user's decision

To accept or reject the proposal, send a request to:

**POST** `/api/v1/recovery-proposals/{proposalId}/decision`

To accept:

```json
{
  "decision": "ACCEPT",
  "baseVersion": 1
}
```

To reject:

```json
{
  "decision": "REJECT",
  "baseVersion": 1
}
```

Send the selected JSON body to the decision endpoint with the same authorization and content-type headers.

On acceptance, the active plan version is updated. On rejection, the active plan remains unchanged.

## 7. Error handling

The frontend should handle errors as follows:

| Status | Meaning | Recommended frontend action |
|---|---|---|
| 400 | Invalid request | Show validation feedback |
| 401 | Unauthorized | Ask the user to authenticate again |
| 403 | Forbidden | Show an access error |
| 404 | Resource not found | Show a not-found message |
| 409 | Version conflict | Reload the current plan |
| 422 | Recovery constraints cannot be satisfied | Explain that no valid proposal is available |

The `422` response applies to recovery proposal generation. The exact status codes for each operation are defined in the endpoint reference.

## 8. Important implementation note

This tutorial describes the API contract, not a currently executable end-to-end workflow.

The business endpoints under `/api/v1` are currently marked as `PLANNED` in the OpenAPI specification. Only the health endpoints are implemented at this stage.

The examples are illustrative and must be checked against the final API implementation.

## 9. Related documentation

- [OpenAPI specification](openapi.yaml)
- [API endpoint reference](API_ENDPOINTS.md)
- [Frontend handoff](FRONTEND_HANDOFF.md)
- [Postman collection](postman/VEYA_API.postman_collection.json)
