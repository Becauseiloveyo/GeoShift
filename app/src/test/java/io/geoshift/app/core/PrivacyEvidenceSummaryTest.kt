package io.geoshift.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyEvidenceSummaryTest {
    private val profile = GeoProfile(
        targetPackage = "com.example.probe", countryCode = "US",
        latitude = 34.05, longitude = -118.25,
    )

    @Test fun perfectConfigurationDoesNotImplySuccessfulProbe() {
        val result = PrivacyEvidence.summarize(
            profile, NativeLocationAccess.APPROXIMATE, emptyList())
        assertTrue(result.configurationScore in 0..100)
        assertEquals(ProbeOutcome.NOT_RUN, result.probeOutcome)
        assertEquals(0, result.rewrittenSurfaces)
        assertEquals(0, result.hitSurfaces)
    }

    @Test fun importedRewriteLogIsNotAnActualApiResult() {
        val observations = listOf(HookAuditEvent(
            packageName = "com.example.probe", surface = "location_getter",
            outcome = AuditOutcome.REWRITTEN, identityVerified = true,
        ))
        val result = PrivacyEvidence.summarize(
            profile, NativeLocationAccess.PRECISE, observations)
        assertEquals(1, result.rewrittenSurfaces)
        assertEquals(ProbeOutcome.NOT_RUN, result.probeOutcome)
    }

    @Test fun otherAppOrUnknownIdentityDoesNotCountAsVerifiedHit() {
        val records = listOf(
            HookAuditEvent("com.example.other", "framework_last", AuditOutcome.REWRITTEN, true),
            HookAuditEvent("com.example.probe", "framework_last", AuditOutcome.HIT, false),
        )
        val result = PrivacyEvidence.summarize(profile, NativeLocationAccess.DENIED, records)
        assertEquals(0, result.rewrittenSurfaces)
        assertEquals(0, result.hitSurfaces)
        assertEquals(ProbeOutcome.NOT_RUN, result.probeOutcome)
    }

    @Test fun explicitFailureRemainsFailureAndIndependentOfScore() {
        val result = PrivacyEvidence.summarize(
            profile, NativeLocationAccess.UNKNOWN,
            listOf(HookAuditEvent("com.example.probe", "wifi", AuditOutcome.FAILED, false)),
        )
        assertEquals(1, result.failedSurfaces)
        assertEquals(ProbeOutcome.NOT_RUN, result.probeOutcome)
    }
}
