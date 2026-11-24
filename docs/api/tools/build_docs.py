"""Generates the API catalogue and the Postman files from docs/api/openapi.yaml (documentation tooling).

Outputs
  docs/api/API_ENDPOINTS.md                                   endpoint catalogue, examples, traceability
  docs/api/postman/VEYA_API.postman_collection.json           Postman Collection v2.1
  docs/api/postman/VEYA_Local.postman_environment.json        local environment without secrets

OpenAPI is the single source of truth: edit openapi.yaml, then run
  python docs/api/tools/build_docs.py
  python docs/api/tools/check_contract.py
"""
from __future__ import annotations

import copy
import json
import re
import uuid
from pathlib import Path

import yaml

API_DIR = Path(__file__).resolve().parents[1]
SPEC = yaml.safe_load((API_DIR / "openapi.yaml").read_text(encoding="utf-8"))
METHODS = ("get", "post", "put", "patch", "delete")
MODULE_TITLES = {
    "system": "Shared / system", "identity": "Identity, consent and data access (LEG-2, LEG-4)",
    "profile": "Profile and planning constraints", "nutrition": "Nutrition and food catalogue",
    "planning": "Daily plans and plan items", "activity": "Physical activity and workouts",
    "recovery": "Dynamic Plan Recovery", "history": "Daily dashboard",
}
MUST_STORIES = {
    "US-01": "Register and sign in", "US-02": "Profile and goal", "US-03": "Exclusions, equipment, available time",
    "US-04": "Delete account and records", "US-05": "Search food catalogue", "US-06": "Log and correct food",
    "US-07": "Consumed / target / remaining nutrition", "US-08": "Reuse saved or recent meals",
    "US-09": "Daily meal plan", "US-10": "Replace a planned meal", "US-12": "Schedule a workout",
    "US-13": "Mark workouts completed/skipped/changed", "US-14": "Replace an exercise",
    "US-15": "Identify significant deviations", "US-16": "Revised remaining-day plan",
    "US-17": "Reason per proposed change", "US-18": "Accept, edit or reject a proposal",
    "US-19": "Explanation if recovery is impossible", "US-20": "Daily dashboard",
    "US-23": "Responsive layout", "US-24": "Loading, empty and error states", "US-26": "Keyboard and readable controls",
    "US-29": "Documented API contract", "US-30": "Server-side authorization", "US-31": "Controlled provider failures",
    "US-32": "Reproducible startup", "US-33": "Automated checks in CI",
}
NON_API = {
    "US-23": "Frontend only (responsive layout).",
    "US-24": "Frontend; relies on the documented error format (Problem Details) and status codes.",
    "US-26": "Frontend only (accessibility).",
    "US-33": "CI pipeline, no endpoint.",
}
POST_MVP = [
    ("US-11", "Should", "Browse exercises by muscle, difficulty, equipment", "Only basic `GET /api/v1/exercises` without US-11 filters."),
    ("US-21", "Should", "Browse historical plans and decisions", "No version/decision browsing endpoint (Q10). Only the active version number is returned."),
    ("US-22", "Could", "Recurring deviation summary", "Not included."),
    ("US-25", "Should", "Help correcting forms", "Frontend; field errors are already part of VALIDATION_FAILED."),
    ("US-27", "Should", "Photo nutrition estimate", "BA Table 2.8 lists `POST /api/v1/nutrition/photo-recognition` - excluded from the MVP contract."),
    ("US-28", "Should", "Correct recognition results", "Not included."),
    ("US-34", "Should", "Favourite saved meals", "Not included."),
    ("US-35", "Should", "Filter activity history", "Not included."),
    ("US-36", "Could", "CSV export", "Not included; LEG-4 JSON export (Q6) is `GET /api/v1/me/export`."),
    ("US-37", "Could", "Calendar view", "Not included."),
    ("US-38", "Could", "Light/dark theme", "Not included."),
]
OPEN_QUESTIONS = [
    ("AQ-1 (= BA Q12)", "Meaning of EDIT in US-18", "**Blocks** the EDIT variant of `POST /api/v1/recovery-proposals/{proposalId}/decision`. The database supports applying a subset of changes (`selectedChangeIds`); BA Fig. B.10 shows an Edit action per proposed item (value editing). ACCEPT and REJECT are not affected."),
    ("AQ-2", "403 or 404 for another user's resource", "US-30/NFR-08 require 401/403. Contract: 403 `ACCESS_DENIED` for an existing resource of another user, 404 for a non-existing id. The database routines report \"not found or not owned\" as `VY404`, so the API must check ownership before calling them. Needs backend confirmation."),
    ("AQ-3", "Adding/removing items of an existing plan", "**Blocked functionality (no endpoint).** The BA defines only initial planning (US-09/US-12), replacements (US-10/US-14) and recovery. Adding a workout to an already saved plan is not specified and the database has no version reason for it. Until decided, all items are sent when the plan is saved."),
    ("AQ-4", "Consents at registration", "Which consent documents are mandatory at registration (PROPOSED: both current versions) and what happens after the health-data consent is withdrawn (LEG-2)."),
    ("AQ-5", "Token lifetime, refresh, logout, storage", "PROPOSED: access token 60 min, no refresh token, logout = discard the token in the client. Where the client stores the token (memory vs. storage) is a frontend decision with security impact."),
    ("AQ-6", "Duplicate e-mail at registration", "PROPOSED 409 `EMAIL_ALREADY_REGISTERED` (usable UX, but reveals that an account exists). Alternative: neutral 201/202 with an e-mail message (needs e-mail infrastructure, not in the BA)."),
    ("AQ-7", "Food provider fallback", "PROPOSED: 200 with `providerStatus = UNAVAILABLE` when local results exist; 502/504 with `manualEntryAllowed` when nothing can be shown (US-05, US-31, R1)."),
    ("AQ-8", "Proposed conventions", "All paths not in BA Table 2.8, camelCase JSON, `/me` for the current user, `{date}` = local plan date, problem `type` URIs, error codes."),
    ("AQ-9", "Empty states", "Day summary for a day without a plan (`plan: null`), constraints of a day without saved values (empty lists), profile not yet created (404 `PROFILE_NOT_FOUND`)."),
    ("AQ-10", "Deleting a food entry", "PROPOSED as part of \"log and correct\" (US-06); the BA does not mention deletion explicitly."),
    ("AQ-11", "Recovery requested when not required", "Contract: 409 `RECOVERY_NOT_REQUIRED` (state conflict). The database raises `VY400` for this case; the API maps it."),
    ("AQ-12", "Meal plan: suggestion vs. save", "PROPOSED two steps for US-09: `POST /api/v1/meal-plan-suggestions` (nothing saved) and `POST /api/v1/plans` (explicit save, version 1), so no plan is stored without the user's confirmation."),
    ("AQ-13", "Rule for \"a constraint makes a future item infeasible\" (US-15 rule 3)", "IMPLEMENTED as PROPOSED, needs approval: an item still PLANNED in the active version is infeasible if a MEAL contains an ingredient excluded for the day, a MEAL needs more preparation minutes or a WORKOUT more minutes than `availableTimeMin`, or a WORKOUT needs equipment missing from `availableEquipment`. `availableIngredients` is stored but not used for infeasibility (the BA does not say whether a meal must consist only of available ingredients)."),
    ("AQ-14", "Acceptable target range of a suggested menu (US-09)", "IMPLEMENTED as PROPOSED, needs approval: a suggested menu is accepted only if its energy is within +/- `veya.planning.target-tolerance-pct` (default 20 %) of the profile target; otherwise 422 NO_FEASIBLE_MEAL_PLAN with a NUTRITION_TARGET blocking constraint. Macros are reported, not constrained."),
]
VARIABLE_LITERALS = {
    "<your-password>": "{{demoPassword}}",
    "anna.demo@example.org": "{{demoEmail}}",
    "2026-11-16": "{{planDate}}",
    "2026-11-17": "{{planDate}}",
    "5e0c7f4a-1b2d-4c3e-9f8a-7b6c5d4e3f21": "{{planId}}",
    "b0000000-0000-4000-8000-000000000201": "{{breakfastItemKey}}",
    "b0000000-0000-4000-8000-000000000202": "{{lunchItemKey}}",
    "b0000000-0000-4000-8000-000000000203": "{{dinnerItemKey}}",
    "b0000000-0000-4000-8000-000000000204": "{{workoutItemKey}}",
    "c0000000-0000-4000-8000-000000000001": "{{proposalId}}",
    "c0000000-0000-4000-8000-000000000002": "{{staleProposalId}}",
    "c0000000-0000-4000-8000-000000000003": "{{rejectProposalId}}",
    "9d4e5f60-7182-4930-a4b5-c6d7e8f90a1b": "{{foodEntryId}}",
    "e0000000-0000-4000-8000-000000000001": "{{savedMealId}}",
    "7a2d3b0f-4c8e-4f90-b2c3-d4e5f6071829": "{{consentId}}",
    "f1a2b3c4-d5e6-4f70-8192-a3b4c5d6e7f8": "{{firstChangeId}}",
}
ENVIRONMENT = [
    ("baseUrl", "http://localhost:8080", "default"),
    ("demoEmail", "anna.demo@example.org", "default"),
    ("demoPassword", "", "secret"),
    ("accessToken", "", "secret"),
    ("otherAccessToken", "", "secret"),
    ("userId", "", "default"),
    ("planDate", "2026-11-17", "default"),
    ("planId", "", "default"),
    ("planVersion", "1", "default"),
    ("breakfastItemKey", "", "default"),
    ("lunchItemKey", "", "default"),
    ("dinnerItemKey", "", "default"),
    ("workoutItemKey", "", "default"),
    ("foodEntryId", "", "default"),
    ("savedMealId", "", "default"),
    ("consentId", "", "default"),
    ("proposalId", "", "default"),
    ("proposalBaseVersion", "1", "default"),
    ("staleProposalId", "", "default"),
    ("rejectProposalId", "", "default"),
    ("firstChangeId", "", "default"),
]


# ------------------------------------------------------------------------------------------------ helpers
def operations():
    for tag in [t["name"] for t in SPEC["tags"]]:
        for path, item in SPEC["paths"].items():
            for method in METHODS:
                op = item.get(method)
                if op and op["tags"][0] == tag:
                    yield tag, path, method, op


def resolve(node):
    while isinstance(node, dict) and "$ref" in node:
        target = SPEC
        for part in node["$ref"][2:].split("/"):
            target = target[part]
        node = target
    return node


def ref_name(node):
    return node["$ref"].rsplit("/", 1)[-1] if isinstance(node, dict) and "$ref" in node else None


def type_of(schema) -> str:
    name = ref_name(schema)
    if name:
        return f"`{name}`"
    if "oneOf" in schema:
        return " or ".join(type_of(s) for s in schema["oneOf"])
    if "allOf" in schema:
        return " + ".join(type_of(s) for s in schema["allOf"])
    t = schema.get("type", "object")
    if isinstance(t, list):
        t = " or ".join(t)
    if t == "array":
        return f"array of {type_of(schema.get('items', {}))}"
    if "enum" in schema:
        return f"{t} enum"
    if "const" in schema:
        return f"{t} = `{schema['const']}`"
    if schema.get("format"):
        return f"{t} ({schema['format']})"
    return t


def constraints(schema) -> str:
    s = resolve(schema)
    parts = []
    for key, label in (("minimum", ">="), ("maximum", "<="), ("exclusiveMinimum", ">"), ("minLength", "min length"),
                       ("maxLength", "max length"), ("minItems", "min items"), ("maxItems", "max items"),
                       ("pattern", "pattern")):
        if key in s:
            parts.append(f"{label} `{s[key]}`")
    if "enum" in s:
        parts.append("one of " + ", ".join(f"`{v}`" for v in s["enum"]))
    if s.get("default") is not None:
        parts.append(f"default `{s['default']}`")
    if s.get("uniqueItems"):
        parts.append("unique")
    return "; ".join(parts)


def flatten(schema):
    """Properties and required names of an object schema (follows $ref and allOf)."""
    schema = resolve(schema)
    props, required = {}, set(schema.get("required", []))
    for member in schema.get("allOf", []):
        p, r = flatten(member)
        props.update(p)
        required |= r
    props.update(schema.get("properties", {}))
    return props, required


def field_table(schema) -> str:
    props, required = flatten(schema)
    if not props:
        return ""
    rows = ["| Field | Type | Required | Constraints | Description |", "|---|---|---|---|---|"]
    for name, prop in props.items():
        desc = (resolve(prop).get("description") or prop.get("description") or "").replace("\n", " ").replace("|", "\\|")
        rows.append(f"| `{name}` | {type_of(prop)} | {'yes' if name in required else 'no'} | {constraints(prop)} | {desc} |")
    return "\n".join(rows)


def media_examples(media):
    if "example" in media:
        yield None, media["example"]
    for name, ex in media.get("examples", {}).items():
        ex = resolve(ex)
        yield ex.get("summary") or name, ex["value"]


def code_block(value) -> str:
    return "```json\n" + json.dumps(value, indent=2, ensure_ascii=False) + "\n```"


def is_public(op) -> bool:
    return op.get("security", SPEC["security"]) == []


# -------------------------------------------------------------------------------------------- catalogue
def build_catalogue() -> str:
    ops = list(operations())
    counts = {s: sum(1 for *_, o in ops if o["x-veya-status"] == s) for s in ("IMPLEMENTED", "PLANNED", "PARTIAL", "BLOCKED")}
    out = [
        "# VEYA REST API — endpoint catalogue (MVP contract draft)",
        "",
        "> **Generated from [`openapi.yaml`](openapi.yaml) by `tools/build_docs.py` — do not edit by hand.**",
        "> Status 2026-11-22: **IMPLEMENTED** operations are served by the running backend and verified over real HTTP by",
        "> the API integration tests (`backend/src/test/java/com/veya/api`, responses validated against this contract).",
        "> **PARTIAL** operations work with the limitation stated in their notes. **PLANNED** operations are documented only;",
        "> no controller exists yet, so those requests cannot succeed against the running backend.",
        "",
        f"Operations: **{len(ops)}** — IMPLEMENTED {counts['IMPLEMENTED']}, PLANNED {counts['PLANNED']}, "
        f"PARTIAL {counts['PARTIAL']}, BLOCKED {counts['BLOCKED']} (fully blocked operations). "
        "One operation variant is blocked: **EDIT** of a recovery proposal (Q12). "
        "One function without endpoint is blocked: adding items to an existing plan (AQ-3).",
        "",
        "Sources: BA `docs/BA_Malafei_Mikita.docx` (Table 2.8 API contract, Appendix A stories, UC-01/UC-02, Fig. B.4/B.5/B.10,",
        "NFR-01..18, LEG-1..9), agreed decisions [`docs/ba-open-questions.md`](../ba-open-questions.md), database",
        "[`docs/database/`](../database/README.md). **Contract source** of every operation says whether the path/status",
        "comes from the BA or is **PROPOSED** (needs agreement, see [Open questions](#open-questions)).",
        "",
        "## Conventions (PROPOSED unless stated)",
        "",
        "* Base URL `http://localhost:8080`; business endpoints under `/api/v1` (BA Table 2.8).",
        "* JSON, camelCase; dates `YYYY-MM-DD` (user's local plan date); times `HH:mm`; instants ISO-8601 UTC.",
        "* Authentication: `Authorization: Bearer <JWT>` from `POST /api/v1/auth/login`; public operations are marked.",
        "* Authorization: a user only sees and changes own data. `/me` endpoints never take a user id. Ids of another",
        "  user's resources -> **403 `ACCESS_DENIED`** (US-30, NFR-08); unknown ids -> 404 (AQ-2).",
        "* Errors: RFC 9457 Problem Details, `Content-Type: application/problem+json`, stable `code` (NFR-14) - see",
        "  [Error codes](#error-codes).",
        "* Plan changes need an explicit user action: suggestions and options are read-only; only `POST /plans`,",
        "  `.../replacement` and `.../decision` change a plan, each creating a new immutable version (Q2).",
        "* Optimistic locking: every plan change sends `baseVersion`; a stale value returns **409",
        "  `STALE_PLAN_VERSION`** and nothing changes (NFR-10).",
        "* Pagination: lists are bounded (`limit`) or limited to one date; no cursor pagination in the MVP.",
        "* Idempotency: GET/PUT/DELETE are idempotent; POSTs are not (repeats return documented 409s). No",
        "  `Idempotency-Key` header in the MVP.",
        "",
        "## Endpoint matrix",
        "",
        "| # | Module | Method and path | Operation | Stories | Auth | Status | Contract source |",
        "|---|---|---|---|---|---|---|---|",
    ]
    for i, (tag, path, method, op) in enumerate(ops, 1):
        src = "BA" if op["x-veya-contract-source"].startswith("BA") else (
            "Actuator" if "Actuator" in op["x-veya-contract-source"] else "PROPOSED")
        status = op["x-veya-status"] + (" (EDIT BLOCKED)" if op["operationId"] == "decideRecoveryProposal" else "")
        out.append(f"| {i} | {tag} | [`{method.upper()} {path}`](#{anchor(method, path)}) | `{op['operationId']}` | "
                   f"{', '.join(op['x-veya-stories'])} | {'public' if is_public(op) else 'JWT'} | {status} | {src} |")
    out += ["", "## Traceability - 27 Must user stories", "",
            "| Story | Title | Operations |", "|---|---|---|"]
    for story, title in MUST_STORIES.items():
        if story in NON_API:
            refs = NON_API[story]
        else:
            refs = "<br>".join(f"`{m.upper()} {p}`" for _, p, m, o in ops if story in o["x-veya-stories"])
        out.append(f"| {story} | {title} | {refs} |")
    out += ["", "Cross-cutting: LEG-2 consents (Q7) and LEG-4 export (Q6) are covered by the identity operations;",
            "LEG-3/US-30 ownership by every authenticated operation; LEG-5 (hashing, HTTPS, no secrets in logs) by the",
            "backend implementation.", "",
            "### Not in the MVP contract (Should/Could)", "",
            "| Story | Priority | Title | Handling |", "|---|---|---|---|"]
    for row in POST_MVP:
        out.append("| " + " | ".join(row) + " |")
    out += ["", "## Open questions", "",
            "| ID | Topic | Impact / proposal |", "|---|---|---|"]
    for row in OPEN_QUESTIONS:
        out.append("| " + " | ".join(row) + " |")
    out += ["", "## Error codes", "",
            "| HTTP | `code` | When |", "|---|---|---|"]
    error_map = [
        ("400", "VALIDATION_FAILED", "Invalid parameters/fields; `errors[]` lists the fields. Nothing changed."),
        ("400", "MALFORMED_REQUEST", "Body is not valid JSON."),
        ("400", "CONFIRMATION_REQUIRED", "Account deletion without `confirm=true`."),
        ("400", "INVALID_EDIT", "EDIT selection is empty, duplicated or not part of the proposal (US-18)."),
        ("401", "AUTHENTICATION_REQUIRED", "Missing, invalid or expired token; deleted account."),
        ("401", "INVALID_CREDENTIALS", "Sign-in failed (same answer for unknown e-mail and wrong password)."),
        ("403", "ACCESS_DENIED", "Resource of another user (US-30)."),
        ("404", "RESOURCE_NOT_FOUND / PROFILE_NOT_FOUND / PLAN_NOT_FOUND", "Unknown id; no profile; no plan for the date."),
        ("409", "STALE_PLAN_VERSION", "`baseVersion` is not the active version (NFR-10); `currentVersion` is returned."),
        ("409", "PROPOSAL_ALREADY_DECIDED", "Second decision for the same proposal."),
        ("409", "RECOVERY_NOT_REQUIRED", "Recovery requested without an open significant deviation (AQ-11)."),
        ("409", "ITEM_FROZEN / STATUS_ALREADY_RECORDED / IMMUTABLE_HISTORY", "Completed items and superseded versions cannot change."),
        ("409", "PLAN_ALREADY_EXISTS / EMAIL_ALREADY_REGISTERED / SAVED_MEAL_NAME_TAKEN / CONSENT_ALREADY_ACTIVE / CONSENT_ALREADY_WITHDRAWN", "Uniqueness or state conflicts."),
        ("422", "RECOVERY_NOT_FEASIBLE", "No feasible recovery combination; `blockingConstraints[]` (US-19). No new version."),
        ("422", "NO_FEASIBLE_ALTERNATIVE / NO_FEASIBLE_MEAL_PLAN", "No compliant meal/exercise alternative or meal plan; `blockingConstraints[]`."),
        ("422", "HARD_CONSTRAINT_VIOLATION", "A requested item breaks an exclusion, time or equipment constraint."),
        ("502", "PROVIDER_UNAVAILABLE", "External provider error without local results; `manualEntryAllowed` (US-31)."),
        ("504", "PROVIDER_TIMEOUT", "External provider timeout without local results; `manualEntryAllowed` (US-05)."),
        ("500", "INTERNAL_ERROR", "Unexpected error; no internal details are returned."),
    ]
    for row in error_map:
        out.append("| " + " | ".join(row) + " |")
    out += ["", "Database error codes are translated by the API: `VY400` -> 400, `VY404` -> 403/404 (after the ownership",
            "check, AQ-2), `VY409` -> 409 `STALE_PLAN_VERSION`/`PROPOSAL_ALREADY_DECIDED`, `VY001` -> 409 `IMMUTABLE_HISTORY`;",
            "constraint violations (23xxx) -> 400/409. Database details (table names, SQL) are never returned.", ""]

    for tag in [t["name"] for t in SPEC["tags"]]:
        out += [f"## {MODULE_TITLES[tag]}", ""]
        for t, path, method, op in ops:
            if t == tag:
                out += endpoint_section(path, method, op)
    return "\n".join(out) + "\n"


def anchor(method, path) -> str:
    text = f"{method} {path}".lower()
    return re.sub(r"[^a-z0-9 _-]", "", text).replace(" ", "-")


def endpoint_section(path, method, op) -> list[str]:
    status = op["x-veya-status"]
    out = [f"### `{method.upper()} {path}`", "",
           f"**{op['summary'].split('] ', 1)[-1]}** — operationId `{op['operationId']}`", "",
           "| | |", "|---|---|",
           f"| Status | **{status}**{' — EDIT variant BLOCKED (Q12)' if op['operationId'] == 'decideRecoveryProposal' else ''} |",
           f"| Module | `{op['x-veya-module']}` |",
           f"| BA stories | {', '.join(op['x-veya-stories'])} |",
           f"| Acceptance criteria | {op['x-veya-acceptance']} |",
           f"| Contract source | {op['x-veya-contract-source']} |",
           f"| Authentication | {'Public' if is_public(op) else 'JWT bearer token'} |",
           f"| Authorization / ownership | {op['x-veya-authorization']} |"]
    if op.get("x-veya-notes"):
        out.append(f"| Pagination / idempotency / notes | {op['x-veya-notes']} |")
    if op.get("x-veya-open-questions"):
        out.append(f"| Open questions | {'; '.join(op['x-veya-open-questions'])} |")
    out.append("")
    if op.get("description"):
        out += [op["description"].strip(), ""]
    params = [resolve(p) for p in op.get("parameters", [])]
    if params:
        out += ["**Parameters**", "", "| Name | In | Type | Required | Constraints | Description |", "|---|---|---|---|---|---|"]
        for p in params:
            out.append(f"| `{p['name']}` | {p['in']} | {type_of(p['schema'])} | {'yes' if p.get('required') else 'no'} | "
                       f"{constraints(p['schema'])} | {(p.get('description') or '').replace('|', '/')} |")
        out.append("")
    body = resolve(op.get("requestBody", {}))
    if body:
        media = body["content"]["application/json"]
        out += [f"**Request body** ({'required' if body.get('required') else 'optional'}) — {type_of(media['schema'])}", ""]
        table = field_table(media["schema"])
        if table:
            out += [table, ""]
        for title, value in media_examples(media):
            out += [f"Request example{': ' + title if title else ''}", "", code_block(value), ""]
    out += ["**Responses**", "", "| Status | Meaning | Body |", "|---|---|---|"]
    for code, response in op["responses"].items():
        response = resolve(response)
        content = response.get("content", {})
        media_type = next(iter(content), None)
        body_type = f"{type_of(content[media_type]['schema'])} (`{media_type}`)" if media_type else "—"
        out.append(f"| {code} | {response['description'].strip().replace(chr(10), ' ')} | {body_type} |")
    out.append("")
    for code, response in op["responses"].items():
        response = resolve(response)
        for media_type, media in response.get("content", {}).items():
            for title, value in media_examples(media):
                out += [f"Response {code}{' - ' + title if title else ''}", "", code_block(value), ""]
    return out


# ---------------------------------------------------------------------------------------------- postman
def substitute(text: str) -> str:
    for literal, variable in VARIABLE_LITERALS.items():
        text = text.replace(literal, variable)
    return text


def request_example(op, case):
    body = resolve(op.get("requestBody", {}))
    if not body:
        return None
    media = body["content"]["application/json"]
    wanted = case.get("example")
    for name, ex in media.get("examples", {}).items():
        if wanted is None or name == wanted:
            return copy.deepcopy(resolve(ex)["value"])
    return copy.deepcopy(media.get("example"))


def body_text(value, case) -> str:
    text = json.dumps(value, indent=2, ensure_ascii=False)
    for field, variable in case.get("bodyVars", {}).items():
        text = re.sub(rf'("{field}": )\d+', rf"\g<1>{{{{{variable}}}}}", text)
    return substitute(text)


def url_for(path, op, case):
    params = {resolve(p)["name"]: resolve(p) for p in op.get("parameters", [])}
    segments = []
    for segment in path.strip("/").split("/"):
        match = re.fullmatch(r"{(\w+)}", segment)
        if match:
            name = match.group(1)
            value = case.get("path", {}).get(name, str(params[name].get("example", "")))
            segments.append(substitute(str(value)))
        else:
            segments.append(segment)
    query = []
    for name, p in params.items():
        if p["in"] != "query":
            continue
        override = case.get("query", {}).get(name)
        if override is not None:
            value = override
        elif p.get("required") or "example" in p:
            value = p.get("example", "")
            if isinstance(value, bool):
                value = str(value).lower()
        else:
            continue
        query.append({"key": name, "value": substitute(str(value))})
    raw = "{{baseUrl}}/" + "/".join(segments)
    if query:
        raw += "?" + "&".join(f"{q['key']}={q['value']}" for q in query)
    url = {"raw": raw, "host": ["{{baseUrl}}"], "path": segments}
    if query:
        url["query"] = query
    return url


def documented_example(op, status):
    response = resolve(op["responses"].get(str(status), {}))
    for media in response.get("content", {}).values():
        for _, value in media_examples(media):
            return value, media.get("schema")
    return None, None


def test_script(op, case) -> list[str]:
    expect = int(case["expect"])
    lines = [f'pm.test("status is {expect}", function () {{ pm.response.to.have.status({expect}); }});']
    example, schema = documented_example(op, expect)
    if expect >= 400:
        code = example.get("code") if isinstance(example, dict) else None
        lines.append('pm.test("RFC 9457 problem details", function () {'
                     ' pm.expect(pm.response.headers.get("Content-Type")).to.include("application/problem+json");'
                     ' pm.expect(pm.response.json()).to.have.property("code"); });')
        if code:
            lines.append(f'pm.test("error code {code}", function () {{ pm.expect(pm.response.json().code).to.eql("{code}"); }});')
    elif expect != 204 and schema is not None:
        props, required = flatten(schema) if resolve(schema).get("type", "object") == "object" or "allOf" in resolve(schema) else ({}, set())
        if required:
            keys = json.dumps(sorted(required))
            lines.append(f'pm.test("response has required fields", function () {{ pm.expect(pm.response.json()).to.include.all.keys({keys}); }});')
        elif resolve(schema).get("type") == "array":
            lines.append('pm.test("response is an array", function () { pm.expect(pm.response.json()).to.be.an("array"); });')
    for variable, path in case.get("saveFrom", {}).items():
        accessor = "".join(f"[{p}]" if p.isdigit() else f'["{p}"]' for p in str(path).split("."))
        lines.append(f'if (pm.response.code < 300) {{ pm.environment.set("{variable}", pm.response.json(){accessor}); }}')
    return lines


def postman_item(path, method, op, case):
    status = op["x-veya-status"]
    label = "[BLOCKED Q12]" if case.get("blocked") else f"[{status}]"
    description = (f"operationId: `{op['operationId']}` · path: `{path}` · method: `{method.upper()}`\n\n"
                   f"Expected: HTTP {case['expect']}. Stories: {', '.join(op['x-veya-stories'])}.\n\n")
    if status == "PLANNED":
        description += ("**PLANNED - not implemented yet.** This request documents the contract and cannot pass an "
                        "integration test until the backend implements the operation.\n\n")
    elif status == "PARTIAL":
        description += "**PARTIAL** - implemented with a limitation: " + op.get("x-veya-notes", "") + "\n\n"
    if case.get("blocked"):
        description += "**BLOCKED by Q12** - the EDIT request shape is provisional and may change.\n\n"
    if case.get("destructive"):
        description += "**Destructive** - deletes the account. Run last.\n\n"
    request = {"method": method.upper(), "header": [{"key": "Accept", "value": "application/json"}],
               "url": url_for(path, op, case), "description": description.strip()}
    auth = case.get("auth", "none" if is_public(op) else "bearer")
    if auth == "none":
        request["auth"] = {"type": "noauth"}
    elif auth == "other":
        request["auth"] = {"type": "bearer", "bearer": [{"key": "token", "value": "{{otherAccessToken}}", "type": "string"}]}
    elif auth == "invalid":
        request["auth"] = {"type": "bearer", "bearer": [{"key": "token", "value": "invalid.jwt.token", "type": "string"}]}
    example = request_example(op, case)
    if example is not None:
        request["header"].append({"key": "Content-Type", "value": "application/json"})
        request["body"] = {"mode": "raw", "raw": body_text(example, case), "options": {"raw": {"language": "json"}}}
    item = {"name": f"{label} {case['name']}", "request": request,
            "event": [{"listen": "test", "script": {"type": "text/javascript", "exec": test_script(op, case)}}]}
    expected_example, _ = documented_example(op, case["expect"])
    if expected_example is not None:
        item["response"] = [{
            "name": f"Documented {case['expect']} (from OpenAPI, not a recorded run)",
            "originalRequest": copy.deepcopy({k: v for k, v in request.items() if k != "description"}),
            "status": "Documented", "code": int(case["expect"]),
            "header": [{"key": "Content-Type", "value": "application/problem+json" if int(case["expect"]) >= 400 else "application/json"}],
            "body": json.dumps(expected_example, indent=2, ensure_ascii=False), "_postman_previewlanguage": "json"}]
    return item


SCENARIO = [  # UC-01 Dynamic Plan Recovery, in execution order (operationId, case name)
    ("login", "Sign in (Anna)"),
    ("createPlan", "Save plan for 2026-11-17"),
    ("createFoodEntry", "Log lunch (+250 kcal against the plan)"),
    ("recordPlanItemStatus", "Confirm lunch (deviation >= 200 kcal)"),
    ("createActivityEntry", "Skip the planned workout"),
    ("listOpenDeviations", "Open deviations"),
    ("createRecoveryProposal", "Generate a recovery proposal"),
    ("createRecoveryProposal", "Generate a second proposal (for the stale case)"),
    ("decideRecoveryProposal", "ACCEPT the proposal"),
    ("decideRecoveryProposal", "ACCEPT a stale proposal"),
    ("decideRecoveryProposal", "Decide the same proposal again"),
    ("getDaySummary", "Dashboard of 2026-11-16"),
]


def build_postman():
    folders, cleanup, by_case = [], [], {}
    for tag in [t["name"] for t in SPEC["tags"]]:
        items = []
        for t, path, method, op in operations():
            if t != tag:
                continue
            for case in op["x-veya-postman"]:
                item = postman_item(path, method, op, case)
                by_case[(op["operationId"], case["name"])] = item
                (cleanup if case.get("destructive") else items).append(item)
        folders.append({"name": f"{MODULE_TITLES[tag]}", "description": next(t["description"] for t in SPEC["tags"] if t["name"] == tag), "item": items})
    scenario = {"name": "Scenario - UC-01 Dynamic Plan Recovery (PLANNED)",
                "description": "Ordered happy path and conflicts of UC-01: plan -> deviation -> proposal -> ACCEPT -> stale 409 -> "
                               "already decided 409 -> dashboard. PLANNED - cannot run until the backend is implemented.",
                "item": [copy.deepcopy(by_case[key]) for key in SCENARIO]}
    folders.insert(1, scenario)
    folders.append({"name": "Cleanup (destructive, run last)", "item": cleanup})
    collection = {
        "info": {
            "_postman_id": str(uuid.uuid5(uuid.NAMESPACE_URL, "veya-api-collection")),
            "name": "VEYA API (MVP contract draft)",
            "description": ("Generated from docs/api/openapi.yaml. Each request name starts with the operation status: "
                            "[IMPLEMENTED] requests work against the running backend, [PARTIAL] ones with the limitation "
                            "in their description, [PLANNED] ones cannot pass until the backend implements them. Saved "
                            "responses are documented examples, not recorded runs. Set demoPassword and obtain tokens "
                            "locally; never commit them."),
            "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json",
        },
        "auth": {"type": "bearer", "bearer": [{"key": "token", "value": "{{accessToken}}", "type": "string"}]},
        "item": folders,
    }
    environment = {
        "id": str(uuid.uuid5(uuid.NAMESPACE_URL, "veya-local-environment")),
        "name": "VEYA Local",
        "values": [{"key": k, "value": v, "type": t, "enabled": True} for k, v, t in ENVIRONMENT],
        "_postman_variable_scope": "environment",
    }
    return collection, environment


def main():
    (API_DIR / "API_ENDPOINTS.md").write_text(build_catalogue(), encoding="utf-8", newline="\n")
    collection, environment = build_postman()
    (API_DIR / "postman").mkdir(exist_ok=True)
    (API_DIR / "postman" / "VEYA_API.postman_collection.json").write_text(
        json.dumps(collection, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
    (API_DIR / "postman" / "VEYA_Local.postman_environment.json").write_text(
        json.dumps(environment, indent=2, ensure_ascii=False) + "\n", encoding="utf-8", newline="\n")
    print("generated API_ENDPOINTS.md and Postman files")


if __name__ == "__main__":
    main()
