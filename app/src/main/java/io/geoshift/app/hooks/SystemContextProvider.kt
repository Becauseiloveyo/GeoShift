package io.geoshift.app.hooks

import android.content.Context

/** Obtains the already-created system context without keeping Activity objects alive. */
internal object SystemContextProvider {
    fun get(): Context? = runCatching {
        val activityThread = Class.forName("android.app.ActivityThread")
        val current = activityThread.getMethod("currentActivityThread").invoke(null) ?: return null
        activityThread.getMethod("getSystemContext").invoke(current) as? Context
    }.getOrNull()
}
