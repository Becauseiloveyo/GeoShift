package io.geoshift.app.hooks

import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Log
import io.geoshift.app.core.ProfileKey
import io.geoshift.app.core.ProfileStoreV2
import io.geoshift.app.core.StrictSessionControl
import io.geoshift.app.core.StrictSessionPolicy
import io.geoshift.app.core.SystemRuntimeSnapshot
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import java.util.concurrent.atomic.AtomicReference

/** v0.4 system-process foundation, direct delivery layer and explicit strict-map session. */
class GeoShiftSystemModule : XposedModule() {
    companion object {
        private const val TAG = "GeoShiftSystem"
        private const val STRICT_TAG = "GeoShiftStrict"
    }

    private val runtime = AtomicReference(SystemRuntimeSnapshot.EMPTY)
    private var preferenceListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var lastStrictCommandId: Long = 0L

    override fun onSystemServerStarting(param: SystemServerStartingParam) {
        val properties = getFrameworkProperties()
        if (properties and XposedInterface.PROP_CAP_SYSTEM == 0L) {
            log(Log.WARN, TAG, "Framework does not advertise PROP_CAP_SYSTEM; system layer disabled")
            return
        }
        if (properties and XposedInterface.PROP_CAP_REMOTE == 0L) {
            log(Log.WARN, TAG, "Framework does not advertise PROP_CAP_REMOTE; system layer disabled")
            return
        }

        val prefs = runCatching { getRemotePreferences(ProfileStoreV2.REMOTE_PREFS) }
            .onFailure { log(Log.ERROR, TAG, "Unable to open Remote Preferences", it) }
            .getOrNull() ?: return

        // Existing persisted strict commands are deliberately consumed as historical state only.
        // A new command-id change is required after every system_server start to arm strict mode.
        lastStrictCommandId = prefs.getLong(StrictSessionControl.KEY_COMMAND_ID, 0L)
        reloadSnapshot(prefs)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { changedPrefs, key ->
            reloadSnapshot(changedPrefs)
            if (key == StrictSessionControl.KEY_COMMAND_ID) {
                applyStrictCommand(changedPrefs)
            }
        }
        preferenceListener = listener // SharedPreferences listeners may otherwise be weakly referenced.
        prefs.registerOnSharedPreferenceChangeListener(listener)

        val capabilities = XiaomiSystemProbe.probe(param.classLoader)
        log(
            Log.INFO,
            TAG,
            "v0.4 system foundation ready; profiles=${runtime.get().profiles.size}; ${capabilities.summary()}",
        )

        val systemContext = SystemContextProvider.get()
        if (systemContext == null) {
            log(Log.WARN, TAG, "System context unavailable; delivery hooks skipped to preserve package/UID verification")
            return
        }

        val directReport = SystemLocationDeliveryHooks(
            module = this,
            classLoader = param.classLoader,
            context = systemContext,
            snapshot = runtime::get,
        ).install()
        log(
            Log.INFO,
            TAG,
            "Direct framework delivery hooks: last=${directReport.lastLocationHooks}, current=${directReport.currentLocationHooks}, listener=${directReport.listenerHooks}",
        )

        val strictReport = StrictProviderDeliveryHooks(
            module = this,
            classLoader = param.classLoader,
            snapshot = runtime::get,
        ).install()
        log(
            Log.INFO,
            STRICT_TAG,
            "Strict provider hooks installed inactive-by-default: provider=${strictReport.providerHooks}, gnss=${strictReport.gnssHooks}",
        )
    }

    private fun applyStrictCommand(prefs: SharedPreferences) {
        val commandId = prefs.getLong(StrictSessionControl.KEY_COMMAND_ID, 0L)
        if (commandId == lastStrictCommandId) return
        lastStrictCommandId = commandId

        // Explicit opt-in to a shared, system-wide experiment; no implicit per-app guarantee.
        val enabled = prefs.getBoolean(StrictSessionControl.KEY_ENABLED, false)
        val acknowledged = prefs.getBoolean(StrictSessionControl.KEY_ACKNOWLEDGE_GLOBAL, false)
        val issuedElapsed = prefs.getLong(StrictSessionControl.KEY_ISSUED_ELAPSED_MS, 0L)
        val duration = prefs.getLong(StrictSessionControl.KEY_DURATION_MS, 0L)
        if (!enabled || !acknowledged ||
            !StrictSessionPolicy.isActive(SystemClock.elapsedRealtime(), issuedElapsed, duration)
        ) {
            runtime.updateAndGet { it.clearStrictSession() }
            log(Log.INFO, STRICT_TAG, "Strict session stopped or rejected (inactive, unacknowledged or expired)")
            return
        }

        val packageName = prefs.getString(StrictSessionControl.KEY_PACKAGE, "").orEmpty().trim()
        val userId = prefs.getInt(StrictSessionControl.KEY_USER_ID, ProfileKey.SYSTEM_USER_ID)
        val key = runCatching { ProfileKey(userId, packageName) }.getOrNull()
        if (key == null) {
            runtime.updateAndGet { it.clearStrictSession() }
            log(Log.WARN, STRICT_TAG, "Strict map session rejected: invalid profile key")
            return
        }

        val updated = runtime.updateAndGet { snapshot ->
            snapshot.clearStrictSession().armStrictSession(key, issuedElapsed, duration)
        }
        if (updated.strictSession?.profileKey == key) {
            log(
                Log.WARN,
                STRICT_TAG,
                "EXPERIMENTAL GLOBAL strict session armed; affects shared provider consumers, user=$userId package=$packageName ttlMs=$duration",
            )
        } else {
            log(Log.WARN, STRICT_TAG, "Strict map session rejected: profile missing, disabled, or location disabled")
        }
    }

    private fun reloadSnapshot(prefs: SharedPreferences) {
        runCatching {
            val previous = runtime.get()
            val loaded = SystemProfileSnapshotLoader.load(prefs)
            val retainedSession = previous.strictSession?.takeIf { session ->
                loaded.profiles[session.profileKey]?.let { it.enabled && it.locationEnabled } == true &&
                    prefs.getBoolean(StrictSessionControl.KEY_ENABLED, false) &&
                    prefs.getBoolean(StrictSessionControl.KEY_ACKNOWLEDGE_GLOBAL, false) &&
                    session.isActive(SystemClock.elapsedRealtime())
            }
            runtime.set(loaded.copy(strictSession = retainedSession))
        }.onFailure {
            // Fail open: keep the last known-good immutable snapshot.
            log(Log.WARN, TAG, "Profile snapshot reload failed; keeping previous state", it)
        }
    }
}
