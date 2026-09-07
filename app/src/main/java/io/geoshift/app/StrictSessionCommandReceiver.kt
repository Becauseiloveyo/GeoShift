package io.geoshift.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.geoshift.app.core.ProfileKey
import io.geoshift.app.core.ProfileStoreV2
import io.geoshift.app.core.StrictSessionControl

/**
 * Shell/root-only developer control for the v0.4 strict-map session.
 *
 * The manifest protects this receiver with android.permission.DUMP, so ordinary third-party apps
 * cannot arm a system-wide strict session. This temporary control path lets us validate the
 * system-provider layer before promoting the feature into the normal Compose UI.
 */
class StrictSessionCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val enable = intent.getBooleanExtra(EXTRA_ENABLE, true)
        val packageName = intent.getStringExtra(EXTRA_PACKAGE).orEmpty().trim()
        val userId = intent.getIntExtra(EXTRA_USER_ID, ProfileKey.SYSTEM_USER_ID)

        Thread({
            try {
                val service = awaitService() ?: run {
                    Log.w(TAG, "Strict-session command ignored: Xposed service unavailable")
                    return@Thread
                }

                if (enable) {
                    val profile = ProfileStoreV2.load(service, packageName)
                    if (profile == null || !profile.enabled || !profile.locationEnabled) {
                        Log.w(TAG, "Strict-session command rejected: no enabled location profile for $packageName")
                        return@Thread
                    }
                }

                if (StrictSessionControl.issue(service, enable, packageName, userId)) {
                    Log.i(
                        TAG,
                        if (enable) "Strict-session command issued for user=$userId package=$packageName"
                        else "Strict-session stop command issued",
                    )
                } else {
                    Log.w(TAG, "Strict-session command could not be written")
                }
            } catch (error: Throwable) {
                Log.w(TAG, "Strict-session command failed", error)
            } finally {
                pending.finish()
            }
        }, "GeoShift-StrictCommand").start()
    }

    private fun awaitService(): io.github.libxposed.service.XposedService? {
        val deadline = android.os.SystemClock.uptimeMillis() + SERVICE_WAIT_MS
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            GeoShiftApp.service?.let { return it }
            try {
                Thread.sleep(50L)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }
        return GeoShiftApp.service
    }

    companion object {
        const val EXTRA_ENABLE = "enable"
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_USER_ID = "user"

        private const val TAG = "GeoShiftStrict"
        private const val SERVICE_WAIT_MS = 4_000L
    }
}
