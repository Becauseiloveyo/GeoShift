package io.geoshift.app.hooks

import android.location.Location
import android.util.Log
import io.geoshift.app.core.ProfileStore
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.util.Locale
import java.util.TimeZone

class GeoShiftModule : XposedModule() {
    companion object {
        private const val TAG = "GeoShift"
        private val TIMEZONE_IDS = TimeZone.getAvailableIDs().toHashSet()
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (!param.isFirstPackage) return

        val prefs = getRemotePreferences(ProfileStore.REMOTE_PREFS)
        val targetAtInstall = prefs.getString(ProfileStore.KEY_TARGET_PACKAGE, "").orEmpty()
        if (targetAtInstall.isBlank() || targetAtInstall != param.packageName) return

        fun profileActive(): Boolean {
            if (!prefs.getBoolean(ProfileStore.KEY_ENABLED, true)) return false
            return prefs.getString(ProfileStore.KEY_TARGET_PACKAGE, "").orEmpty() == param.packageName
        }

        log(Log.INFO, TAG, "Installing dynamic profile hooks for ${param.packageName}")

        runCatching {
            val method = TimeZone::class.java.getDeclaredMethod("getDefault")
            hook(method).intercept { chain ->
                if (!profileActive() || !prefs.getBoolean(ProfileStore.KEY_TIMEZONE_ENABLED, true)) {
                    chain.proceed()
                } else {
                    val id = prefs.getString(ProfileStore.KEY_TIMEZONE, "").orEmpty()
                    if (id !in TIMEZONE_IDS) chain.proceed() else TimeZone.getTimeZone(id)
                }
            }
        }.onFailure { log(Log.WARN, TAG, "TimeZone hook failed", it) }

        runCatching {
            val noArg = Locale::class.java.getDeclaredMethod("getDefault")
            hook(noArg).intercept { chain ->
                localeOrOriginal(prefs, profileActive(), chain::proceed)
            }

            val category = Locale::class.java.getDeclaredMethod("getDefault", Locale.Category::class.java)
            hook(category).intercept { chain ->
                localeOrOriginal(prefs, profileActive(), chain::proceed)
            }
        }.onFailure { log(Log.WARN, TAG, "Locale hook failed", it) }

        runCatching {
            val getLatitude = Location::class.java.getDeclaredMethod("getLatitude")
            val getLongitude = Location::class.java.getDeclaredMethod("getLongitude")

            hook(getLatitude).intercept { chain ->
                if (!profileActive() || !prefs.getBoolean(ProfileStore.KEY_LOCATION_ENABLED, true)) {
                    chain.proceed()
                } else {
                    val value = prefs.getString(ProfileStore.KEY_LATITUDE, null)?.toDoubleOrNull()
                    if (value == null || !value.isFinite() || value !in -90.0..90.0) chain.proceed() else value
                }
            }
            hook(getLongitude).intercept { chain ->
                if (!profileActive() || !prefs.getBoolean(ProfileStore.KEY_LOCATION_ENABLED, true)) {
                    chain.proceed()
                } else {
                    val value = prefs.getString(ProfileStore.KEY_LONGITUDE, null)?.toDoubleOrNull()
                    if (value == null || !value.isFinite() || value !in -180.0..180.0) chain.proceed() else value
                }
            }
        }.onFailure { log(Log.WARN, TAG, "Location hook failed", it) }
    }

    private fun localeOrOriginal(
        prefs: android.content.SharedPreferences,
        active: Boolean,
        original: () -> Any?,
    ): Any? {
        if (!active || !prefs.getBoolean(ProfileStore.KEY_LOCALE_ENABLED, true)) return original()
        val tag = prefs.getString(ProfileStore.KEY_LOCALE, "").orEmpty()
        val locale = Locale.forLanguageTag(tag)
        return if (tag.isBlank() || locale.language.isBlank()) original() else locale
    }
}
