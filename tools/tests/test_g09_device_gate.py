import sys
import tempfile
from pathlib import Path
from types import SimpleNamespace
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from g09_device_gate import (
    MATRIX_CHECKS, PACKAGES, make_manifest, preflight, review, verify_manifest,
)
from test_verify_probe_reports import ROLES, report


class G09DeviceGateTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = Path(self.temp.name)
        for role in ROLES:
            file = self.root / role / "debug" / f"probe-{role}-debug.apk"
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_bytes(b"TEST-ONLY-NOT-REAL-APK-" + role.encode())
        self.reports = [
            report(role, phase) for role in ROLES for phase in ("baseline", "active")
        ]
        self.preflight = {
            "schemaVersion": 1, "status": "READY_FOR_MANUAL_VALIDATION",
            "androidSdk": 37,
        }
        self.matrix = {
            "schemaVersion": 1, "evidenceOrigin": "USER_REPORTED",
            "checks": {key: "PASS" for key in MATRIX_CHECKS},
            "continuous30": {
                "startUtc": "2026-10-18T01:00:00Z",
                "endUtc": "2026-10-18T01:31:00Z",
                "observedSampleCount": 7,
                "unexpectedReturnCount": 0,
            },
        }

    def tearDown(self):
        self.temp.cleanup()

    def test_manifest_roundtrip_exactly_four_artifacts(self):
        manifest = make_manifest(self.root, "a" * 40)
        self.assertEqual(4, len(manifest["artifacts"]))
        self.assertEqual("CHECKSUM_MATCH", verify_manifest(self.root, manifest)["status"])

    def test_tampered_probe_binary_is_not_accepted(self):
        manifest = make_manifest(self.root, "a" * 40)
        (self.root / "target/debug/probe-target-debug.apk").write_bytes(b"forged")
        with self.assertRaises(ValueError):
            verify_manifest(self.root, manifest)

    def test_path_traversal_or_package_relabeling_rejected(self):
        manifest = make_manifest(self.root, "a" * 40)
        manifest["artifacts"][0]["path"] = "../../outside.apk"
        with self.assertRaises(ValueError):
            verify_manifest(self.root, manifest)

    def test_missing_role_rejected(self):
        (self.root / "control3/debug/probe-control3-debug.apk").unlink()
        with self.assertRaises(ValueError):
            make_manifest(self.root, "a" * 40)

    def test_no_devices_returns_blocked_without_disclosing_serial(self):
        def runner(args, **kwargs):
            if args == ["adb", "devices"]:
                return SimpleNamespace(returncode=0, stdout="List of devices attached\n\n")
            raise AssertionError("unexpected additional ADB call")
        result = preflight(run=runner)
        self.assertEqual("BLOCKED", result["status"])
        self.assertNotIn("deviceSerial", result)
        self.assertIn("REQUIRE_ONE_AUTHORIZED_DEVICE", result["blockers"])

    def test_read_only_device_preflight_checks_all_five_packages(self):
        invoked = []
        def runner(args, **kwargs):
            invoked.append(args)
            if args == ["adb", "devices"]:
                reply = "List of devices attached\nFAKE_TEST_SERIAL\tdevice\n"
            elif args[-3:] == ["shell", "getprop", "ro.build.version.sdk"]:
                reply = "37\n"
            elif args[-3:] == ["shell", "getprop", "ro.product.manufacturer"]:
                reply = "Xiaomi\n"
            elif args[-3:] == ["shell", "getprop", "ro.product.model"]:
                reply = "Owned test phone\n"
            elif len(args) >= 5 and args[-4:-2] == ["shell", "pm"] and args[-2] == "path":
                reply = "package:/data/app/fake/base.apk\n"
            else:
                raise AssertionError("Unexpected command: " + repr(args))
            return SimpleNamespace(returncode=0, stdout=reply)
        result = preflight(run=runner)
        self.assertEqual("READY_FOR_MANUAL_VALIDATION", result["status"])
        self.assertTrue(all(result["installed"][pkg] for pkg in PACKAGES))
        self.assertNotIn("FAKE_TEST_SERIAL", str(result))
        self.assertFalse(any("install" in args or "grant" in args for args in invoked))

    def test_all_manual_checks_are_only_reported_complete(self):
        result = review(self.preflight, self.matrix, self.reports)
        self.assertEqual("USER_REPORTED_COMPLETE_REQUIRES_INDEPENDENT_REVIEW",
                         result["deviceGate"])
        self.assertEqual("USER_REPORTED", result["evidenceOrigin"])

    def test_missing_preflight_blocks_even_positive_user_matrix(self):
        self.preflight["status"] = "BLOCKED"
        result = review(self.preflight, self.matrix, self.reports)
        self.assertEqual("BLOCKED", result["deviceGate"])

    def test_not_run_manual_case_cannot_complete_gate(self):
        self.matrix["checks"]["keystore_migration_recovery"] = "NOT_RUN"
        self.assertEqual("NOT_RUN",
                         review(self.preflight, self.matrix, self.reports)["deviceGate"])

    def test_reported_failure_remains_failure(self):
        self.matrix["checks"]["foreground_background"] = "FAIL"
        self.assertEqual("FAIL_REPORTED",
                         review(self.preflight, self.matrix, self.reports)["deviceGate"])

    def test_thirty_minutes_needs_duration_and_samples(self):
        self.matrix["continuous30"]["endUtc"] = "2026-10-18T01:20:00Z"
        with self.assertRaises(ValueError):
            review(self.preflight, self.matrix, self.reports)
        self.matrix["continuous30"]["endUtc"] = "2026-10-18T01:31:00Z"
        self.matrix["continuous30"]["observedSampleCount"] = 1
        with self.assertRaises(ValueError):
            review(self.preflight, self.matrix, self.reports)

    def test_user_claim_of_control_pass_requires_eight_comparable_reports(self):
        self.reports[5]["observations"][0]["fingerprint"] = None
        self.reports[5]["observations"][0]["actual"] = "ABSENT"
        with self.assertRaises(ValueError):
            review(self.preflight, self.matrix, self.reports)

    def test_permission_or_origin_not_changed_by_review(self):
        self.matrix["evidenceOrigin"] = "DEVICE_ATTESTED"
        with self.assertRaises(ValueError):
            review(self.preflight, self.matrix, self.reports)

    def test_timestamp_without_timezone_rejected(self):
        self.matrix["continuous30"]["startUtc"] = "2026-10-18T01:00:00"
        with self.assertRaises(ValueError):
            review(self.preflight, self.matrix, self.reports)


if __name__ == "__main__":
    unittest.main()
