package io.geoshift.app.hooks

/**
 * Runtime-only class discovery for Android/HyperOS internals.
 * No hook is installed by this probe; missing vendor classes are a supported state.
 */
internal data class SystemHookCapabilities(
    val locationManagerService: Boolean,
    val locationProviderManager: Boolean,
    val abstractLocationProvider: Boolean,
    val gnssLocationProvider: Boolean,
    val miuiBlurLocationManager: Boolean,
) {
    fun summary(): String = buildString {
        append("LMS=").append(flag(locationManagerService))
        append(" LPM=").append(flag(locationProviderManager))
        append(" ALP=").append(flag(abstractLocationProvider))
        append(" GNSS=").append(flag(gnssLocationProvider))
        append(" MIUI_BLUR=").append(flag(miuiBlurLocationManager))
    }

    private fun flag(value: Boolean) = if (value) "yes" else "no"
}

internal object XiaomiSystemProbe {
    fun probe(classLoader: ClassLoader): SystemHookCapabilities = SystemHookCapabilities(
        locationManagerService = exists(classLoader, "com.android.server.location.LocationManagerService"),
        locationProviderManager = exists(classLoader, "com.android.server.location.provider.LocationProviderManager"),
        abstractLocationProvider = exists(classLoader, "com.android.server.location.provider.AbstractLocationProvider"),
        gnssLocationProvider = exists(classLoader, "com.android.server.location.gnss.GnssLocationProvider"),
        miuiBlurLocationManager = exists(classLoader, "com.android.server.location.MiuiBlurLocationManagerImpl"),
    )

    private fun exists(classLoader: ClassLoader, className: String): Boolean =
        runCatching { Class.forName(className, false, classLoader) }.isSuccess
}
