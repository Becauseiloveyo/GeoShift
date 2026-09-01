package io.geoshift.app.hooks

import android.location.Location
import android.os.LocaleList
import android.util.Log
import io.geoshift.app.core.GeoProfile
import io.geoshift.app.core.ProfileStoreV2
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.time.ZoneId
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicReference

class GeoShiftModule : XposedModule() {
    companion object {
        private const val TAG = "GeoShift"
        private val TIMEZONE_IDS = TimeZone.getAvailableIDs().toHashSet()
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (!param.isFirstPackage) return

        val prefs = getRemotePreferences(ProfileStoreV2.REMOTE_PREFS)
        val profile = AtomicReference(ProfileStoreV2.load(prefs, param.packageName))
        val prefix = ProfileStoreV2.prefixFor(param.packageName)
        prefs.registerOnSharedPreferenceChangeListener { changedPrefs, key ->
            if (key == null || key.startsWith(prefix)) {
                profile.set(ProfileStoreV2.load(changedPrefs, param.packageName))
            }
        }

        log(Log.INFO, TAG, "Installing reactive profile hooks for ${param.packageName}")

        runCatching {
            hook(TimeZone::class.java.getDeclaredMethod("getDefault")).intercept { chain ->
                val current = profile.get()
                if (!current.isTimezoneActive()) chain.proceed()
                else TimeZone.getTimeZone(current!!.timezoneId)
            }

            hook(ZoneId::class.java.getDeclaredMethod("systemDefault")).intercept { chain ->
                val current = profile.get()
                if (!current.isTimezoneActive()) chain.proceed()
                else runCatching { ZoneId.of(current!!.timezoneId) }.getOrElse { chain.proceed() }
            }
        }.onFailure { log(Log.WARN, TAG, "Time zone hook failed", it) }

        runCatching {
            hook(Locale::class.java.getDeclaredMethod("getDefault")).intercept { chain ->
                localeOrOriginal(profile.get(), chain::proceed)
            }
            hook(Locale::class.java.getDeclaredMethod("getDefault", Locale.Category::class.java)).intercept { chain ->
                localeOrOriginal(profile.get(), chain::proceed)
            }
            hook(LocaleList::class.java.getDeclaredMethod("getDefault")).intercept { chain ->
                localeListOrOriginal(profile.get(), chain::proceed)
            }
            hook(LocaleList::class.java.getDeclaredMethod("getAdjustedDefault")).intercept { chain ->
                localeListOrOriginal(profile.get(), chain::proceed)
            }
        }.onFailure { log(Log.WARN, TAG, "Locale hook failed", it) }

        runCatching {
            val getLatitude = Location::class.java.getDeclaredMethod("getLatitude")
            val getLongitude = Location::class.java.getDeclaredMethod("getLongitude")

            hook(getLatitude).intercept { chain ->
                val current = profile.get()
                if (!current.isLocationActive() || current!!.latitude !in -90.0..90.0) chain.proceed()
                else current!!.latitude
            }
            hook(getLongitude).intercept { chain ->
                val current = profile.get()
                if (!current.isLocationActive() || current!!.longitude !in -180.0..180.0) chain.proceed()
                else current!!.longitude
            }
        }.onFailure { log(Log.WARN, TAG, "Location hook failed", it) }
    }

    private fun GeoProfile?.isTimezoneActive(): Boolean =
        this != null && enabled && timezoneEnabled && timezoneId in TIMEZONE_IDS

    private fun GeoProfile?.isLocaleActive(): Boolean =
        this != null && enabled && localeEnabled && localeTag.isNotBlank()

    private fun GeoProfile?.isLocationActive(): Boolean =
        this != null && enabled && locationEnabled && latitude.isFinite() && longitude.isFinite()

    private fun localeOrOriginal(current: GeoProfile?, original: () -> Any?): Any? {
        if (!current.isLocaleActive()) return original()
        val locale = Locale.forLanguageTag(current!!.localeTag)
        return if (locale.language.isBlank()) original() else locale
    }

    private fun localeListOrOriginal(current: GeoProfile?, original: () -> Any?): Any? {
        if (!current.isLocaleActive()) return original()
        val locale = Locale.forLanguageTag(current!!.localeTag)
        return if (locale.language.isBlank()) original() else LocaleList(locale)
    }
}
