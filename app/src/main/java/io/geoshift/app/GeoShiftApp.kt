package io.geoshift.app

import android.app.Application
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.CopyOnWriteArraySet

class GeoShiftApp : Application(), XposedServiceHelper.OnServiceListener {
    companion object {
        @Volatile
        var service: XposedService? = null
            private set

        private val listeners = CopyOnWriteArraySet<(XposedService?) -> Unit>()

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
        listeners.forEach { it(service) }
    }

    override fun onServiceDied(service: XposedService) {
        if (GeoShiftApp.service === service) GeoShiftApp.service = null
        listeners.forEach { it(null) }
    }
}
