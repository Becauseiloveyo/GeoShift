package io.geoshift.app.core

data class ProfileHealthReport(
    val score: Int,
    val mapReady: Boolean,
    val wifiReady: Boolean,
    val cellularReady: Boolean,
    val wifiAccessPointCount: Int,
    val detailedCellIdentity: Boolean,
    val issues: List<ProfileDiagnostics.Issue>,
)

object ProfileHealth {
    fun evaluate(profile: GeoProfile, nowEpochMs: Long = System.currentTimeMillis()): ProfileHealthReport {
        val issues = ProfileDiagnostics.evaluate(profile, nowEpochMs)
        val accessPoints = profile.effectiveWifiAccessPoints()
        val mapReady = profile.enabled && profile.locationEnabled &&
            profile.latitude.isFinite() && profile.latitude in -90.0..90.0 &&
            profile.longitude.isFinite() && profile.longitude in -180.0..180.0
        val wifiReady = !profile.wifiEnabled || accessPoints.isNotEmpty()
        val detailedCell = profile.telephonyEnabled && profile.cellRadio.isNotBlank() &&
            profile.cellAreaCode >= 0L && profile.cellId >= 0L
        val cellularReady = !profile.telephonyEnabled ||
            (profile.mcc.isNotBlank() && profile.mnc.isNotBlank())

        var score = 100
        issues.forEach { issue ->
            score -= when (issue.severity) {
                ProfileDiagnostics.Severity.ERROR -> 22
                ProfileDiagnostics.Severity.WARNING -> 7
            }
        }
        if (profile.wifiEnabled) {
            score -= when (accessPoints.size) {
                0 -> 18
                1 -> 6
                2 -> 2
                else -> 0
            }
        }
        if (profile.telephonyEnabled && !detailedCell) score -= 6
        if (profile.locationEnabled && !profile.geocoderEnabled) score -= 2

        return ProfileHealthReport(
            score = score.coerceIn(0, 100),
            mapReady = mapReady,
            wifiReady = wifiReady,
            cellularReady = cellularReady,
            wifiAccessPointCount = accessPoints.size,
            detailedCellIdentity = detailedCell,
            issues = issues,
        )
    }
}
