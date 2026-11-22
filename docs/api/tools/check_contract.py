"""Consistency checks for the VEYA API contract (documentation tooling, not application code).

Checks
  1. docs/api/openapi.yaml is a valid OpenAPI 3.1 document (openapi-spec-validator).
  2. Every example (parameters, request bodies, responses, components/examples) validates against its schema.
     Object schemas are made strict for the check, so misspelled or unknown fields are reported. Request examples
     named invalid*/missing* are negative examples and must be REJECTED by the schema.
  3. Every operation has the VEYA metadata (x-veya-status, -module, -stories, ...), a status prefix in the summary,
     401 for secured operations, 403/404 for operations on another user's resources, and Postman cases whose
     expected status codes are documented.
  4. Every Must user story that needs the API is referenced by at least one operation; no Should/Could story is.
  5. The generated Postman Collection and API_ENDPOINTS.md contain exactly the operations of the specification.

Usage (Python 3.11+):
  python -m pip install pyyaml openapi-spec-validator==0.7.1 jsonschema
  python docs/api/tools/check_contract.py
Exit code 0 = all checks passed.
"""
from __future__ import annotations

import copy
import json
import re
import sys
from pathlib import Path

import yaml
from jsonschema import Draft202012Validator
from openapi_spec_validator import validate as validate_openapi
from referencing import Registry, Resource
from referencing.jsonschema import DRAFT202012

API_DIR = Path(__file__).resolve().parents[1]
SPEC_FILE = API_DIR / "openapi.yaml"
POSTMAN_FILE = API_DIR / "postman" / "VEYA_API.postman_collection.json"
ENV_FILE = API_DIR / "postman" / "VEYA_Local.postman_environment.json"
CATALOGUE_FILE = API_DIR / "API_ENDPOINTS.md"

METHODS = ("get", "post", "put", "patch", "delete")
STATUSES = {"IMPLEMENTED", "PARTIAL", "PLANNED", "BLOCKED"}
MUST = {f"US-{n:02d}" for n in [*range(1, 11), *range(12, 21), 23, 24, 26, *range(29, 34)]}
UI_OR_CI_ONLY = {"US-23", "US-24", "US-26", "US-33"}  # no API operation of their own
OWNED_PATH_PARAMS = {"planId", "proposalId", "entryId", "savedMealId", "consentId"}
REQUIRED_EXT = ["x-veya-status", "x-veya-module", "x-veya-stories", "x-veya-acceptance",
                "x-veya-contract-source", "x-veya-authorization", "x-veya-postman"]

errors: list[str] = []


def fail(message: str) -> None:
    errors.append(message)


def operations(spec):
    for path, item in spec["paths"].items():
        for method in METHODS:
            if method in item:
                yield path, method, item[method]


def resolve(spec, node):
    while isinstance(node, dict) and "$ref" in node and node["$ref"].startswith("#/"):
        target = spec
        for part in node["$ref"][2:].split("/"):
            target = target[part]
        node = target
    return node


def strict_spec(spec):
    """Copy of the spec whose object schemas reject unknown properties (except allOf building blocks)."""
    strict = copy.deepcopy(spec)
    schemas = strict["components"]["schemas"]
    composed = set()

    def collect(node):
        if isinstance(node, dict):
            for member in node.get("allOf", []):
                if isinstance(member, dict) and "$ref" in member:
                    composed.add(member["$ref"].rsplit("/", 1)[-1])
            for value in node.values():
                collect(value)
        elif isinstance(node, list):
            for value in node:
                collect(value)

    collect(schemas)

    def tighten(node, in_all_of=False):
        if isinstance(node, dict):
            if "allOf" in node:
                node.setdefault("unevaluatedProperties", False)
                for member in node["allOf"]:
                    tighten(member, in_all_of=True)
                for key, value in node.items():
                    if key != "allOf":
                        tighten(value)
                return
            if "properties" in node and not in_all_of and "additionalProperties" not in node:
                node["unevaluatedProperties"] = False
            for key, value in node.items():
                if key not in ("if", "then", "else", "not"):  # conditional parts must stay open
                    tighten(value)
        elif isinstance(node, list):
            for value in node:
                tighten(value)

    for name, schema in schemas.items():
        tighten(schema, in_all_of=name in composed)
    return strict


def check_examples(spec):
    strict = strict_spec(spec)
    registry = Registry().with_resource("urn:veya", Resource(contents=strict, specification=DRAFT202012))

    def validator_for(schema):
        root = {"$schema": "https://json-schema.org/draft/2020-12/schema", "$ref": "urn:veya#/x-check"}
        local = copy.deepcopy(strict)
        local["x-check"] = schema
        reg = Registry().with_resource("urn:veya", Resource(contents=local, specification=DRAFT202012))
        return Draft202012Validator(root, registry=reg)

    def validate(where, schema, value):
        for error in validator_for(schema).iter_errors(value):
            fail(f"example {where}: {error.message} at {'/'.join(map(str, error.absolute_path))}")

    def media_examples(media):
        if "example" in media:
            yield "example", media["example"]
        for name, example in media.get("examples", {}).items():
            yield name, resolve(spec, example)["value"]

    for path, method, op in operations(spec):
        where = f"{method.upper()} {path}"
        for param in op.get("parameters", []):
            param = resolve(spec, param)
            if "example" in param:
                validate(f"{where} param {param['name']}", param["schema"], param["example"])
        body = resolve(spec, op.get("requestBody", {}))
        for ctype, media in body.get("content", {}).items():
            for name, value in media_examples(media):
                if name.startswith(("invalid", "missing")):
                    # Negative example: the schema itself must reject it (client-side validation is possible).
                    if not list(validator_for(media["schema"]).iter_errors(value)):
                        fail(f"example {where} request {name}: negative example is accepted by the schema")
                    continue
                validate(f"{where} request {name}", media["schema"], value)
        for status, response in op.get("responses", {}).items():
            response = resolve(spec, response)
            for ctype, media in response.get("content", {}).items():
                if "schema" not in media:
                    continue
                for name, value in media_examples(media):
                    validate(f"{where} {status} {name}", media["schema"], value)
    del registry


def check_metadata(spec):
    seen_ids = set()
    referenced = set()
    for path, method, op in operations(spec):
        where = f"{method.upper()} {path}"
        op_id = op.get("operationId")
        if not op_id or op_id in seen_ids:
            fail(f"{where}: missing or duplicate operationId {op_id}")
        seen_ids.add(op_id)
        for key in REQUIRED_EXT:
            if key not in op:
                fail(f"{where}: missing {key}")
        status = op.get("x-veya-status")
        if status not in STATUSES:
            fail(f"{where}: invalid x-veya-status {status}")
        elif not op.get("summary", "").startswith(f"[{status}]"):
            fail(f"{where}: summary must start with [{status}]")
        stories = set(op.get("x-veya-stories", []))
        referenced |= stories
        for story in stories - MUST:
            fail(f"{where}: references non-Must story {story}")
        secured = op.get("security", spec.get("security")) != []
        responses = op.get("responses", {})
        if secured and "401" not in responses:
            fail(f"{where}: secured operation without 401")
        params = {resolve(spec, p)["name"] for p in op.get("parameters", [])}
        for name in re.findall(r"{(\w+)}", path):
            if name not in params:
                fail(f"{where}: path parameter {name} not declared")
        if params & OWNED_PATH_PARAMS and not {"403", "404"} <= set(responses):
            fail(f"{where}: operation on owned resource must document 403 and 404")
        for case in op.get("x-veya-postman", []):
            if str(case["expect"]) not in responses:
                fail(f"{where}: Postman case '{case['name']}' expects undocumented {case['expect']}")
    missing = (MUST - UI_OR_CI_ONLY) - referenced
    if missing:
        fail(f"Must stories without any operation: {sorted(missing)}")
    return referenced


def check_generated(spec):
    expected = {(m.upper(), p) for p, m, _ in operations(spec)}
    if POSTMAN_FILE.exists():
        collection = json.loads(POSTMAN_FILE.read_text(encoding="utf-8"))
        if "schema.getpostman.com/json/collection/v2.1.0" not in collection["info"]["schema"]:
            fail("Postman collection is not v2.1")
        found = set()

        def walk(items):
            for item in items:
                if "item" in item:
                    walk(item["item"])
                else:
                    match = re.search(r"operationId: `(\w+)`", item["request"].get("description", ""))
                    if not match:
                        fail(f"Postman request without operationId: {item['name']}")
                    op_path = item["request"]["description"].split("`")[3]
                    found.add((item["request"]["method"], op_path))
                    if item["request"].get("auth", {}).get("type") == "bearer":
                        pass

        walk(collection["item"])
        if found != expected:
            fail(f"Postman vs OpenAPI mismatch: missing {sorted(expected - found)}, extra {sorted(found - expected)}")
        env = json.loads(ENV_FILE.read_text(encoding="utf-8"))
        for value in env["values"]:
            if value["key"] in ("accessToken", "otherAccessToken", "demoPassword") and value["value"]:
                fail(f"environment variable {value['key']} must be empty (no secrets)")
    else:
        fail("Postman collection not generated")
    if CATALOGUE_FILE.exists():
        text = CATALOGUE_FILE.read_text(encoding="utf-8")
        for method, path in expected:
            if f"`{method} {path}`" not in text:
                fail(f"API_ENDPOINTS.md does not document {method} {path}")
    else:
        fail("API_ENDPOINTS.md not generated")


def main() -> int:
    spec = yaml.safe_load(SPEC_FILE.read_text(encoding="utf-8"))
    try:
        validate_openapi(spec)
        print("1. OpenAPI 3.1 structure: valid")
    except Exception as exc:  # noqa: BLE001 - report any validator failure
        fail(f"OpenAPI validation: {exc}")
    before = len(errors)
    check_examples(spec)
    print(f"2. Examples vs schemas: {'ok' if len(errors) == before else f'{len(errors) - before} problem(s)'}")
    before = len(errors)
    referenced = check_metadata(spec)
    ops = list(operations(spec))
    counts = {s: sum(1 for _, _, o in ops if o.get("x-veya-status") == s) for s in sorted(STATUSES)}
    print(f"3. Metadata: {'ok' if len(errors) == before else 'problems'}; operations={len(ops)} {counts}")
    print(f"4. Stories referenced: {len(referenced)} - {', '.join(sorted(referenced))}")
    before = len(errors)
    check_generated(spec)
    print(f"5. Postman + catalogue vs OpenAPI: {'ok' if len(errors) == before else 'problems'}")
    for message in errors:
        print("  ERROR:", message)
    print("RESULT:", "PASSED" if not errors else f"FAILED ({len(errors)} error(s))")
    return 0 if not errors else 1


if __name__ == "__main__":
    sys.exit(main())
