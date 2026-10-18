#!/usr/bin/env python3
"""Verify user-exported owned-device GeoShift Probe evidence.

Run: python3 tools/verify_probe_reports.py path/to/eight-reports/*.json
The JSON is untrusted. A valid report is evidence of observations, not proof of
the operating system, report authenticity, location privacy or account safety.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

ROLES = ("target", "control1", "control2", "control3")
PHASES = ("baseline", "active")
OUTCOMES = {
    "MATCH_TARGET", "DIFFERENT", "PRESENT", "ABSENT", "DENIED",
    "TIMEOUT", "ERROR", "UNSUPPORTED", "NOT_RUN",
}
VERDICTS = {"PASS", "FAIL", "NOT_RUN"}
ALLOWED_APIS = {"framework", "fused", "geocoder", "wifi", "telephony"}
LOCATION_APIS = {"framework", "fused"}
HEX32 = re.compile(r"^[0-9a-f]{32}$")
SESSION = re.compile(r"^[A-Za-z0-9_-]{6,32}$")
SHA = re.compile(r"^(LOCAL_UNVERIFIED|[0-9a-f]{40})$")
API_METHOD = re.compile(r"^[A-Za-z][A-Za-z0-9_]{1,60}$")
SENSITIVE_FIELDS = {
    "latitude", "longitude", "ssid", "bssid", "cellid", "cell_id", "api_key",
    "token", "auth", "ip", "coordinates", "secret", "address", "raw",
}


def _require(ok: bool, reason: str):
    if not ok:
        raise ValueError(reason)


def parse_report(doc: dict) -> dict:
    _require(isinstance(doc, dict), "Invalid report")
    _require(doc.get("schemaVersion") == 1, "Unknown report version")
    role = doc.get("role")
    phase = doc.get("phase")
    _require(role in ROLES and phase in PHASES, "Unknown role or phase")
    _require(doc.get("packageName") == f"io.geoshift.probe.{role}", "Role/package mismatch")
    _require(isinstance(doc.get("session"), str) and
             bool(SESSION.fullmatch(doc["session"])), "Invalid session")
    _require(isinstance(doc.get("androidSdk"), int) and
             doc["androidSdk"] >= 29, "Invalid Android API")
    _require(isinstance(doc.get("geoshiftSourceRevision"), str) and
             bool(SHA.fullmatch(doc["geoshiftSourceRevision"])), "Invalid source revision")
    _require(doc.get("redacted") is True, "Not a redacted report")
    _require(isinstance(doc.get("collectedAtUtc"), str) and
             doc["collectedAtUtc"].endswith("Z"), "Invalid timestamp")
    _require(isinstance(doc.get("observations"), list), "Missing observations")
    _require(len(doc["observations"]) <= 40, "Too many observations")
    _require(not (set(doc) & SENSITIVE_FIELDS), "Sensitive top-level fields")
    checked = {}
    for sample in doc["observations"]:
        _require(isinstance(sample, dict), "Invalid observation")
        _require(not (set(sample) & SENSITIVE_FIELDS), "Sensitive observation field")
        api = sample.get("api")
        method = sample.get("method")
        _require(api in ALLOWED_APIS and isinstance(method, str) and
                 bool(API_METHOD.fullmatch(method)), "Invalid API method")
        _require(sample.get("actual") in OUTCOMES and sample.get("status") in VERDICTS,
                 "Invalid outcome/verdict")
        _require(sample.get("expected") in {"TARGET_MATCH", "UNCHANGED_FROM_BASELINE", "OBSERVE_ONLY"},
                 "Invalid expected classification")
        expected = "UNCHANGED_FROM_BASELINE" if role != "target" else (
            "TARGET_MATCH" if api in LOCATION_APIS and
            sample.get("expected") == "TARGET_MATCH" else "OBSERVE_ONLY"
        )
        _require(sample["expected"] == expected, "Mismatched expectation")
        fp = sample.get("fingerprint")
        _require(fp is None or (isinstance(fp, str) and bool(HEX32.fullmatch(fp))),
                 "Malformed fingerprint")
        _require(fp is None or sample["actual"] in {"MATCH_TARGET", "DIFFERENT", "PRESENT"},
                 "Fingerprint on unavailable result")
        identity = (api, method)
        _require(identity not in checked, "Duplicate API evidence")
        checked[identity] = sample
    return doc


def compare(reports: list[dict]) -> dict:
    parsed = [parse_report(x) for x in reports]
    docs = {}
    for doc in parsed:
        key = (doc["role"], doc["phase"])
        _require(key not in docs, "Duplicate role+phase")
        docs[key] = doc
    _require(len(docs) == 8 and all((r, p) in docs for r in ROLES for p in PHASES),
             "Require four roles in BOTH baseline and active phases (8 reports)")
    sessions = {d["session"] for d in parsed}
    _require(len(sessions) == 1, "Reports are from different sessions")
    _require(len({d["androidSdk"] for d in parsed}) == 1, "Android API mismatch")
    _require(len({(d.get("manufacturer"), d.get("model")) for d in parsed}) == 1,
             "Device model mismatch")
    _require(len({d["geoshiftSourceRevision"] for d in parsed}) == 1,
             "GeoShift revision mismatch")

    checks: list[dict] = []
    target = docs["target", "active"]
    measured_target = False
    for row in target["observations"]:
        if row["api"] not in LOCATION_APIS or row["expected"] != "TARGET_MATCH":
            continue
        status = ("PASS" if row["actual"] == "MATCH_TARGET" else
                  "FAIL" if row["actual"] == "DIFFERENT" else "NOT_RUN")
        if status == "PASS":
            measured_target = True
        checks.append({
            "role": "target", "api": row["api"], "method": row["method"],
            "expected": "TARGET_MATCH", "actual": row["actual"], "status": status,
        })

    for role in ROLES[1:]:
        base = {(r["api"], r["method"]): r for r in docs[role, "baseline"]["observations"]}
        active = {(r["api"], r["method"]): r for r in docs[role, "active"]["observations"]}
        for api, method in sorted(base.keys() | active.keys()):
            previous, current = base.get((api, method)), active.get((api, method))
            fingerprints = (
                previous is not None and current is not None and
                previous.get("fingerprint") is not None and
                current.get("fingerprint") is not None
            )
            status = (
                "PASS" if fingerprints and previous["fingerprint"] == current["fingerprint"]
                else "FAIL" if fingerprints else "NOT_RUN"
            )
            checks.append({
                "role": role, "api": api, "method": method,
                "expected": "UNCHANGED_FROM_BASELINE",
                "actual": "MATCH" if status == "PASS" else
                          "CHANGED" if status == "FAIL" else "INSUFFICIENT_EVIDENCE",
                "status": status,
            })
    controls_have_location = all(
        any(c["role"] == role and c["api"] in LOCATION_APIS and c["status"] == "PASS"
            for c in checks) for role in ROLES[1:]
    )
    overall = (
        "FAIL" if any(c["status"] == "FAIL" for c in checks)
        else "PASS" if measured_target and controls_have_location
        else "NOT_RUN"
    )
    return {
        "schemaVersion": 1,
        "method": "untrusted-user-exported-device-probe-comparison",
        "session": next(iter(sessions)),
        "geoshiftSourceRevision": parsed[0]["geoshiftSourceRevision"],
        "androidSdk": parsed[0]["androidSdk"],
        "overall": overall,
        "checks": checks,
        "limitations": [
            "User-exported reports and session secrets are not cryptographically attested.",
            "HMAC fingerprints reflect quantized values and can change with normal movement or sensor noise.",
            "PASS means only selected measured APIs matched criteria, not zero location leakage.",
            "Missing, unsupported, denied, timed-out or uncomparable observations are NOT_RUN.",
        ],
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("reports", type=Path, nargs="+", help="Eight baseline/active role JSON files")
    parser.add_argument("-o", "--output", type=Path, help="Redacted comparison JSON")
    args = parser.parse_args(argv)
    try:
        docs = []
        for path in args.reports:
            _require(path.stat().st_size <= 200_000, "Input report too large")
            docs.append(json.loads(path.read_text(encoding="utf-8")))
        result = compare(docs)
        rendered = json.dumps(result, ensure_ascii=False, indent=2) + "\n"
        if args.output:
            args.output.write_text(rendered, encoding="utf-8")
        else:
            print(rendered, end="")
        return 1 if result["overall"] == "FAIL" else 0
    except (OSError, ValueError, KeyError, TypeError) as error:
        print(f"INVALID_PROBE_EVIDENCE: {type(error).__name__}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
