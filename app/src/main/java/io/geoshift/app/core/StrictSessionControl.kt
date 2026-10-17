package io.geoshift.app.core

import android.os.SystemClock
import io.github.libxposed.service.XposedService

/**
 * Runtime-only strict-map session command channel.
 *
 * Persisted values are commands, not boot state. The system module snapshots the current command
 * id when system_server starts and only reacts to later command-id changes, so a strict session is
 * never re-armed automatically after reboot. The wall-clock stamp lets late-starting broker
 * processes reject commands that belong to an older boot.
 */
object StrictSessionControl {
    const val KEY_COMMAND_ID = "runtime_strict_command_id"
    const val KEY_COMMAND_WALL_MS = "runtime_strict_command_wall_ms"
    const val KEY_ENABLED = "runtime_strict_enabled"
    const val KEY_PACKAGE = "runtime_strict_package"
    const val KEY_USER_ID = "runtime_strict_user_id"
    const val KEY_ACKNOWLEDGE_GLOBAL = "runtime_strict_ack_global"
    const val KEY_ISSUED_ELAPSED_MS = "runtime_strict_issued_elapsed_ms"
    const val KEY_DURATION_MS = "runtime_strict_duration_ms"

    fun issue(
        service: XposedService,
        enabled: Boolean,
        packageName: String = "",
        userId: Int = ProfileKey.SYSTEM_USER_ID,
        acknowledgeGlobalImpact: Boolean = false,
        durationMs: Long = StrictSessionPolicy.DEFAULT_DURATION_MS,
    ): Boolean {
        if (enabled && (packageName.isBlank() || !acknowledgeGlobalImpact ||
                durationMs !in 1L..StrictSessionPolicy.MAX_DURATION_MS)) return false
        if (userId < 0) return false

        val prefs = service.getRemotePreferences(ProfileStoreV2.REMOTE_PREFS)
        val editor = prefs.edit() ?: return false
        editor
            .putBoolean(KEY_ENABLED, enabled)
            .putString(KEY_PACKAGE, packageName.trim())
            .putInt(KEY_USER_ID, userId)
            .putBoolean(KEY_ACKNOWLEDGE_GLOBAL, enabled && acknowledgeGlobalImpact)
            .putLong(KEY_DURATION_MS, if (enabled) durationMs else 0L)
            .putLong(KEY_ISSUED_ELAPSED_MS, if (enabled) SystemClock.elapsedRealtime() else 0L)
            .putLong(KEY_COMMAND_WALL_MS, System.currentTimeMillis())
            .putLong(KEY_COMMAND_ID, SystemClock.elapsedRealtimeNanos())
            .apply()
        return true
    }
}
