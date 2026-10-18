package io.geoshift.app.core

/**
 * Only a snapshot of Android PackageManager's grant/manifest information.
 * Permission status is NOT proof of actual location delivery, AppOps, background
 * access, last-used timestamp, or a successful GeoShift hook.
 */
enum class NativeLocationAccess {
    UNKNOWN, NOT_REQUESTED, DENIED, APPROXIMATE, PRECISE,
}

object NativeLocationPermission {
    fun classify(
        packageVisible: Boolean,
        requestedFine: Boolean,
        requestedCoarse: Boolean,
        fineGranted: Boolean,
        coarseGranted: Boolean,
    ): NativeLocationAccess = when {
        !packageVisible -> NativeLocationAccess.UNKNOWN
        !requestedFine && !requestedCoarse -> NativeLocationAccess.NOT_REQUESTED
        requestedFine && fineGranted -> NativeLocationAccess.PRECISE
        (requestedCoarse || requestedFine) && coarseGranted -> NativeLocationAccess.APPROXIMATE
        else -> NativeLocationAccess.DENIED
    }
}
