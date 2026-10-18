package io.geoshift.app.core

/**
 * The user-facing export is deliberately less detailed than the internal v5
 * atomic snapshot. Remove observed VPN exit IP and radio device identifiers.
 *
 * The configured simulated coordinates are retained because an exported
 * location profile must be usable; they are user-supplied/GeoIP-derived
 * target locations, NOT a reading of the phone's actual GPS hardware.
 * Users should still treat target coordinates as potentially sensitive.
 */
object ProfileExportPolicy {
    fun sanitized(profile: GeoProfile): GeoProfile = profile.copy(
        lastSyncIp = "",
        lastSyncAtEpochMs = 0L,
        lastSyncCity = "",
        lastSyncRegion = "",
        wifiEnabled = false,
        wifiSsid = "",
        wifiBssid = "",
        wifiAccessPoints = emptyList(),
        telephonyEnabled = false,
        mcc = "",
        mnc = "",
        operatorName = "",
        cellRadio = "",
        cellAreaCode = -1L,
        cellId = -1L,
        radioSource = "",
        radioStale = true,
        geoSource = "",
    )
}
