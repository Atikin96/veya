"""Fails if a runtime/compile dependency of the backend has a known vulnerability in the OSV database (osv.dev).

Usage:  (cd backend && ./mvnw -q dependency:list -DincludeScope=runtime -DoutputFile=target/deps.txt)
        python .github/scripts/osv_check.py backend/target/deps.txt
Only public Maven coordinates are sent to api.osv.dev.
"""
import json
import re
import sys
import urllib.request

PATTERN = re.compile(r"\s+([\w.\-]+):([\w.\-]+):jar:(?:[\w.\-]+:)?([\w.\-]+):(compile|runtime)")


def main(path: str) -> int:
    deps = [m.groups()[:3] for line in open(path, encoding="utf-8") if (m := PATTERN.match(line))]
    if not deps:
        print("no dependencies parsed")
        return 2
    query = {"queries": [{"package": {"ecosystem": "Maven", "name": f"{g}:{a}"}, "version": v} for g, a, v in deps]}
    request = urllib.request.Request("https://api.osv.dev/v1/querybatch", data=json.dumps(query).encode(),
                                     headers={"Content-Type": "application/json", "User-Agent": "veya-ci-check"})
    results = json.load(urllib.request.urlopen(request, timeout=60))["results"]
    hits = [(deps[i], [v["id"] for v in r.get("vulns", [])]) for i, r in enumerate(results) if r.get("vulns")]
    print(f"dependencies checked: {len(deps)}")
    for dep, ids in hits:
        print("VULNERABLE", ":".join(dep), ", ".join(ids))
    print(f"dependencies with known vulnerabilities: {len(hits)}")
    return 1 if hits else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1]))
