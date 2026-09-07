package io.geoshift.app.hooks

import android.util.Log
import io.geoshift.app.core.ProfileStoreV2
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

/**
 * Isolated package-side entry for Strict Map Session broker/private-location adapters.
 * Normal GeoShift per-app hooks remain independent in GeoShiftModule.
 */
class GeoShiftStrictPackageModule : XposedModule() {
    private var session: StrictProcessSession? = null

    override fun onPackageReady(param: PackageReadyParam) {
        if (!param.isFirstPackage) return

        val prefs = runCatching { getRemotePreferences(ProfileStoreV2.REMOTE_PREFS) }
            .getOrElse {
                log(Log.WARN, TAG, "Unable to open strict Remote Preferences in ${param.packageName}", it)
                return
            }

        val runtime = StrictProcessSession(this, prefs, param.packageName)
        session = runtime // retain the SharedPreferences listener for the lifetime of the process.

        val report = StrictThirdPartyLocationHooks(
            module = this,
            classLoader = param.classLoader,
            strictProfile = runtime::profile,
        ).install()

        if (report.amapPresent || param.packageName == XIAOMI_FUSED_PACKAGE) {
            log(
                Log.INFO,
                TAG,
                "Strict package adapters in ${param.packageName}: amap=${report.amapPresent}, request=${report.requestHooks}, extra=${report.extraCommandHooks}, offline=${report.offlineHooks}",
            )
        }
    }

    companion object {
        private const val TAG = "GeoShiftStrict"
        private const val XIAOMI_FUSED_PACKAGE = "com.xiaomi.location.fused"
    }
}
