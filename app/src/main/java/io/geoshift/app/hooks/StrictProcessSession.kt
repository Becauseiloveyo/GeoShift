package io.geoshift.app.hooks

import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Log
import io.geoshift.app.core.GeoProfile
import io.geoshift.app.core.ProfileStoreV2
import io.geoshift.app.core.StrictSessionControl
import io.github.libxposed.api.XposedModule
import java.util.concurrent.atomic.AtomicReference

/**
 * Read-only process-side view of the current Strict Map Session.
 *
 * Unlike system_server, broker processes may start after the command was issued. A wall-clock
 * command stamp is therefore checked against the current boot start time. Commands from older
 * boots are ignored, preserving the runtime-only strict-session contract.
 */
internal class StrictProcessSession(
    private val module: XposedModule,
    private val prefs: SharedPreferences,
    private val processPackage: String,
) {
    private val current = AtomicReference<GeoProfile?>(null)
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { changedPrefs, _ ->
        refresh(changedPrefs)
    }

    init {
        refresh(prefs)
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun profile(): GeoProfile? = current.get()

    private fun refresh(source: SharedPreferences) {
        val now = System.currentTimeMillis()
        val bootStart = now - SystemClock.elapsedRealtime()
        val commandWall = source.getLong(StrictSessionControl.KEY_COMMAND_WALL_MS, 0L)
        val commandBelongsToCurrentBoot =
            commandWall >= bootStart - BOOT_CLOCK_TOLERANCE_MS && commandWall <= now + FUTURE_CLOCK_TOLERANCE_MS

        val candidate = if (
            commandBelongsToCurrentBoot &&
            source.getBoolean(StrictSessionControl.KEY_ENABLED, false)
        ) {
            val packageName = source.getString(StrictSessionControl.KEY_PACKAGE, "").orEmpty().trim()
            ProfileStoreV2.load(source, packageName)
                ?.takeIf { it.enabled && it.locationEnabled }
        } else {
            null
        }

        val previous = current.getAndSet(candidate)
        if (previous?.targetPackage != candidate?.targetPackage) {
            if (candidate == null) {
                module.log(Log.INFO, TAG, "Strict broker session inactive in $processPackage")
            } else {
                module.log(
                    Log.INFO,
                    TAG,
                    "Strict broker session active in $processPackage for ${candidate.targetPackage} at ${candidate.latitude},${candidate.longitude}",
                )
            }
        }
    }

    companion object {
        private const val TAG = "GeoShiftStrict"
        private const val BOOT_CLOCK_TOLERANCE_MS = 10_000L
        private const val FUTURE_CLOCK_TOLERANCE_MS = 60_000L
    }
}
