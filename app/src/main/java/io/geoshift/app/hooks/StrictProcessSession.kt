package io.geoshift.app.hooks

import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Log
import io.geoshift.app.core.GeoProfile
import io.geoshift.app.core.ProfileStoreV2
import io.geoshift.app.core.StrictSessionControl
import io.geoshift.app.core.StrictSessionPolicy
import io.github.libxposed.api.XposedModule
import java.util.concurrent.atomic.AtomicReference

/**
 * Defensive read-only process-side session policy. Broker hooks are disabled by G02.
 *
 * Retained for compatibility tests and later owned-device research. Every access rechecks
 * a monotonic lease instead of trusting an old preference-change notification.
 */
internal class StrictProcessSession(
    private val module: XposedModule,
    private val prefs: SharedPreferences,
    private val processPackage: String,
) {
    private data class Active(
        val profile: GeoProfile,
        val issuedElapsedMs: Long,
        val durationMs: Long,
    )

    private val current = AtomicReference<Active?>(null)
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { changedPrefs, _ ->
        refresh(changedPrefs)
    }

    init {
        refresh(prefs)
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun profile(): GeoProfile? {
        val active = current.get() ?: return null
        return if (StrictSessionPolicy.isActive(
                SystemClock.elapsedRealtime(), active.issuedElapsedMs, active.durationMs,
            )) active.profile else null
    }

    private fun refresh(source: SharedPreferences) {
        val nowWall = System.currentTimeMillis()
        val nowElapsed = SystemClock.elapsedRealtime()
        val issuedWall = source.getLong(StrictSessionControl.KEY_COMMAND_WALL_MS, 0L)
        val issuedElapsed = source.getLong(StrictSessionControl.KEY_ISSUED_ELAPSED_MS, 0L)
        val duration = source.getLong(StrictSessionControl.KEY_DURATION_MS, 0L)
        val enabled = source.getBoolean(StrictSessionControl.KEY_ENABLED, false)
        val acknowledged = source.getBoolean(StrictSessionControl.KEY_ACKNOWLEDGE_GLOBAL, false)

        val candidate = if (
            enabled && acknowledged &&
            StrictSessionPolicy.isCurrentBootCommand(
                nowWall, nowElapsed, issuedWall, issuedElapsed, duration,
            )
        ) {
            val packageName = source.getString(StrictSessionControl.KEY_PACKAGE, "").orEmpty().trim()
            ProfileStoreV2.load(source, packageName)
                ?.takeIf { it.enabled && it.locationEnabled }
                ?.let { Active(it, issuedElapsed, duration) }
        } else null

        val before = current.getAndSet(candidate)
        if ((before == null) != (candidate == null)) {
            module.log(
                Log.INFO,
                TAG,
                "Strict broker session ${if (candidate == null) "inactive" else "leased"} in $processPackage",
            )
        }
    }

    companion object {
        private const val TAG = "GeoShiftStrict"
    }
}
