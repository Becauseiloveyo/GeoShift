package io.geoshift.app.core

import android.os.SystemClock
import io.github.libxposed.service.XposedService

/**
 * Runtime-only strict-map session command channel.
 *
 * Persisted values are commands, not boot state. The system module snapshots the current command
 * id when system_server starts and only reacts to later command-id changes, so a strict session is
 * never re-armed automatically after reboot.
 */
object StrictSessionControl {
    const val KEY_COMMAND_ID = "runtime_strict_command_id"
    const val KEY_ENABLED = "runtime_strict_enabled"
    const val KEY_PACKAGE = "runtime_strict_package"
    const val KEY_USER_ID = "runtime_strict_user_id"

    fun issue(
        service: XposedService,
        enabled: Boolean,
        packageName: String = "",
        userId: Int = ProfileKey.SYSTEM_USER_ID,
    ): Boolean {
        if (enabled && packageName.isBlank()) return false
        if (userId < 0) return false

        val prefs = service.getRemotePreferences(ProfileStoreV2.REMOTE_PREFS)
        val editor = prefs.edit() ?: return false
        editor
            .putBoolean(KEY_ENABLED, enabled)
            .putString(KEY_PACKAGE, packageName.trim())
            .putInt(KEY_USER_ID, userId)
            .putLong(KEY_COMMAND_ID, SystemClock.elapsedRealtimeNanos())
            .apply()
        return true
    }
}
