package io.geoshift.app.core

/**
 * Pure, monotonic-clock policy for the explicit system-wide location test session.
 *
 * This is a bounded, developer-only shared-provider experiment, NOT per-app isolation.
 * Comparisons are performed at the hook invocation site, never only when commands arrive.
 */
internal object StrictSessionPolicy {
    const val MAX_DURATION_MS = 120_000L
    const val DEFAULT_DURATION_MS = 120_000L
    private const val CLOCK_SKEW_TOLERANCE_MS = 10_000L

    /** Explicit acknowledgement is mandatory even for a correctly bounded lease. */
    fun canArm(acknowledgeGlobalImpact: Boolean, durationMs: Long): Boolean =
        acknowledgeGlobalImpact && durationMs in 1L..MAX_DURATION_MS

    fun isActive(nowElapsedMs: Long, issuedElapsedMs: Long, durationMs: Long): Boolean =
        issuedElapsedMs > 0L &&
            durationMs in 1L..MAX_DURATION_MS &&
            nowElapsedMs >= issuedElapsedMs &&
            nowElapsedMs - issuedElapsedMs < durationMs

    /**
     * Elapsed time must match the current boot. Wall time is a secondary cross-boot check
     * for late-starting brokers; a changed wall clock fails closed.
     */
    fun isCurrentBootCommand(
        nowWallMs: Long,
        nowElapsedMs: Long,
        issuedWallMs: Long,
        issuedElapsedMs: Long,
        durationMs: Long,
    ): Boolean {
        if (!isActive(nowElapsedMs, issuedElapsedMs, durationMs)) return false
        if (issuedWallMs <= 0L || nowWallMs < issuedWallMs) return false
        val wallAge = nowWallMs - issuedWallMs
        val elapsedAge = nowElapsedMs - issuedElapsedMs
        return wallAge >= 0L && wallAge - elapsedAge in
            -CLOCK_SKEW_TOLERANCE_MS..CLOCK_SKEW_TOLERANCE_MS
    }
}
