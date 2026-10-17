package io.geoshift.app.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StrictSessionPolicyTest {
    @Test
    fun explicitGlobalAcknowledgementIsRequired() {
        assertFalse(StrictSessionPolicy.canArm(false, 120_000))
        assertFalse(StrictSessionPolicy.canArm(true, 120_001))
        assertFalse(StrictSessionPolicy.canArm(true, 0))
        assertTrue(StrictSessionPolicy.canArm(true, 1))
        assertTrue(StrictSessionPolicy.canArm(true, 120_000))
    }

    @Test
    fun exactTimeoutBoundaryIsInactive() {
        assertTrue(StrictSessionPolicy.isActive(10_001, 10_000, 2_000))
        assertTrue(StrictSessionPolicy.isActive(11_999, 10_000, 2_000))
        assertFalse(StrictSessionPolicy.isActive(12_000, 10_000, 2_000))
        assertFalse(StrictSessionPolicy.isActive(12_001, 10_000, 2_000))
    }

    @Test
    fun invalidOrUnboundedSessionsFailClosed() {
        assertFalse(StrictSessionPolicy.isActive(15_000, 0, 2_000))
        assertFalse(StrictSessionPolicy.isActive(9_999, 10_000, 2_000))
        assertFalse(StrictSessionPolicy.isActive(10_100, 10_000, 0))
        assertFalse(StrictSessionPolicy.isActive(10_100, 10_000, -1))
        assertFalse(
            StrictSessionPolicy.isActive(
                10_100, 10_000, StrictSessionPolicy.MAX_DURATION_MS + 1,
            ),
        )
    }

    @Test
    fun sameBootCommandIsAcceptedButRebootAndClockDriftFailClosed() {
        assertTrue(StrictSessionPolicy.isCurrentBootCommand(
            nowWallMs = 1_004_000,
            nowElapsedMs = 54_000,
            issuedWallMs = 1_000_000,
            issuedElapsedMs = 50_000,
            durationMs = 10_000,
        ))
        assertFalse(StrictSessionPolicy.isCurrentBootCommand(
            nowWallMs = 1_004_000,
            nowElapsedMs = 2_000,
            issuedWallMs = 1_000_000,
            issuedElapsedMs = 50_000,
            durationMs = 10_000,
        ))
        assertFalse(StrictSessionPolicy.isCurrentBootCommand(
            nowWallMs = 1_100_000,
            nowElapsedMs = 54_000,
            issuedWallMs = 1_000_000,
            issuedElapsedMs = 50_000,
            durationMs = 10_000,
        ))
        assertFalse(StrictSessionPolicy.isCurrentBootCommand(
            nowWallMs = 999_999,
            nowElapsedMs = 54_000,
            issuedWallMs = 1_000_000,
            issuedElapsedMs = 50_000,
            durationMs = 10_000,
        ))
    }

    @Test
    fun rebootWithLargeElapsedClockButDifferentBootEpochIsRejected() {
        assertFalse(StrictSessionPolicy.isCurrentBootCommand(
            nowWallMs = 2_000_000,
            nowElapsedMs = 51_000,
            issuedWallMs = 1_000_000,
            issuedElapsedMs = 50_000,
            durationMs = 5_000,
        ))
    }
}
