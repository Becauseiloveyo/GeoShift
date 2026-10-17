package io.geoshift.app.hooks

import android.util.Log
import io.geoshift.app.core.AuditOutcome
import io.geoshift.app.core.HookAudit
import io.geoshift.app.core.HookAuditEvent
import io.github.libxposed.api.XposedModule
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * At-most-once coarse diagnostic event per process/surface/outcome.
 * Hook callbacks only enqueue to a daemon worker; module logging is done off the
 * location-delivery hot path. No coordinates, IP, network or disk reads.
 */
internal class HookAuditEmitter(
    private val module: XposedModule,
    private val packageName: String,
) {
    private val emitted = ConcurrentHashMap.newKeySet<String>()
    private val worker by lazy {
        Executors.newSingleThreadExecutor { work ->
            Thread(work, "GeoShift-Audit").apply { isDaemon = true }
        }
    }

    fun emit(
        surface: String,
        outcome: AuditOutcome,
        identityVerified: Boolean = false,
        targetPackageName: String = packageName,
    ) {
        val message = HookAudit.encode(
            HookAuditEvent(targetPackageName, surface, outcome, identityVerified),
        ) ?: return
        if (emitted.add(message)) {
            runCatching { worker.execute { module.log(Log.INFO, "GeoShiftAudit", message) } }
        }
    }
}
