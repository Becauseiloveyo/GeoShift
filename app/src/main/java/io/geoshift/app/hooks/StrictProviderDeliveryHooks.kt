package io.geoshift.app.hooks

import android.location.Location
import android.util.Log
import io.geoshift.app.core.GeoProfile
import io.geoshift.app.core.SystemRuntimeSnapshot
import io.github.libxposed.api.XposedModule
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/**
 * Provider-global delivery rewrite used only while an explicit Strict Map Session is armed.
 *
 * This intentionally does nothing during normal per-app operation. It targets the shared provider
 * delivery layer because brokered fused/network updates may no longer carry the original client
 * UID by the time they reach system_server. Any unsupported shape fails open to the original value.
 */
internal class StrictProviderDeliveryHooks(
    private val module: XposedModule,
    private val classLoader: ClassLoader,
    private val snapshot: () -> SystemRuntimeSnapshot,
) {
    data class InstallReport(
        val providerHooks: Int,
        val gnssHooks: Int,
    ) {
        val total: Int get() = providerHooks + gnssHooks
    }

    fun install(): InstallReport {
        val provider = hookClass(
            "com.android.server.location.provider.AbstractLocationProvider",
            setOf("reportLocation", "onReportLocation"),
        ) + hookClass(
            "com.android.server.location.provider.LocationProviderManager",
            setOf("onReportLocation"),
        ) + hookClass(
            "com.android.server.location.provider.PassiveLocationProvider",
            setOf("updateLocation", "onReportLocation"),
        )

        val gnss = hookClass(
            "com.android.server.location.gnss.GnssLocationProvider",
            setOf("onReportLocation", "onReportLocations"),
        )

        return InstallReport(provider, gnss)
    }

    private fun hookClass(className: String, methodNames: Set<String>): Int {
        val targetClass = runCatching { Class.forName(className, false, classLoader) }
            .getOrElse { return 0 }

        var installed = 0
        targetClass.declaredMethods
            .filter { it.name in methodNames }
            .forEach { method ->
                runCatching {
                    module.hook(method).intercept { chain ->
                        val profile = snapshot().strictProfile()
                            ?: return@intercept chain.proceed()

                        val forwarded = chain.args.toTypedArray()
                        var changed = false
                        for (index in forwarded.indices) {
                            val original = forwarded[index]
                            val rewritten = rewriteValue(original, profile)
                            if (rewritten !== original) {
                                forwarded[index] = rewritten
                                changed = true
                            }
                        }

                        if (changed) chain.proceed(forwarded) else chain.proceed()
                    }
                    installed++
                }.onFailure {
                    module.log(Log.WARN, TAG, "Failed to install strict provider hook ${method.signature()}", it)
                }
            }
        return installed
    }

    private fun rewriteValue(value: Any?, profile: GeoProfile): Any? = when (value) {
        is Location -> SystemLocationRewriter.rewrite(value, profile)
        is List<*> -> rewriteLocationList(value, profile)
        is Array<*> -> rewriteLocationArray(value, profile)
        null -> null
        else -> if (value.javaClass.name == LOCATION_RESULT_CLASS) {
            rewriteLocationResult(value, profile)
        } else {
            value
        }
    }

    private fun rewriteLocationList(value: List<*>, profile: GeoProfile): Any {
        if (value.none { it is Location }) return value
        return value.map { item ->
            if (item is Location) SystemLocationRewriter.rewrite(item, profile) else item
        }
    }

    private fun rewriteLocationArray(value: Array<*>, profile: GeoProfile): Any {
        if (value.javaClass.componentType != Location::class.java) return value
        @Suppress("UNCHECKED_CAST")
        val locations = value as Array<Location>
        return Array(locations.size) { index ->
            SystemLocationRewriter.rewrite(locations[index], profile) ?: locations[index]
        }
    }

    private fun rewriteLocationResult(value: Any, profile: GeoProfile): Any {
        return runCatching {
            val resultClass = value.javaClass
            val asList = resultClass.declaredMethods.firstOrNull {
                it.name == "asList" && it.parameterCount == 0
            } ?: return@runCatching value
            asList.isAccessible = true
            val originalLocations = asList.invoke(value) as? List<*> ?: return@runCatching value
            if (originalLocations.isEmpty() || originalLocations.any { it !is Location }) return@runCatching value

            val rewritten = originalLocations.map { item ->
                SystemLocationRewriter.rewrite(item as Location, profile) ?: item
            }

            val create = resultClass.declaredMethods.firstOrNull { method ->
                method.name == "create" &&
                    Modifier.isStatic(method.modifiers) &&
                    method.returnType == resultClass &&
                    method.parameterCount == 1 &&
                    List::class.java.isAssignableFrom(method.parameterTypes[0])
            } ?: return@runCatching value
            create.isAccessible = true
            create.invoke(null, rewritten) ?: value
        }.onFailure {
            module.log(Log.WARN, TAG, "Strict LocationResult rewrite failed open", it)
        }.getOrDefault(value)
    }

    private fun Method.signature(): String = "$declaringClass#$name(${parameterTypes.joinToString { it.simpleName }})"

    companion object {
        private const val TAG = "GeoShiftStrict"
        private const val LOCATION_RESULT_CLASS = "android.location.LocationResult"
    }
}
