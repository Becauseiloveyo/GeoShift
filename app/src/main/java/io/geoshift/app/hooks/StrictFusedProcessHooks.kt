package io.geoshift.app.hooks

import android.location.Location
import android.util.Log
import io.geoshift.app.core.GeoProfile
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/** Strict-session adapter for the standalone AOSP fused-location process. */
internal class StrictFusedProcessHooks(
    private val module: XposedModule,
    private val classLoader: ClassLoader,
    private val strictProfile: () -> GeoProfile?,
) {
    data class InstallReport(
        val providerPresent: Boolean,
        val chooseBestHooks: Int,
        val childListenerHooks: Int,
    ) {
        val total: Int get() = chooseBestHooks + childListenerHooks
    }

    private val loggedHits = ConcurrentHashMap.newKeySet<String>()

    fun install(): InstallReport {
        val provider = runCatching {
            Class.forName(FUSED_PROVIDER_CLASS, false, classLoader)
        }.getOrNull()

        val choose = provider?.let(::hookChooseBestLocation) ?: 0
        val child = runCatching {
            Class.forName(FUSED_CHILD_LISTENER_CLASS, false, classLoader)
        }.getOrNull()?.let(::hookChildLocationListener) ?: 0

        return InstallReport(provider != null, choose, child)
    }

    private fun hookChooseBestLocation(clazz: Class<*>): Int {
        var installed = 0
        clazz.declaredMethods
            .filter { method ->
                method.name == "chooseBestLocation" &&
                    Location::class.java.isAssignableFrom(method.returnType)
            }
            .forEach { method ->
                runCatching {
                    module.hook(method).intercept { chain ->
                        val profile = strictProfile() ?: return@intercept chain.proceed()
                        val original = chain.proceed() as? Location ?: return@intercept null
                        logHitOnce(
                            "fused-choose:${method.signature()}",
                            "Strict AOSP FusedLocationProvider chooseBestLocation hit for ${profile.targetPackage}",
                        )
                        SystemLocationRewriter.rewrite(original, profile)
                    }
                    installed++
                }.onFailure {
                    module.log(Log.WARN, TAG, "Failed to hook fused ${method.signature()}", it)
                }
            }
        return installed
    }

    private fun hookChildLocationListener(clazz: Class<*>): Int {
        var installed = 0
        clazz.declaredMethods
            .filter { method -> method.parameterTypes.any { Location::class.java.isAssignableFrom(it) } }
            .forEach { method ->
                runCatching {
                    module.hook(method).intercept { chain ->
                        val profile = strictProfile() ?: return@intercept chain.proceed()
                        val forwarded = chain.args.toTypedArray()
                        var changed = false
                        for (index in forwarded.indices) {
                            val location = forwarded[index] as? Location ?: continue
                            forwarded[index] = SystemLocationRewriter.rewrite(location, profile) ?: location
                            changed = true
                        }
                        if (changed) {
                            logHitOnce(
                                "fused-child:${method.signature()}",
                                "Strict AOSP fused child-listener path hit for ${profile.targetPackage}",
                            )
                            chain.proceed(forwarded)
                        } else {
                            chain.proceed()
                        }
                    }
                    installed++
                }.onFailure {
                    module.log(Log.WARN, TAG, "Failed to hook fused child ${method.signature()}", it)
                }
            }
        return installed
    }

    private fun logHitOnce(key: String, message: String) {
        if (loggedHits.add(key)) module.log(Log.INFO, TAG, message)
    }

    private fun Method.signature(): String =
        "$name(${parameterTypes.joinToString { it.simpleName }})"

    companion object {
        private const val TAG = "GeoShiftStrict"
        private const val FUSED_PROVIDER_CLASS = "com.android.location.fused.FusedLocationProvider"
        private const val FUSED_CHILD_LISTENER_CLASS = "com.android.location.fused.FusedLocationProvider\$ChildLocationListener"
    }
}
