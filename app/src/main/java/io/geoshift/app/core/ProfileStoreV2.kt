package io.geoshift.app.core

import android.content.SharedPreferences
import io.github.libxposed.service.XposedService

/**
 * Multi-profile storage layered on the same Remote Preferences file used by v0.1.
 * Package names are stable namespaces; new fields default safely for old installs.
 */
object ProfileStoreV2 {
    const val REMOTE_PREFS = ProfileStore.REMOTE_PREFS

    private const val KEY_PROFILE_INDEX = "v2_profile_index"
    /** One authoritative per-profile payload: readers cannot observe mixed geo/radio fields. */
    private const val KEY_ATOMIC_SNAPSHOT = "atomic_snapshot_v5"
    private const val PREFIX = "profile::"
    private const val SEPARATOR = "|"

    private fun key(packageName: String, field: String) = "$PREFIX$packageName::$field"
    fun prefixFor(packageName: String): String = "$PREFIX$packageName::"

    fun list(service: XposedService): List<GeoProfile> {
        val prefs = service.getRemotePreferences(REMOTE_PREFS)
        migrateLegacyIfNeeded(prefs)
        return packageNames(prefs).mapNotNull { load(prefs, it) }
    }

    fun listPackages(service: XposedService): List<String> {
        val prefs = service.getRemotePreferences(REMOTE_PREFS)
        migrateLegacyIfNeeded(prefs)
        return packageNames(prefs)
    }

    fun load(service: XposedService, packageName: String): GeoProfile? {
        val prefs = service.getRemotePreferences(REMOTE_PREFS)
        migrateLegacyIfNeeded(prefs)
        return load(prefs, packageName)
    }

    fun load(prefs: SharedPreferences, packageName: String): GeoProfile? {
        if (packageName.isBlank()) return null
        val atomic = prefs.getString(key(packageName, KEY_ATOMIC_SNAPSHOT), null)
        if (atomic != null) {
            // A corrupt authoritative payload must not silently fall back to potentially
            // mixed legacy keys, which could expose a stale radio profile.
            return runCatching { ProfileCodec.decode(atomic) }
                .getOrNull()
                ?.takeIf { it.targetPackage == packageName && it.validate().isEmpty() }
        }
        val storedTarget = prefs.getString(key(packageName, ProfileStore.KEY_TARGET_PACKAGE), null)
        if (storedTarget == null) {
            val legacyTarget = prefs.getString(ProfileStore.KEY_TARGET_PACKAGE, "").orEmpty()
            return if (legacyTarget == packageName) ProfileStore.load(prefs).let { legacy ->
                legacy.copy(radioStale = legacy.followVpn && (legacy.wifiEnabled || legacy.telephonyEnabled))
            } else null
        }
        val legacy = GeoProfile(
            enabled = prefs.getBoolean(key(packageName, ProfileStore.KEY_ENABLED), true),
            targetPackage = storedTarget,
            followVpn = prefs.getBoolean(key(packageName, ProfileStore.KEY_FOLLOW_VPN), false),
            timezoneEnabled = prefs.getBoolean(key(packageName, ProfileStore.KEY_TIMEZONE_ENABLED), true),
            timezoneId = prefs.getString(key(packageName, ProfileStore.KEY_TIMEZONE), java.util.TimeZone.getDefault().id).orEmpty(),
            localeEnabled = prefs.getBoolean(key(packageName, ProfileStore.KEY_LOCALE_ENABLED), true),
            localeTag = prefs.getString(key(packageName, ProfileStore.KEY_LOCALE), java.util.Locale.getDefault().toLanguageTag()).orEmpty(),
            countryCode = prefs.getString(key(packageName, ProfileStore.KEY_COUNTRY), java.util.Locale.getDefault().country).orEmpty(),
            locationEnabled = prefs.getBoolean(key(packageName, ProfileStore.KEY_LOCATION_ENABLED), true),
            latitude = prefs.getString(key(packageName, ProfileStore.KEY_LATITUDE), "0.0")?.toDoubleOrNull() ?: 0.0,
            longitude = prefs.getString(key(packageName, ProfileStore.KEY_LONGITUDE), "0.0")?.toDoubleOrNull() ?: 0.0,
            geocoderEnabled = prefs.getBoolean(key(packageName, ProfileStore.KEY_GEOCODER_ENABLED), true),
            wifiEnabled = prefs.getBoolean(key(packageName, ProfileStore.KEY_WIFI_ENABLED), false),
            wifiSsid = prefs.getString(key(packageName, ProfileStore.KEY_WIFI_SSID), "").orEmpty(),
            wifiBssid = prefs.getString(key(packageName, ProfileStore.KEY_WIFI_BSSID), "").orEmpty(),
            wifiRssiDbm = prefs.getInt(key(packageName, ProfileStore.KEY_WIFI_RSSI), -45),
            wifiAccessPoints = WifiAccessPointCodec.decode(
                prefs.getString(key(packageName, ProfileStore.KEY_WIFI_ACCESS_POINTS), null)
            ),
            telephonyEnabled = prefs.getBoolean(key(packageName, ProfileStore.KEY_TELEPHONY_ENABLED), false),
            mcc = prefs.getString(key(packageName, ProfileStore.KEY_MCC), "").orEmpty(),
            mnc = prefs.getString(key(packageName, ProfileStore.KEY_MNC), "").orEmpty(),
            operatorName = prefs.getString(key(packageName, ProfileStore.KEY_OPERATOR_NAME), "").orEmpty(),
            cellRadio = prefs.getString(key(packageName, ProfileStore.KEY_CELL_RADIO), "").orEmpty(),
            cellAreaCode = prefs.getLong(key(packageName, ProfileStore.KEY_CELL_AREA_CODE), -1L),
            cellId = prefs.getLong(key(packageName, ProfileStore.KEY_CELL_ID), -1L),
            radioSource = prefs.getString(key(packageName, ProfileStore.KEY_RADIO_SOURCE), "").orEmpty(),
            lastSyncIp = prefs.getString(key(packageName, ProfileStore.KEY_LAST_SYNC_IP), "").orEmpty(),
            lastSyncCity = prefs.getString(key(packageName, ProfileStore.KEY_LAST_SYNC_CITY), "").orEmpty(),
            lastSyncRegion = prefs.getString(key(packageName, ProfileStore.KEY_LAST_SYNC_REGION), "").orEmpty(),
            lastSyncAtEpochMs = prefs.getLong(key(packageName, ProfileStore.KEY_LAST_SYNC_AT), 0L),
        )
        // Old Follow-VPN radio provenance is ambiguous. Keep values available for
        // manual recovery, but don't silently claim them current after migration.
        return legacy.copy(
            radioStale = legacy.followVpn && (legacy.wifiEnabled || legacy.telephonyEnabled),
        )
    }

    fun save(service: XposedService, profile: GeoProfile): Boolean {
        if (profile.targetPackage.isBlank()) return false
        val prefs = service.getRemotePreferences(REMOTE_PREFS)
        migrateLegacyIfNeeded(prefs)
        return save(prefs, profile)
    }

    @Synchronized
    fun save(prefs: SharedPreferences, profile: GeoProfile): Boolean {
        if (profile.targetPackage.isBlank() || profile.validate().isNotEmpty()) return false
        val packageName = profile.targetPackage
        val prior = load(prefs, packageName)
        // One immutable complete snapshot per commit, with optimistic revision guard.
        val committed = ProfileRevisionPolicy.next(prior, profile) ?: return false
        val editor = prefs.edit() ?: return false
        val packages = (packageNames(prefs) + packageName).distinct().sorted()
        val editorResult = editor
            .putString(key(packageName, KEY_ATOMIC_SNAPSHOT), ProfileCodec.encode(committed))
            .putString(KEY_PROFILE_INDEX, packages.joinToString(SEPARATOR))
            .putBoolean(key(packageName, ProfileStore.KEY_ENABLED), committed.enabled)
            .putString(key(packageName, ProfileStore.KEY_TARGET_PACKAGE), packageName)
            .putBoolean(key(packageName, ProfileStore.KEY_FOLLOW_VPN), committed.followVpn)
            .putBoolean(key(packageName, ProfileStore.KEY_TIMEZONE_ENABLED), committed.timezoneEnabled)
            .putString(key(packageName, ProfileStore.KEY_TIMEZONE), committed.timezoneId)
            .putBoolean(key(packageName, ProfileStore.KEY_LOCALE_ENABLED), committed.localeEnabled)
            .putString(key(packageName, ProfileStore.KEY_LOCALE), committed.localeTag)
            .putString(key(packageName, ProfileStore.KEY_COUNTRY), committed.countryCode.uppercase())
            .putBoolean(key(packageName, ProfileStore.KEY_LOCATION_ENABLED), committed.locationEnabled)
            .putString(key(packageName, ProfileStore.KEY_LATITUDE), committed.latitude.toString())
            .putString(key(packageName, ProfileStore.KEY_LONGITUDE), committed.longitude.toString())
            .putBoolean(key(packageName, ProfileStore.KEY_GEOCODER_ENABLED), committed.geocoderEnabled)
            .putBoolean(key(packageName, ProfileStore.KEY_WIFI_ENABLED), committed.wifiEnabled)
            .putString(key(packageName, ProfileStore.KEY_WIFI_SSID), committed.wifiSsid)
            .putString(key(packageName, ProfileStore.KEY_WIFI_BSSID), committed.wifiBssid.lowercase())
            .putInt(key(packageName, ProfileStore.KEY_WIFI_RSSI), committed.wifiRssiDbm)
            .putString(
                key(packageName, ProfileStore.KEY_WIFI_ACCESS_POINTS),
                WifiAccessPointCodec.encode(committed.wifiAccessPoints)
            )
            .putBoolean(key(packageName, ProfileStore.KEY_TELEPHONY_ENABLED), committed.telephonyEnabled)
            .putString(key(packageName, ProfileStore.KEY_MCC), committed.mcc)
            .putString(key(packageName, ProfileStore.KEY_MNC), committed.mnc)
            .putString(key(packageName, ProfileStore.KEY_OPERATOR_NAME), committed.operatorName)
            .putString(key(packageName, ProfileStore.KEY_CELL_RADIO), committed.cellRadio)
            .putLong(key(packageName, ProfileStore.KEY_CELL_AREA_CODE), committed.cellAreaCode)
            .putLong(key(packageName, ProfileStore.KEY_CELL_ID), committed.cellId)
            .putString(key(packageName, ProfileStore.KEY_RADIO_SOURCE), committed.radioSource)
            .putString(key(packageName, ProfileStore.KEY_LAST_SYNC_IP), committed.lastSyncIp)
            .putString(key(packageName, ProfileStore.KEY_LAST_SYNC_CITY), committed.lastSyncCity)
            .putString(key(packageName, ProfileStore.KEY_LAST_SYNC_REGION), committed.lastSyncRegion)
            .putLong(key(packageName, ProfileStore.KEY_LAST_SYNC_AT), committed.lastSyncAtEpochMs)
            .commit()
        return editorResult
    }

    fun delete(service: XposedService, packageName: String): Boolean {
        val prefs = service.getRemotePreferences(REMOTE_PREFS)
        migrateLegacyIfNeeded(prefs)
        val editor = prefs.edit() ?: return false
        val keys = listOf(
            ProfileStore.KEY_ENABLED, ProfileStore.KEY_TARGET_PACKAGE, ProfileStore.KEY_FOLLOW_VPN,
            ProfileStore.KEY_TIMEZONE_ENABLED, ProfileStore.KEY_TIMEZONE,
            ProfileStore.KEY_LOCALE_ENABLED, ProfileStore.KEY_LOCALE, ProfileStore.KEY_COUNTRY,
            ProfileStore.KEY_LOCATION_ENABLED, ProfileStore.KEY_LATITUDE, ProfileStore.KEY_LONGITUDE,
            ProfileStore.KEY_GEOCODER_ENABLED,
            ProfileStore.KEY_WIFI_ENABLED, ProfileStore.KEY_WIFI_SSID, ProfileStore.KEY_WIFI_BSSID,
            ProfileStore.KEY_WIFI_RSSI, ProfileStore.KEY_WIFI_ACCESS_POINTS,
            ProfileStore.KEY_TELEPHONY_ENABLED, ProfileStore.KEY_MCC, ProfileStore.KEY_MNC,
            ProfileStore.KEY_OPERATOR_NAME, ProfileStore.KEY_CELL_RADIO, ProfileStore.KEY_CELL_AREA_CODE,
            ProfileStore.KEY_CELL_ID, ProfileStore.KEY_RADIO_SOURCE,
            ProfileStore.KEY_LAST_SYNC_IP, ProfileStore.KEY_LAST_SYNC_CITY,
            ProfileStore.KEY_LAST_SYNC_REGION, ProfileStore.KEY_LAST_SYNC_AT,
            KEY_ATOMIC_SNAPSHOT,
        )
        keys.forEach { editor.remove(key(packageName, it)) }
        val packages = packageNames(prefs).filterNot { it == packageName }
        return editor.putString(KEY_PROFILE_INDEX, packages.joinToString(SEPARATOR)).commit()
    }

    private fun packageNames(prefs: SharedPreferences): List<String> =
        prefs.getString(KEY_PROFILE_INDEX, "").orEmpty()
            .split(SEPARATOR)
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()

    private fun migrateLegacyIfNeeded(prefs: SharedPreferences) {
        if (packageNames(prefs).isNotEmpty()) return
        val legacyTarget = prefs.getString(ProfileStore.KEY_TARGET_PACKAGE, "").orEmpty()
        if (legacyTarget.isBlank()) return
        save(prefs, ProfileStore.load(prefs))
    }
}