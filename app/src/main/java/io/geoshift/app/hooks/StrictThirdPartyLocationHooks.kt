package io.geoshift.app.hooks

import android.location.LocationListener
import android.util.Log
import io.geoshift.app.core.GeoProfile
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.ConcurrentHashMap

/**
 * Strict-session adapters for broker/private network-location paths that bypass the normal
 * system-server delivery layer. Portal uses the same AMap NetworkLocationManager path inside
 * Xiaomi's fused-location process; GeoShift keeps it runtime-gated and fail-open.
 */
internal class StrictThirdPartyLocationHooks(
    private val module: XposedModule,
    private val classLoader: ClassLoader,
    private val strictProfile: () -> GeoProfile?,
) {
    data class InstallReport(
        val amapPresent: Boolean,
        val requestHooks: Int,
        val extraCommandHooks: Int,
        val offlineHooks: Int,
    ) {
        val total: Int get() = requestHooks + extraCommandHooks + offlineHooks
    }

    private val loggedHits = ConcurrentHashMap.newKeySet<String>()

    fun install(): InstallReport {
        val clazz = runCatching {
            Class.forName(AMAP_NETWORK_MANAGER, false, classLoader)
        }.getOrNull() ?: return InstallReport(false, 0, 0, 0)

        val request = hookRequestLocationUpdates(clazz)
        val extra = hookExtraCommands(clazz)
        val offline = hookOfflineLocationToggle(clazz)
        return InstallReport(true, request, extra, offline)
    }

    private fun hookRequestLocationUpdates(clazz: Class<*>): Int {
        var installed = 0
        clazz.declaredMethods
            .filter { method ->
                method.name == "requestLocationUpdates" &&
                    method.parameterTypes.any { LocationListener::class.java.isAssignableFrom(it) }
            }
            .forEach { method ->
                runCatching {
                    val listenerIndex = method.parameterTypes.indexOfFirst {
                        LocationListener::class.java.isAssignableFrom(it)
                    }
                    module.hook(method).intercept { chain ->
                        val profile = strictProfile() ?: return@intercept chain.proceed()
                        val original = chain.args.getOrNull(listenerIndex)
                            ?: return@intercept chain.proceed()
                        val callbackType = method.parameterTypes[listenerIndex]
                        if (!callbackType.isInterface) return@intercept chain.proceed()

                        val forwarded = chain.args.toTypedArray()
                        forwarded[listenerIndex] = blackHoleListener(callbackType, original)
                            ?: return@intercept chain.proceed()
                        logHitOnce(
                            "amap-request:${method.signature()}",
                            "Strict AMap NetworkLocationManager requestLocationUpdates blocked private network-location callbacks for ${profile.targetPackage}",
                        )
                        chain.proceed(forwarded)
                    }
                    installed++
                }.onFailure {
                    module.log(Log.WARN, TAG, "Failed to hook AMap ${method.signature()}", it)
                }
            }
        return installed
    }

    private fun hookExtraCommands(clazz: Class<*>): Int {
        var installed = 0
        clazz.declaredMethods
            .filter { it.name == "onSendExtraCommand" }
            .forEach { method ->
                runCatching {
                    module.hook(method).intercept { chain ->
                        val profile = strictProfile() ?: return@intercept chain.proceed()
                        if (method.returnType == Boolean::class.javaPrimitiveType || method.returnType == Boolean::class.java) {
                            logHitOnce(
                                "amap-extra:${method.signature()}",
                                "Strict AMap NetworkLocationManager extra-command path suppressed for ${profile.targetPackage}",
                            )
                            false
                        } else {
                            chain.proceed()
                        }
                    }
                    installed++
                }.onFailure {
                    module.log(Log.WARN, TAG, "Failed to hook AMap ${method.signature()}", it)
                }
            }
        return installed
    }

    private fun hookOfflineLocationToggle(clazz: Class<*>): Int {
        var installed = 0
        clazz.declaredMethods
            .filter { it.name == "updateOffLocEnable" && it.returnType == Void.TYPE }
            .forEach { method ->
                runCatching {
                    module.hook(method).intercept { chain ->
                        val profile = strictProfile() ?: return@intercept chain.proceed()
                        logHitOnce(
                            "amap-offline:${method.signature()}",
                            "Strict AMap offline-network-location toggle suppressed for ${profile.targetPackage}",
                        )
                        null
                    }
                    installed++
                }.onFailure {
                    module.log(Log.WARN, TAG, "Failed to hook AMap ${method.signature()}", it)
                }
            }
        return installed
    }

    private fun blackHoleListener(callbackType: Class<*>, original: Any): Any? = runCatching {
        Proxy.newProxyInstance(
            callbackType.classLoader ?: original.javaClass.classLoader,
            arrayOf(callbackType),
        ) { _, method, _ -> defaultValue(method.returnType) }
    }.onFailure {
        module.log(Log.WARN, TAG, "Unable to create strict AMap listener gate; failing open", it)
    }.getOrNull()

    private fun defaultValue(type: Class<*>): Any? = when (type) {
        java.lang.Boolean.TYPE -> false
        java.lang.Byte.TYPE -> 0.toByte()
        java.lang.Short.TYPE -> 0.toShort()
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        java.lang.Float.TYPE -> 0f
        java.lang.Double.TYPE -> 0.0
        java.lang.Character.TYPE -> '\u0000'
        else -> null
    }

    private fun logHitOnce(key: String, message: String) {
        if (loggedHits.add(key)) module.log(Log.INFO, TAG, message)
    }

    private fun Method.signature(): String =
        "$name(${parameterTypes.joinToString { it.simpleName }})"

    companion object {
        private const val TAG = "GeoShiftStrict"
        private const val AMAP_NETWORK_MANAGER = "com.amap.android.location.NetworkLocationManager"
    }
}
