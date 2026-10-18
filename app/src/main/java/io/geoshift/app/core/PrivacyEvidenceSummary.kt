package io.geoshift.app.core

/**
 * Configuration completeness, historical hook events and actual API/probe outcomes
 * are different dimensions. No numerical health score or imported log can
 * magically turn a NOT_RUN probe into a PASS.
 */
enum class ProbeOutcome { NOT_RUN, PASS, FAIL }

data class PrivacyEvidenceSummary(
    val configurationScore: Int,
    val nativePermission: NativeLocationAccess,
    val installedSurfaces: Int,
    val hitSurfaces: Int,
    val rewrittenSurfaces: Int,
    val failedSurfaces: Int,
    val probeOutcome: ProbeOutcome,
)

object PrivacyEvidence {
    private val observedSurfaces = listOf(
        "location_getter", "framework_last", "framework_callback", "system_last",
        "system_callback", "timezone", "locale", "geocoder", "wifi",
        "telephony", "cell_identity",
    )

    fun summarize(
        profile: GeoProfile,
        permission: NativeLocationAccess,
        events: List<HookAuditEvent>,
        probeOutcome: ProbeOutcome = ProbeOutcome.NOT_RUN,
    ): PrivacyEvidenceSummary {
        val states = observedSurfaces.map { surface ->
            HookAudit.summary(events, profile.targetPackage, surface)
        }
        return PrivacyEvidenceSummary(
            configurationScore = ProfileHealth.evaluate(profile).score,
            nativePermission = permission,
            installedSurfaces = states.count { it == HookAuditStatus.INSTALLED },
            hitSurfaces = states.count { it == HookAuditStatus.HIT },
            rewrittenSurfaces = states.count { it == HookAuditStatus.REWRITTEN },
            failedSurfaces = states.count { it == HookAuditStatus.FAILED },
            probeOutcome = probeOutcome,
        )
    }
}
