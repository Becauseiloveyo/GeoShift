package io.geoshift.app.hooks

import android.content.SharedPreferences
import android.util.Log
import io.geoshift.app.core.ProfileStoreV2
import io.geoshift.app.core.SystemRuntimeSnapshot
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam
import java.util.concurrent.atomic.AtomicReference

/**
 * v0.4 system-process foundation.
 *
 * This first stage deliberately installs no system method hooks. It only verifies
 * system/remote capabilities, keeps an immutable in-memory profile snapshot and
 * probes AOSP/HyperOS classes. Delivery hooks are added after this foundation is
 * validated on-device.
 */
class GeoShiftSystemModule : XposedModule() {
    companion object {
        private const val TAG = "GeoShiftSystem"
    }

    private val runtime = AtomicReference(SystemRuntimeSnapshot.EMPTY)
    private var preferenceListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

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

        reloadSnapshot(prefs)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { changedPrefs, _ ->
            reloadSnapshot(changedPrefs)
        }
        preferenceListener = listener // SharedPreferences listeners may otherwise be weakly referenced.
        prefs.registerOnSharedPreferenceChangeListener(listener)

        val capabilities = XiaomiSystemProbe.probe(param.classLoader)
        log(
            Log.INFO,
            TAG,
            "v0.4 system foundation ready; profiles=${runtime.get().profiles.size}; ${capabilities.summary()}",
        )
    }

    private fun reloadSnapshot(prefs: SharedPreferences) {
        runCatching {
            val previous = runtime.get()
            val loaded = SystemProfileSnapshotLoader.load(prefs)
            val retainedSession = previous.strictSession?.takeIf { loaded.profiles.containsKey(it.profileKey) }
            runtime.set(loaded.copy(strictSession = retainedSession))
        }.onFailure {
            // Fail open: keep the last known-good immutable snapshot.
            log(Log.WARN, TAG, "Profile snapshot reload failed; keeping previous state", it)
        }
    }
}
