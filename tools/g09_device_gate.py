#!/usr/bin/env python3
"""G09 owned-device acceptance preparation. Never changes the connected device.

Subcommands:
  manifest --apk-root <dir> --revision <40 hex SHA> --output <file>
  verify-manifest --apk-root <dir> --manifest <file>
  preflight --output <file> [--serial <ADB serial>]
  review --preflight <file> --matrix <file> --reports <eight JSON files> --output <file>

The reviewer checks provenance and user-reported evidence but CANNOT attest a
device, Hook correctness, absence of leaks, or genuine 30-minute testing.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
from typing import Callable

from verify_probe_reports import compare

ROLES = ("target", "control1", "control2", "control3")
PACKAGES = ("io.geoshift.app",) + tuple("io.geoshift.probe." + x for x in ROLES)
SHA = re.compile(r"^[a-f0-9]{40}$")
FILENAME = re.compile(r"^probe-(target|control1|control2|control3)-debug\.apk$")
MATRIX_CHECKS = (
    "location_permissions",
    "profile_target_sole_scope",
    "strict_disabled_in_normal_mode",
    "target_framework_and_fused",
    "three_non_target_controls",
    "null_cancel_timeout",
    "geocoder_async",
    "wifi_cell_staleness",
    "follow_vpn_profile_sync",
    "foreground_background",
    "continuous_location_30min",
    "strict_manual_timeout_stop",
    "stop_and_force_close_restore",
    "device_reboot_restore",
    "keystore_migration_recovery",
    "profile_export_redaction",
)
MAX_FILE = 500_000


def ensure(condition: bool, message: str):
    if not condition:
        raise ValueError(message)


def load_json(path: Path) -> dict:
    ensure(path.is_file() and path.stat().st_size <= MAX_FILE, "File missing or too large")
    obj = json.loads(path.read_text(encoding="utf-8"))
    ensure(isinstance(obj, dict), "Expected JSON object")
    return obj


def write_json(path: Path, obj: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(obj, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def artifact_paths(apk_root: Path) -> dict[str, Path]:
    return {role: apk_root / role / "debug" / f"probe-{role}-debug.apk"
            for role in ROLES}


def checksum(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1 << 20), b""):
            digest.update(block)
    return digest.hexdigest()


def make_manifest(apk_root: Path, revision: str) -> dict:
    ensure(bool(SHA.fullmatch(revision)), "Expected full 40-character revision")
    records = []
    for role, path in artifact_paths(apk_root).items():
        ensure(path.is_file() and path.stat().st_size > 0, "Missing nonempty APK for role " + role)
        records.append({
            "role": role, "packageName": "io.geoshift.probe." + role,
            "path": str(path.relative_to(apk_root).as_posix()),
            "bytes": path.stat().st_size, "sha256": checksum(path),
        })
    return {"schemaVersion": 1, "kind": "probe-apk-checksum-manifest",
            "sourceRevision": revision, "artifacts": records,
            "note": "Checksums bind files to this manifest, not to an authenticated signing key."}


def verify_manifest(apk_root: Path, manifest: dict) -> dict:
    ensure(manifest.get("schemaVersion") == 1 and
           manifest.get("kind") == "probe-apk-checksum-manifest",
           "Unsupported manifest")
    revision = manifest.get("sourceRevision")
    ensure(isinstance(revision, str) and bool(SHA.fullmatch(revision)), "Invalid source SHA")
    records = manifest.get("artifacts")
    ensure(isinstance(records, list) and len(records) == 4, "Exactly four APKs required")
    expected = artifact_paths(apk_root)
    seen = set()
    for record in records:
        ensure(isinstance(record, dict), "Invalid artifact record")
        role = record.get("role")
        ensure(role in ROLES and role not in seen, "Unknown or duplicate role")
        seen.add(role)
        ensure(record.get("packageName") == "io.geoshift.probe." + role,
               "Unexpected package metadata")
        path = expected[role]
        ensure(record.get("path") == path.relative_to(apk_root).as_posix(),
               "Unexpected APK path; refusing traversal")
        ensure(path.is_file() and record.get("bytes") == path.stat().st_size,
               "Missing APK or size mismatch")
        ensure(isinstance(record.get("sha256"), str) and
               checksum(path) == record["sha256"], "APK checksum mismatch")
    return {"status": "CHECKSUM_MATCH", "sourceRevision": revision,
            "verifiedArtifactCount": len(seen),
            "warning": "Package metadata has not been checked inside APK; "
                       "a hash manifest does not prove signing identity."}


def checked_adb(args: list[str], run: Callable = subprocess.run) -> str:
    try:
        proc = run(args, capture_output=True, text=True, timeout=12, check=False)
    except (OSError, subprocess.SubprocessError):
        raise ValueError("ADB_UNAVAILABLE_OR_TIMEOUT") from None
    ensure(proc.returncode == 0, "ADB_COMMAND_FAILED")
    return proc.stdout.strip()


def preflight(run: Callable = subprocess.run, serial: str | None = None) -> dict:
    """Read-only adb inspection. Never output device serial, location or package paths."""
    base = ["adb"] + (["-s", serial] if serial else [])
    try:
        devices = checked_adb(["adb", "devices"], run).splitlines()[1:]
        connected = [x.split()[0] for x in devices if len(x.split()) >= 2 and x.split()[1] == "device"]
        ensure(len(connected) == 1 if serial is None else serial in connected,
               "REQUIRE_ONE_AUTHORIZED_DEVICE")
        sdk_raw = checked_adb(base + ["shell", "getprop", "ro.build.version.sdk"], run)
        manufacturer = checked_adb(base + ["shell", "getprop", "ro.product.manufacturer"], run)
        model = checked_adb(base + ["shell", "getprop", "ro.product.model"], run)
        ensure(sdk_raw.isdigit(), "INVALID_DEVICE_SDK")
        sdk = int(sdk_raw)
        installed = {}
        for package in PACKAGES:
            response = checked_adb(base + ["shell", "pm", "path", package], run)
            installed[package] = response.startswith("package:")
        return {
            "schemaVersion": 1, "source": "read-only-adb-getprop-and-pm-path",
            "observedAtUtc": datetime.now(timezone.utc).isoformat(),
            "androidSdk": sdk, "manufacturer": manufacturer[:80], "model": model[:80],
            "installed": installed,
            "status": "READY_FOR_MANUAL_VALIDATION" if sdk == 37 and all(installed.values())
                      else "BLOCKED",
            "blockers": ([] if sdk == 37 else ["ANDROID_17_API37_REQUIRED"]) +
                        (["MISSING_EXPECTED_APK_INSTALLATIONS"] if not all(installed.values()) else []),
            "limitations": [
                "No package signing verification; only package-manager installation presence.",
                "No read of LSPosed scope, active Profile, native grants or provider-global Strict status.",
                "Never changes device permissions, packages, location or runtime settings.",
            ],
        }
    except ValueError as err:
        return {
            "schemaVersion": 1, "source": "read-only-adb-getprop-and-pm-path",
            "status": "BLOCKED", "blockers": [str(err)],
            "installed": {package: False for package in PACKAGES},
            "limitations": ["No device tests were run."],
        }


def duration_seconds(start: str, end: str) -> float:
    def parse(value: str) -> datetime:
        ensure(isinstance(value, str), "Expected ISO timestamp")
        t = datetime.fromisoformat(value.replace("Z", "+00:00"))
        ensure(t.tzinfo is not None, "Timezone required")
        return t
    return (parse(end) - parse(start)).total_seconds()


def review(preflight_doc: dict, manual: dict, reports: list[dict]) -> dict:
    """Honest output never upgrades user-asserted evidence to device attestation."""
    ensure(preflight_doc.get("schemaVersion") == 1, "Invalid preflight")
    ensure(manual.get("schemaVersion") == 1, "Invalid matrix schema")
    ensure(manual.get("evidenceOrigin") == "USER_REPORTED", "Matrix must identify its origin")
    checks = manual.get("checks")
    ensure(isinstance(checks, dict) and set(checks) == set(MATRIX_CHECKS),
           "Every G09 case must have one status")
    ensure(all(value in {"PASS", "FAIL", "NOT_RUN"} for value in checks.values()),
           "Invalid case status")
    series = manual.get("continuous30")
    ensure(isinstance(series, dict), "Missing continuity evidence")
    count = series.get("observedSampleCount")
    ensure(type(count) is int and 0 <= count <= 10000, "Invalid sample count")
    observed = duration_seconds(series["startUtc"], series["endUtc"])
    continuous_valid = observed >= 30 * 60 and count >= 6 and (
        series.get("unexpectedReturnCount") == 0
    )
    if checks["continuous_location_30min"] == "PASS":
        ensure(continuous_valid, "Cannot claim 30min PASS from insufficient samples")

    comparison = compare(reports)
    if checks["three_non_target_controls"] == "PASS":
        ensure(comparison["overall"] == "PASS", "Control PASS needs compatible eight-report result")
    if checks["target_framework_and_fused"] == "PASS":
        target_checks = [x for x in comparison["checks"] if x["role"] == "target"]
        ensure(any(x["status"] == "PASS" for x in target_checks),
               "Target PASS needs an observed target location match")
    if preflight_doc.get("status") != "READY_FOR_MANUAL_VALIDATION":
        outcome = "BLOCKED"
    elif "FAIL" in checks.values() or comparison["overall"] == "FAIL":
        outcome = "FAIL_REPORTED"
    elif comparison["overall"] != "PASS" or "NOT_RUN" in checks.values():
        outcome = "NOT_RUN"
    else:
        outcome = "USER_REPORTED_COMPLETE_REQUIRES_INDEPENDENT_REVIEW"
    return {
        "schemaVersion": 1, "kind": "g09-owned-device-review",
        "deviceGate": outcome, "evidenceOrigin": "USER_REPORTED",
        "androidSdk": preflight_doc.get("androidSdk"),
        "probeOverall": comparison["overall"],
        "caseCounts": {name: list(checks.values()).count(name) for name in ("PASS", "FAIL", "NOT_RUN")},
        "continuityEvidence": {"observedSeconds": int(observed), "samples": count,
                               "meetsMinimum": continuous_valid},
        "warning": "User-reported outcomes and exported JSON are not hardware-attested. "
                   "Do not mark G09 DONE without independent phone-side inspection, "
                   "LSPosed scope review, APK signing checks and reproducible logs.",
    }


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="action", required=True)
    create = sub.add_parser("manifest")
    create.add_argument("--apk-root", type=Path, required=True)
    create.add_argument("--revision", required=True)
    create.add_argument("--output", type=Path, required=True)
    verify = sub.add_parser("verify-manifest")
    verify.add_argument("--apk-root", type=Path, required=True)
    verify.add_argument("--manifest", type=Path, required=True)
    readiness = sub.add_parser("preflight")
    readiness.add_argument("--output", type=Path, required=True)
    readiness.add_argument("--serial")
    gate = sub.add_parser("review")
    gate.add_argument("--preflight", type=Path, required=True)
    gate.add_argument("--matrix", type=Path, required=True)
    gate.add_argument("--reports", type=Path, nargs=8, required=True)
    gate.add_argument("--output", type=Path, required=True)
    args = parser.parse_args(argv)
    try:
        if args.action == "manifest":
            result = make_manifest(args.apk_root, args.revision)
            write_json(args.output, result)
        elif args.action == "verify-manifest":
            result = verify_manifest(args.apk_root, load_json(args.manifest))
            print(json.dumps(result, indent=2))
        elif args.action == "preflight":
            result = preflight(serial=args.serial)
            write_json(args.output, result)
            return 0 if result["status"] == "READY_FOR_MANUAL_VALIDATION" else 3
        else:
            docs = [load_json(p) for p in args.reports]
            result = review(load_json(args.preflight), load_json(args.matrix), docs)
            write_json(args.output, result)
            return 0 if result["deviceGate"] == "USER_REPORTED_COMPLETE_REQUIRES_INDEPENDENT_REVIEW" else 3
        return 0
    except (OSError, ValueError, TypeError, KeyError, json.JSONDecodeError) as error:
        print("G09_INVALID_OR_UNVERIFIED_INPUT: " + type(error).__name__, file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
