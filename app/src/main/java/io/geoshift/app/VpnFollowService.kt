package io.geoshift.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import io.geoshift.app.core.ProfileStoreV2
import io.geoshift.app.network.GeoProfileSynchronizer
import io.geoshift.app.network.VpnDetector
import io.github.libxposed.service.XposedService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class VpnFollowService : Service() {
    private val detector by lazy { VpnDetector(this) }
    private val synchronizer = GeoProfileSynchronizer()
    private val executor = Executors.newSingleThreadExecutor()
    private val syncing = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val delayedSync = Runnable { syncNow() }
    private val xposedListener: (XposedService?) -> Unit = { service ->
        if (service != null) scheduleSync()
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        promoteToForeground("Starting VPN-follow synchronization")
        GeoShiftApp.addServiceListener(xposedListener)
        networkCallback = detector.register { state ->
            if (state.active) {
                updateNotification("VPN detected; checking exit IP")
                scheduleSync()
            } else {
                updateNotification("Waiting for an active VPN")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scheduleSync()
        return START_STICKY
    }

    override fun onDestroy() {
        mainHandler.removeCallbacks(delayedSync)
        detector.unregister(networkCallback)
        networkCallback = null
        GeoShiftApp.removeServiceListener(xposedListener)
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun scheduleSync() {
        mainHandler.removeCallbacks(delayedSync)
        mainHandler.postDelayed(delayedSync, 1200L)
    }

    private fun syncNow() {
        val service = GeoShiftApp.service ?: run {
            updateNotification("Waiting for LSPosed service")
            return
        }
        val profiles = ProfileStoreV2.list(service).filter { it.enabled && it.followVpn }
        if (profiles.isEmpty()) {
            stopSelf()
            return
        }
        if (!detector.currentState().active) {
            updateNotification("Waiting for an active VPN")
            return
        }
        if (!syncing.compareAndSet(false, true)) return

        executor.execute {
            try {
                // Resolve the public exit exactly once so every followed app receives
                // one coherent geographic snapshot for this network transition.
                val geoIp = synchronizer.resolveCurrentExit()
                val now = System.currentTimeMillis()
                var saved = 0
                for (profile in profiles) {
                    val outcome = synchronizer.synchronize(profile, geoIp, now)
                    val errors = outcome.profile.validate()
                    if (errors.isEmpty() && ProfileStoreV2.save(service, outcome.profile)) saved++
                }
                val place = listOf(geoIp.city, geoIp.countryCode)
                    .filter { it.isNotBlank() }
                    .joinToString(", ")
                updateNotification("Synced $saved/${profiles.size} profiles · ${geoIp.ip} · $place")
            } catch (error: Throwable) {
                updateNotification("GeoIP sync failed: ${error.message ?: error.javaClass.simpleName}")
            } finally {
                syncing.set(false)
            }
        }
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "GeoShift VPN follow", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows status while GeoShift follows the active VPN exit location"
                setShowBadge(false)
            }
        )
    }

    private fun promoteToForeground(status: String) {
        val notification = buildNotification(status)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(status: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(status))
    }

    private fun buildNotification(status: String): Notification = Notification.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_menu_mylocation)
        .setContentTitle("GeoShift · Follow VPN")
        .setContentText(status)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .build()

    companion object {
        private const val CHANNEL_ID = "geoshift_vpn_follow"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            context.startForegroundService(Intent(context, VpnFollowService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, VpnFollowService::class.java))
        }
    }
}
