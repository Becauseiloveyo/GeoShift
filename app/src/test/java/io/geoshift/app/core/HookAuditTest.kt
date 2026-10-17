package io.geoshift.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HookAuditTest {
    @Test fun validEventRoundTripWithLogcatPrefix() {
        val event = HookAuditEvent("com.example.map", "framework_last", AuditOutcome.REWRITTEN, true)
        val message = HookAudit.encode(event)!!
        assertEquals(event, HookAudit.parseLine("2026-10-17 I/GeoShiftAudit: $message"))
        assertFalse(message.contains("latitude"))
        assertEquals(event, HookAudit.parseLine("I/GeoShiftAudit: $message\\r"))
    }

    @Test fun malformedAndUnrecognizedFieldsAreRejected() {
        assertNull(HookAudit.parseLine("irrelevant"))
        assertNull(HookAudit.parseLine("GS_AUDIT_V1 pkg=com.example.map surface=unknown outcome=HIT identity=verified"))
        assertNull(HookAudit.parseLine("GS_AUDIT_V1 pkg=com.example.map surface=framework_last outcome=HIT identity=verified lat=1"))
        assertNull(HookAudit.parseLine("GS_AUDIT_V1 pkg=com.example.map surface=framework_last outcome=HIT identity=maybe"))
        assertNull(HookAudit.parseLine("GS_AUDIT_V1 pkg=../../bad surface=framework_last outcome=HIT identity=verified"))
    }

    @Test fun staleUnknownIdentityDoesNotCountAsVerifiedRewrite() {
        val pkg = "com.example.map"
        val events = listOf(
            HookAuditEvent(pkg, "system_callback", AuditOutcome.INSTALLED, false),
            HookAuditEvent(pkg, "system_callback", AuditOutcome.HIT, false),
            HookAuditEvent(pkg, "system_callback", AuditOutcome.REWRITTEN, false),
        )
        assertEquals(HookAuditStatus.INSTALLED, HookAudit.summary(events, pkg, "system_callback"))
        assertEquals(HookAuditStatus.NOT_VERIFIED, HookAudit.summary(events, "com.example.other", "system_callback"))
        assertEquals(HookAuditStatus.NOT_VERIFIED, HookAudit.summary(events, pkg, "wifi"))
    }

    @Test fun verifiedHitsRewritesAndFailuresHaveHonestStates() {
        val pkg = "com.example.map"
        val base = listOf(HookAuditEvent(pkg, "location_getter", AuditOutcome.INSTALLED, false))
        assertEquals(HookAuditStatus.INSTALLED, HookAudit.summary(base, pkg, "location_getter"))
        val hit = HookAuditEvent(pkg, "location_getter", AuditOutcome.HIT, true)
        assertEquals(HookAuditStatus.HIT, HookAudit.summary(base + hit, pkg, "location_getter"))
        val changed = HookAuditEvent(pkg, "location_getter", AuditOutcome.REWRITTEN, true)
        assertEquals(HookAuditStatus.REWRITTEN, HookAudit.summary(base + hit + changed, pkg, "location_getter"))
        assertEquals(HookAuditStatus.FAILED, HookAudit.summary(
            base + changed + HookAuditEvent(pkg, "location_getter", AuditOutcome.FAILED, false),
            pkg, "location_getter",
        ))
    }

    @Test fun documentIsBoundedAndDeduplicated() {
        val marker = HookAudit.encode(HookAuditEvent("com.example.map", "location_getter", AuditOutcome.HIT, true))!!
        assertEquals(1, HookAudit.parseDocument("$marker\n$marker\nnot diagnostic").size)
        val error = runCatching { HookAudit.parseDocument("x".repeat(HookAudit.MAX_DOCUMENT_CHARS + 1)) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }
}
