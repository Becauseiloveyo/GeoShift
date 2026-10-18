package io.geoshift.probe

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Task
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

data class ProbeConfig(
    val sharedSecret: String,
    val expectedLat: Double?,
    val expectedLon: Double?,
    val role: String,
) {
    val expectedCoordinates: Boolean get() = expectedLat != null && expectedLon != null
    val isTarget: Boolean get() = role == "target"
}

/**
 * Runs only on explicit user action, on a worker thread. Values from location/radio
 * APIs are immediately reduced to outcomes and keyed fingerprints. No raw
 * coordinates, addresses, SSIDs, cell IDs or exception texts reach report/logging.
 */
class ProbeEngine(private val context: Context, private val config: ProbeConfig) {
    private val timeoutSeconds = 8L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val callbackExecutor = Executor { task -> mainHandler.post(task) }

    private fun sample(
        api: String,
        method: String,
        actual: ProbeActual,
        fingerprint: String? = null,
    ): ProbeObservation {
        val expected = when {
            config.isTarget && config.expectedCoordinates && api in setOf("framework", "fused") ->
                "TARGET_MATCH"
            !config.isTarget -> "UNCHANGED_FROM_BASELINE"
            else -> "OBSERVE_ONLY"
        }
        val verdict = when {
            expected == "TARGET_MATCH" && actual == ProbeActual.MATCH_TARGET -> ProbeVerdict.PASS
            expected == "TARGET_MATCH" && actual == ProbeActual.DIFFERENT -> ProbeVerdict.FAIL
            else -> ProbeVerdict.NOT_RUN
        }
        return ProbeObservation(api, method, expected, actual, verdict, fingerprint)
    }

    private fun hasLocationPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun location(api: String, method: String, fix: Location?): ProbeObservation {
        if (fix == null) return sample(api, method, ProbeActual.ABSENT)
        val latitude = fix.latitude
        val longitude = fix.longitude
        val fingerprint = ProbeFingerprint.location(config.sharedSecret, latitude, longitude)
        val matched = config.isTarget && config.expectedCoordinates &&
            ProbeFingerprint.nearExpected(latitude, longitude, config.expectedLat!!, config.expectedLon!!)
        val actual = if (matched) ProbeActual.MATCH_TARGET else if (config.isTarget && config.expectedCoordinates)
            ProbeActual.DIFFERENT else ProbeActual.PRESENT
        return sample(api, method, actual, fingerprint)
    }

    private fun guarded(api: String, method: String, block: () -> ProbeObservation): ProbeObservation =
        try { block() }
        catch (_: SecurityException) { sample(api, method, ProbeActual.DENIED) }
        catch (_: IllegalArgumentException) { sample(api, method, ProbeActual.UNSUPPORTED) }
        catch (_: Throwable) { sample(api, method, ProbeActual.ERROR) }

    private fun selectedProvider(manager: LocationManager): String? =
        when {
            manager.allProviders.contains(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            manager.allProviders.contains(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> null
        }

    fun run(): List<ProbeObservation> {
        val results = mutableListOf<ProbeObservation>()
        results += frameworkLast()
        results += frameworkCurrent()
        results += frameworkListener()
        results += fusedLast()
        results += fusedCurrent()
        results += geocoder()
        results += wifiConnection()
        results += wifiScans()
        results += telephonyOperator()
        results += telephonyCells()
        return results
    }

    private fun frameworkLast() = guarded("framework", "getLastKnownLocation") {
        if (!hasLocationPermission()) return@guarded sample("framework", "getLastKnownLocation", ProbeActual.DENIED)
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val provider = selectedProvider(manager)
            ?: return@guarded sample("framework", "getLastKnownLocation", ProbeActual.UNSUPPORTED)
        location("framework", "getLastKnownLocation", manager.getLastKnownLocation(provider))
    }

    private fun frameworkCurrent() = guarded("framework", "getCurrentLocation") {
        if (!hasLocationPermission()) return@guarded sample("framework", "getCurrentLocation", ProbeActual.DENIED)
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val provider = selectedProvider(manager)
            ?: return@guarded sample("framework", "getCurrentLocation", ProbeActual.UNSUPPORTED)
        val delivered = CountDownLatch(1)
        val answer = AtomicReference<Location?>()
        val cancellation = CancellationSignal()
        manager.getCurrentLocation(provider, cancellation, callbackExecutor) { fix ->
            answer.set(fix)
            delivered.countDown()
        }
        val completed = delivered.await(timeoutSeconds, TimeUnit.SECONDS)
        if (!completed) cancellation.cancel()
        if (!completed) sample("framework", "getCurrentLocation", ProbeActual.TIMEOUT)
        else location("framework", "getCurrentLocation", answer.get())
    }

    private fun frameworkListener() = guarded("framework", "requestLocationUpdates") {
        if (!hasLocationPermission()) return@guarded sample("framework", "requestLocationUpdates", ProbeActual.DENIED)
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val provider = selectedProvider(manager)
            ?: return@guarded sample("framework", "requestLocationUpdates", ProbeActual.UNSUPPORTED)
        val delivered = CountDownLatch(1)
        val answer = AtomicReference<Location?>()
        val listener = object : LocationListener {
            override fun onLocationChanged(fix: Location) {
                if (delivered.count > 0L) {
                    answer.set(fix)
                    delivered.countDown()
                }
            }
        }
        var registered = false
        try {
            manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
            registered = true
            val completed = delivered.await(timeoutSeconds, TimeUnit.SECONDS)
            if (!completed) sample("framework", "requestLocationUpdates", ProbeActual.TIMEOUT)
            else location("framework", "requestLocationUpdates", answer.get())
        } finally {
            if (registered) manager.removeUpdates(listener)
        }
    }

    private fun fusedTask(method: String, create: () -> Task<Location>): ProbeObservation =
        guarded("fused", method) {
            if (!hasLocationPermission()) return@guarded sample("fused", method, ProbeActual.DENIED)
            val done = CountDownLatch(1)
            val answer = AtomicReference<Location?>()
            val error = AtomicReference(false)
            create().addOnSuccessListener { fix ->
                answer.set(fix)
                done.countDown()
            }.addOnFailureListener {
                error.set(true)
                done.countDown()
            }
            if (!done.await(timeoutSeconds, TimeUnit.SECONDS))
                sample("fused", method, ProbeActual.TIMEOUT)
            else if (error.get()) sample("fused", method, ProbeActual.ERROR)
            else location("fused", method, answer.get())
        }

    private fun fusedLast(): ProbeObservation = fusedTask("getLastLocation") {
        LocationServices.getFusedLocationProviderClient(context).lastLocation
    }

    private fun fusedCurrent(): ProbeObservation {
        val source = com.google.android.gms.tasks.CancellationTokenSource()
        return try {
            fusedTask("getCurrentLocation") {
                LocationServices.getFusedLocationProviderClient(context).getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY, source.token
                )
            }
        } finally { source.cancel() }
    }

    private fun geocoder() = guarded("geocoder", "getFromLocation") {
        if (!config.expectedCoordinates) {
            return@guarded sample("geocoder", "getFromLocation", ProbeActual.NOT_RUN)
        }
        if (!Geocoder.isPresent())
            return@guarded sample("geocoder", "getFromLocation", ProbeActual.UNSUPPORTED)
        val geo = Geocoder(context, Locale.getDefault())
        val latitude = config.expectedLat!!
        val longitude = config.expectedLon!!
        if (Build.VERSION.SDK_INT >= 33) {
            val done = CountDownLatch(1)
            val result = AtomicReference<List<Address>>(emptyList())
            val failed = AtomicReference(false)
            geo.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                override fun onGeocode(addresses: MutableList<Address>) {
                    result.set(addresses)
                    done.countDown()
                }
                override fun onError(errorMessage: String?) {
                    failed.set(true)
                    done.countDown()
                }
            })
            if (!done.await(timeoutSeconds, TimeUnit.SECONDS)) {
                return@guarded sample("geocoder", "getFromLocation", ProbeActual.TIMEOUT)
            }
            if (failed.get()) return@guarded sample("geocoder", "getFromLocation", ProbeActual.ERROR)
            addressSample(result.get())
        } else {
            @Suppress("DEPRECATION")
            addressSample(geo.getFromLocation(latitude, longitude, 1).orEmpty())
        }
    }

    private fun addressSample(addresses: List<Address>): ProbeObservation {
        if (addresses.isEmpty()) return sample("geocoder", "getFromLocation", ProbeActual.ABSENT)
        val address = addresses.first()
        val coarse = listOf(address.countryCode, address.adminArea, address.locality)
            .filterNotNull().joinToString("|")
        return sample("geocoder", "getFromLocation", ProbeActual.PRESENT,
            ProbeFingerprint.digest(config.sharedSecret, "geocoder-v1", coarse))
    }

    private fun wifiConnection() = guarded("wifi", "getConnectionInfo") {
        val manager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val info = manager.connectionInfo
        val bssid = info?.bssid?.lowercase().orEmpty()
        if (bssid.isBlank() || bssid == "02:00:00:00:00:00")
            sample("wifi", "getConnectionInfo", ProbeActual.ABSENT)
        else sample("wifi", "getConnectionInfo", ProbeActual.PRESENT,
            ProbeFingerprint.digest(config.sharedSecret, "wifi-bssid-v1", bssid))
    }

    private fun wifiScans() = guarded("wifi", "getScanResults") {
        val manager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val raw = manager.scanResults.orEmpty()
        val accessPoints = raw.mapNotNull { it.BSSID?.lowercase() }
            .filter { it.matches(Regex("([0-9a-f]{2}:){5}[0-9a-f]{2}")) &&
                it != "02:00:00:00:00:00" }
            .distinct().sorted().take(8)
        if (accessPoints.isEmpty()) sample("wifi", "getScanResults", ProbeActual.ABSENT)
        else sample("wifi", "getScanResults", ProbeActual.PRESENT,
            ProbeFingerprint.digest(config.sharedSecret, "wifi-scan-v1", accessPoints.joinToString("|")))
    }

    private fun telephonyOperator() = guarded("telephony", "getNetworkOperator") {
        val manager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val operator = manager.networkOperator.orEmpty()
        if (operator.isBlank()) sample("telephony", "getNetworkOperator", ProbeActual.ABSENT)
        else sample("telephony", "getNetworkOperator", ProbeActual.PRESENT,
            ProbeFingerprint.digest(config.sharedSecret, "operator-v1", operator))
    }

    private fun telephonyCells() = guarded("telephony", "getAllCellInfo") {
        val manager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        val cells = manager.allCellInfo.orEmpty()
        if (cells.isEmpty()) sample("telephony", "getAllCellInfo", ProbeActual.ABSENT)
        else {
            // Raw CellIdentity text is used in memory only for a keyed HMAC;
            // it is never logged or persisted.
            val identities = cells.map { it.cellIdentity.toString() }.sorted().take(8)
            sample("telephony", "getAllCellInfo", ProbeActual.PRESENT,
                ProbeFingerprint.digest(config.sharedSecret, "cell-v1", identities.joinToString("|")))
        }
    }
}
