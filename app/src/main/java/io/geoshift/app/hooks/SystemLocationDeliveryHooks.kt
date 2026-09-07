package io.geoshift.app.hooks

import android.content.Context
import android.location.Location
import android.os.Binder
import android.os.Build
import android.util.Log
import io.geoshift.app.core.GeoProfile
import io.geoshift.app.core.SystemRuntimeSnapshot
import io.github.libxposed.api.XposedModule
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Conservative system-server delivery hooks for direct Android framework clients.
 *
 * Identity is captured synchronously while the Binder caller is still available.
 * Broker processes that do not expose the final client package are deliberately
 * left untouched here and remain the responsibility of app/broker adapters.
 */
internal class SystemLocationDeliveryHooks(
    private val module: XposedModule,
    private val classLoader: ClassLoader,
    private val context: Context,
    private val snapshot: () -> SystemRuntimeSnapshot,
) {
    data class InstallReport(
        val lastLocationHooks: Int,
        val currentLocationHooks: Int,
        val listenerHooks: Int,
    ) {
        val total: Int get() = lastLocationHooks + currentLocationHooks + listenerHooks
    }

    fun install(): InstallReport {
        val serviceClass = runCatching {
            Class.forName("com.android.server.location.LocationManagerService", false, classLoader)
        }.getOrElse {
            module.log(Log.WARN, TAG, "LocationManagerService unavailable; direct delivery layer skipped", it)
            return InstallReport(0, 0, 0)
        }

        val last = hookLastLocation(serviceClass)
        val current = hookCallbackMethod(serviceClass, "getCurrentLocation", "ILocationCallback")
        val listeners = hookCallbackMethod(serviceClass, "registerLocationListener", "ILocationListener")
        return InstallReport(last, current, listeners)
    }

    private fun hookLastLocation(serviceClass: Class<*>): Int {
        var installed = 0
        serviceClass.declaredMethods
            .filter { method -> method.name == "getLastLocation" && Location::class.java.isAssignableFrom(method.returnType) }
            .forEach { method ->
                runCatching {
                    module.hook(method).intercept { chain ->
                        val target = resolveTarget(chain.args.toTypedArray())
                            ?: return@intercept chain.proceed()
                        val original = chain.proceed() as? Location
                        SystemLocationRewriter.rewrite(original, profileFor(target))
                    }
                    installed++
                }.onFailure { module.log(Log.WARN, TAG, "Failed to hook ${method.signature()}", it) }
            }
        return installed
    }

    private fun hookCallbackMethod(
        serviceClass: Class<*>,
        methodName: String,
        callbackTypeSuffix: String,
    ): Int {
        var installed = 0
        serviceClass.declaredMethods
            .filter { it.name == methodName }
            .forEach { method ->
                val callbackIndex = method.parameterTypes.indexOfFirst { type ->
                    type.isInterface && type.name.endsWith(callbackTypeSuffix)
                }
                if (callbackIndex < 0) return@forEach

                runCatching {
                    module.hook(method).intercept { chain ->
                        val target = resolveTarget(chain.args.toTypedArray())
                            ?: return@intercept chain.proceed()
                        val original = chain.args.getOrNull(callbackIndex)
                            ?: return@intercept chain.proceed()
                        val callbackType = method.parameterTypes[callbackIndex]
                        val wrapped = wrapCallback(callbackType, original, target)
                            ?: return@intercept chain.proceed()
                        val forwarded = chain.args.toTypedArray()
                        forwarded[callbackIndex] = wrapped
                        chain.proceed(forwarded)
                    }
                    installed++
                }.onFailure { module.log(Log.WARN, TAG, "Failed to hook ${method.signature()}", it) }
            }
        return installed
    }

    private fun wrapCallback(
        callbackType: Class<*>,
        original: Any,
        target: SystemDeliveryTarget,
    ): Any? = runCatching {
        Proxy.newProxyInstance(callbackType.classLoader, arrayOf(callbackType)) { _, method, args ->
            val profile = profileFor(target)
            val forwarded = args?.map { value ->
                SystemLocationRewriter.rewriteCallbackValue(value, profile)
            }?.toTypedArray() ?: emptyArray()
            try {
                method.invoke(original, *forwarded)
            } catch (error: InvocationTargetException) {
                throw error.targetException
            }
        }
    }.onFailure {
        module.log(Log.WARN, TAG, "Unable to wrap ${callbackType.name}; using original callback", it)
    }.getOrNull()

    private fun profileFor(target: SystemDeliveryTarget): GeoProfile? {
        val current = snapshot()
        if (target.strict && current.strictSession?.profileKey != target.profileKey) return null
        return current.profiles[target.profileKey]
    }

    private fun resolveTarget(args: Array<Any?>): SystemDeliveryTarget? {
        val current = snapshot()
        current.strictSession?.let { return SystemDeliveryTarget(it.profileKey, strict = true) }

        val uid = callerUid() ?: return null
        val packages = runCatching { context.packageManager.getPackagesForUid(uid)?.toSet().orEmpty() }
            .getOrElse { return null }
        if (packages.isEmpty()) return null

        val userId = userIdFromUid(uid)
        val strings = args.filterIsInstance<String>()
        return SystemProfileSelector.select(current, userId, packages, strings)
    }

    private fun callerUid(): Int? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Binder.getCallingUidOrThrow()
        else Binder.getCallingUid()
    }.getOrNull()?.takeIf { it >= 0 }

    private fun userIdFromUid(uid: Int): Int = uid / PER_USER_RANGE

    private fun Method.signature(): String = "$name(${parameterTypes.joinToString { it.simpleName }})"

    companion object {
        private const val TAG = "GeoShiftSystem"
        private const val PER_USER_RANGE = 100_000
    }
}
