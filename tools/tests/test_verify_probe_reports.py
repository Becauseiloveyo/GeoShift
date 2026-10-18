import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from verify_probe_reports import compare, parse_report


ROLES = ("target", "control1", "control2", "control3")


def report(role, phase, target_match=True, unchanged=True):
    expected = "TARGET_MATCH" if role == "target" else "UNCHANGED_FROM_BASELINE"
    actual = "MATCH_TARGET" if role == "target" and target_match else (
        "DIFFERENT" if role == "target" else "PRESENT")
    fp = "a" * 32 if phase == "baseline" or unchanged else "b" * 32
    return {
        "schemaVersion": 1, "session": "lab_20261018",
        "packageName": "io.geoshift.probe." + role,
        "role": role, "phase": phase, "androidSdk": 37,
        "manufacturer": "owned-device", "model": "lab",
        "geoshiftSourceRevision": "a" * 40, "collectedAtUtc": "2026-10-18T00:00:00Z",
        "redacted": True, "locationGrant": "FINE",
        "observations": [
            {"api": "framework", "method": "getCurrentLocation",
             "expected": expected,
             "actual": actual,
             "status": "PASS" if role == "target" and target_match else "NOT_RUN",
             "fingerprint": fp}
        ],
    }


class VerifyProbeReportsTest(unittest.TestCase):
    def setUp(self):
        self.reports = [report(role, phase) for role in ROLES
                        for phase in ("baseline", "active")]

    def test_full_eight_reports_can_pass_measured_path_only(self):
        result = compare(self.reports)
        self.assertEqual("PASS", result["overall"])
        self.assertEqual(3, len([c for c in result["checks"]
                                 if c["role"].startswith("control") and c["status"] == "PASS"]))

    def test_one_control_location_mismatch_fails(self):
        self.reports[5] = report("control2", "active", unchanged=False)
        self.assertEqual("FAIL", compare(self.reports)["overall"])

    def test_target_mismatch_fails(self):
        self.reports[1] = report("target", "active", target_match=False)
        self.assertEqual("FAIL", compare(self.reports)["overall"])

    def test_control_without_comparable_result_never_passes(self):
        self.reports[3]["observations"][0]["fingerprint"] = None
        self.reports[3]["observations"][0]["actual"] = "ABSENT"
        self.assertEqual("NOT_RUN", compare(self.reports)["overall"])

    def test_no_target_expected_coordinate_stays_not_run(self):
        self.reports[1]["observations"][0]["expected"] = "OBSERVE_ONLY"
        self.assertEqual("NOT_RUN", compare(self.reports)["overall"])

    def test_requires_three_controls_and_baselines(self):
        with self.assertRaises(ValueError):
            compare(self.reports[:-1])

    def test_role_package_mismatch_rejected(self):
        self.reports[3]["packageName"] = "io.geoshift.probe.target"
        with self.assertRaises(ValueError):
            compare(self.reports)

    def test_sensitive_raw_fields_rejected(self):
        self.reports[0]["observations"][0]["latitude"] = 22.2
        with self.assertRaises(ValueError):
            compare(self.reports)

    def test_mismatched_source_revision_rejected(self):
        self.reports[2]["geoshiftSourceRevision"] = "b" * 40
        with self.assertRaises(ValueError):
            compare(self.reports)

    def test_permission_change_between_phases_rejected(self):
        self.reports[3]["locationGrant"] = "COARSE"
        with self.assertRaises(ValueError):
            compare(self.reports)

    def test_duplicate_api_sample_rejected(self):
        x = self.reports[0]
        x["observations"].append(dict(x["observations"][0]))
        with self.assertRaises(ValueError):
            parse_report(x)


if __name__ == "__main__":
    unittest.main()
