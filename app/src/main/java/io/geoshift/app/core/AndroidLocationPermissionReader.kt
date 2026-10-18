package io.geoshift.app.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager

/**
 * Read-only; never requests or changes another app's permissions.
 * Package visibility restrictions or errors must produce UNKNOWN, not DENIED.
 */
object AndroidLocationPermissionReader {
    fun inspect(context: Context, targetPackage: String): NativeLocationAccess {
        if (targetPackage.isBlank()) return NativeLocationAccess.UNKNOWN
        val pm = context.packageManager
        return runCatching {
            @Suppress("DEPRECATION")
            val info = pm.getPackageInfo(targetPackage, PackageManager.GET_PERMISSIONS)
            val requested = info.requestedPermissions?.toSet().orEmpty()
            NativeLocationPermission.classify(
                packageVisible = true,
                requestedFine = Manifest.permission.ACCESS_FINE_LOCATION in requested,
                requestedCoarse = Manifest.permission.ACCESS_COARSE_LOCATION in requested,
                fineGranted = pm.checkPermission(
                    Manifest.permission.ACCESS_FINE_LOCATION, targetPackage
                ) == PackageManager.PERMISSION_GRANTED,
                coarseGranted = pm.checkPermission(
                    Manifest.permission.ACCESS_COARSE_LOCATION, targetPackage
                ) == PackageManager.PERMISSION_GRANTED,
            )
        }.getOrDefault(NativeLocationAccess.UNKNOWN)
    }
}
