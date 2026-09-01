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
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (!param.isFirstPackage) return

        val prefs = getRemotePreferences(ProfileStore.REMOTE_PREFS)
        if (!prefs.getBoolean(ProfileStore.KEY_ENABLED, true)) return

        val target = prefs.getString(ProfileStore.KEY_TARGET_PACKAGE, "").orEmpty()
        if (target.isNotBlank() && target != param.packageName) return

        log(Log.INFO, TAG, "Applying profile to ${param.packageName}")

        runCatching {
            val method = TimeZone::class.java.getDeclaredMethod("getDefault")
            hook(method).intercept { chain ->
                if (!prefs.getBoolean(ProfileStore.KEY_TIMEZONE_ENABLED, true)) {
                    chain.proceed()
                } else {
                    val id = prefs.getString(ProfileStore.KEY_TIMEZONE, "").orEmpty()
                    if (id.isBlank()) chain.proceed() else TimeZone.getTimeZone(id)
                }
            }
        }.onFailure { log(Log.WARN, TAG, "TimeZone hook failed", it) }

        runCatching {
            val noArg = Locale::class.java.getDeclaredMethod("getDefault")
            hook(noArg).intercept { chain ->
                if (!prefs.getBoolean(ProfileStore.KEY_LOCALE_ENABLED, true)) {
                    chain.proceed()
                } else {
                    val tag = prefs.getString(ProfileStore.KEY_LOCALE, "").orEmpty()
                    if (tag.isBlank()) chain.proceed() else Locale.forLanguageTag(tag)
                }
            }

            val category = Locale::class.java.getDeclaredMethod("getDefault", Locale.Category::class.java)
            hook(category).intercept { chain ->
                if (!prefs.getBoolean(ProfileStore.KEY_LOCALE_ENABLED, true)) {
                    chain.proceed()
                } else {
                    val tag = prefs.getString(ProfileStore.KEY_LOCALE, "").orEmpty()
                    if (tag.isBlank()) chain.proceed() else Locale.forLanguageTag(tag)
                }
            }
        }.onFailure { log(Log.WARN, TAG, "Locale hook failed", it) }

        runCatching {
            val getLatitude = Location::class.java.getDeclaredMethod("getLatitude")
            val getLongitude = Location::class.java.getDeclaredMethod("getLongitude")

            hook(getLatitude).intercept { chain ->
                if (!prefs.getBoolean(ProfileStore.KEY_LOCATION_ENABLED, true)) {
                    chain.proceed()
                } else {
                    prefs.getString(ProfileStore.KEY_LATITUDE, null)?.toDoubleOrNull() ?: chain.proceed()
                }
            }
            hook(getLongitude).intercept { chain ->
                if (!prefs.getBoolean(ProfileStore.KEY_LOCATION_ENABLED, true)) {
                    chain.proceed()
                } else {
                    prefs.getString(ProfileStore.KEY_LONGITUDE, null)?.toDoubleOrNull() ?: chain.proceed()
                }
            }
        }.onFailure { log(Log.WARN, TAG, "Location hook failed", it) }
    }
}
