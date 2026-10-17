package io.geoshift.app.hooks

import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

/**
 * G02 safety gate. A broker has no reliable final calling-app identity; installing a
 * location rewrite or black-hole listener here can affect unrelated applications.
 *
 * Keep this libxposed entry point inert until G08/G09 owned-device isolation validation.
 * Deliberately no runtime setting or Strict command can enable broker-global hooks.
 */
class GeoShiftStrictPackageModule : XposedModule() {
    override fun onPackageReady(param: PackageReadyParam) {
        if (!param.isFirstPackage) return
        log(Log.INFO, "GeoShiftStrict", "Broker-global location adapters disabled (G02 safety gate)")
    }
}
