package io.geoshift.app

import android.app.Application
import android.util.Log
import io.geoshift.app.core.ProfileStoreV2
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.ConcurrentHashMap

class GeoShiftApp : Application(), XposedServiceHelper.OnServiceListener {
    companion object {
        private const val TAG = "GeoShiftScope"
        private val REQUIRED_BROKER_SCOPES = setOf(
            "com.android.location.fused",
            "com.xiaomi.location.fused",
        )

        @Volatile
        var service: XposedService? = null
            private set

        private val listeners = CopyOnWriteArraySet<(XposedService?) -> Unit>()
        private val requestedScopes = ConcurrentHashMap.newKeySet<String>()

        fun addServiceListener(listener: (XposedService?) -> Unit, notifyImmediately: Boolean = true) {
            listeners += listener
            if (notifyImmediately) listener(service)
        }

        fun removeServiceListener(listener: (XposedService?) -> Unit) {
            listeners -= listener
        }
    }

    override fun onCreate() {
        super.onCreate()
        XposedServiceHelper.registerListener(this)
    }

    override fun onServiceBind(service: XposedService) {
        GeoShiftApp.service = service
        requestMissingProfileScopes(service)
        listeners.forEach { it(service) }
    }

    override fun onServiceDied(service: XposedService) {
        if (GeoShiftApp.service === service) GeoShiftApp.service = null
        listeners.forEach { it(null) }
    }

    private fun requestMissingProfileScopes(service: XposedService) {
        runCatching {
            val currentScope = service.scope.toHashSet()
            val profileTargets = ProfileStoreV2.list(service)
                .asSequence()
                .filter { it.enabled }
                .map { it.targetPackage.trim() }
                .filter { it.isNotBlank() }
                .toSet()

            val missing = (profileTargets + REQUIRED_BROKER_SCOPES)
                .asSequence()
                .filter { it !in currentScope }
                .filter { requestedScopes.add(it) }
                .sorted()
                .toList()

            if (missing.isEmpty()) return

            Log.i(TAG, "Requesting missing GeoShift scopes: ${missing.joinToString()}")
            service.requestScope(missing, object : XposedService.OnScopeEventListener {
                override fun onScopeRequestApproved(approved: List<String>) {
                    Log.i(TAG, "Scope request approved: ${approved.joinToString()}")
                }

                override fun onScopeRequestFailed(message: String) {
                    requestedScopes.removeAll(missing.toSet())
                    Log.w(TAG, "Scope request failed: $message")
                }
            })
        }.onFailure {
            Log.w(TAG, "Unable to reconcile profile/broker scopes", it)
        }
    }
}
