package io.geoshift.probe

import org.junit.Assert.*
import org.junit.Test

class ProbeReportTest {
    @Test fun redactedReportNeverIncludesRawSensitiveSamples() {
        val doc = ProbeReport(
            session = "owned_lab_2026",
            packageName = "io.geoshift.probe.target",
            role = "target",
            phase = ProbePhase.active,
            sdk = 37,
            manufacturer = "Lab",
            model = "Test",
            appVersion = "0.5-g08",
            sourceRevision = "LOCAL_UNVERIFIED",
            locationGrant = "FINE",
            recordedAtUtc = "2026-10-18T00:00:00Z",
            observations = listOf(ProbeObservation(
                api = "framework", method = "getCurrentLocation",
                expected = "TARGET_MATCH", actual = ProbeActual.MATCH_TARGET,
                verdict = ProbeVerdict.PASS, fingerprint = "a".repeat(32),
            )),
        )
        val json = ProbeReportCodec.encode(doc)
        assertTrue(json.contains("MATCH_TARGET"))
        assertTrue(json.contains("redacted"))
        assertFalse(json.contains("latitude"))
        assertFalse(json.contains("longitude"))
        assertFalse(json.contains("ssid"))
        assertFalse(json.contains("bssid"))
        assertFalse(json.contains("cellId"))
        assertFalse(json.contains("private-network"))
        assertFalse(json.contains("api_key"))
    }

    @Test fun invalidFingerprintRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ProbeObservation("framework", "getCurrentLocation", "TARGET_MATCH",
                ProbeActual.PRESENT, fingerprint = "not-a-fingerprint")
        }
    }

    @Test fun fingerprintNeedsSharedSecretAndNeverOutputsCoordinates() {
        assertNull(ProbeFingerprint.location("short", 35.0, 139.0))
        val fp = ProbeFingerprint.location("long-private-lab-secret", 35.0, 139.0)
        assertEquals(32, fp?.length)
        assertFalse(fp!!.contains("139"))
        assertEquals(fp, ProbeFingerprint.location("long-private-lab-secret", 35.0, 139.0))
        assertNotEquals(fp, ProbeFingerprint.location("different-private-secret", 35.0, 139.0))
    }

    @Test fun matchedTargetNeedsValidExpectedCoordinate() {
        assertTrue(ProbeFingerprint.nearExpected(35.0002, 139.0002, 35.0, 139.0))
        assertFalse(ProbeFingerprint.nearExpected(36.0, 139.0, 35.0, 139.0))
        assertFalse(ProbeFingerprint.nearExpected(Double.NaN, 139.0, 35.0, 139.0))
    }
}
