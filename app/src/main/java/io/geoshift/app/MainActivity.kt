package io.geoshift.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import io.geoshift.app.core.GeoProfile
import io.geoshift.app.core.ProfileCodec
import io.geoshift.app.core.ProfileDiagnostics
import io.geoshift.app.core.ProfileStore
import io.geoshift.app.core.ProviderSettings
import io.geoshift.app.network.CachingRadioEnvironmentProvider
import io.geoshift.app.network.CompositeRadioEnvironmentProvider
import io.geoshift.app.network.GeoProfileSynchronizer
import io.geoshift.app.network.OpenCellIdRadioEnvironmentProvider
import io.geoshift.app.network.RadioEnvironmentProvider
import io.geoshift.app.network.VpnDetector
import io.geoshift.app.network.WigleRadioEnvironmentProvider
import io.github.libxposed.service.XposedService
import java.text.DateFormat
import java.util.Date

class MainActivity : Activity() {
    private lateinit var serviceStatus: TextView
    private lateinit var geoStatus: TextView
    private lateinit var diagnosticsStatus: TextView
    private lateinit var radioStatus: TextView
    private lateinit var targetPackage: EditText
    private lateinit var timezone: EditText
    private lateinit var locale: EditText
    private lateinit var country: EditText
    private lateinit var latitude: EditText
    private lateinit var longitude: EditText
    private lateinit var openCellIdKey: EditText
    private lateinit var wigleTokenName: EditText
    private lateinit var wigleToken: EditText
    private lateinit var enabled: CheckBox
    private lateinit var followVpn: CheckBox
    private lateinit var timezoneEnabled: CheckBox
    private lateinit var localeEnabled: CheckBox
    private lateinit var locationEnabled: CheckBox

    private val vpnDetector by lazy { VpnDetector(this) }
    private val synchronizer = GeoProfileSynchronizer()
    private var vpnCallback: ConnectivityManager.NetworkCallback? = null
    @Volatile private var syncInFlight = false
    @Volatile private var radioQueryInFlight = false
    private var storedProfile: GeoProfile = GeoProfile()
    private var radioProvider: RadioEnvironmentProvider? = null

    private val serviceListener: (XposedService?) -> Unit = { service ->
        runOnUiThread {
            serviceStatus.text = if (service == null) "LSPosed service: unavailable"
            else "LSPosed service: ${service.frameworkName} / API ${service.apiVersion}"
            if (service != null) loadProfile(service)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "GeoShift"
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(32))
        }
        setContentView(ScrollView(this).apply { addView(content) })

        content.addView(TextView(this).apply { text = "GeoShift 0.2-dev"; textSize = 24f })
        content.addView(TextView(this).apply {
            text = "Per-app geographic profile for privacy and compatibility testing."
            textSize = 14f
        })
        serviceStatus = statusView("LSPosed service: loading")
        geoStatus = statusView("VPN/GeoIP: idle")
        diagnosticsStatus = statusView("Consistency: not checked")
        radioStatus = statusView("Radio environment: providers not configured")
        content.addView(serviceStatus); content.addView(geoStatus); content.addView(diagnosticsStatus); content.addView(radioStatus)

        enabled = checkBox("Enable profile", true)
        followVpn = checkBox("Follow VPN/public exit IP in background", false)
        timezoneEnabled = checkBox("Override time zone", true)
        localeEnabled = checkBox("Override locale", true)
        locationEnabled = checkBox("Override latitude/longitude", true)
        content.addView(enabled); content.addView(followVpn)
        targetPackage = field(content, "Target package", "com.example.app")
        content.addView(timezoneEnabled); timezone = field(content, "Time zone", "America/Los_Angeles")
        content.addView(localeEnabled); locale = field(content, "Locale tag", "en-US")
        country = field(content, "Country code", "US")
        content.addView(locationEnabled); latitude = field(content, "Latitude", "34.0522", decimal = true)
        longitude = field(content, "Longitude", "-118.2437", decimal = true)

        button(content, "Request LSPosed scope") { requestScope() }
        button(content, "Sync now from exit IP") { syncFromExitIp() }
        button(content, "Run consistency check") { runDiagnostics() }
        button(content, "Export profile JSON") { exportProfile() }
        button(content, "Import profile JSON") { importProfile() }
        button(content, "Save profile") { saveProfile(showToast = true) }

        content.addView(TextView(this).apply {
            text = "Optional nearby radio data providers"; textSize = 18f; setPadding(0, dp(18), 0, dp(6))
        })
        content.addView(TextView(this).apply {
            text = "Credentials stay in private local preferences and never enter LSPosed Remote Preferences or profile exports."
            textSize = 12f
        })
        openCellIdKey = field(content, "OpenCellID API key", "optional", secret = true)
        wigleTokenName = field(content, "WiGLE API token name", "optional")
        wigleToken = field(content, "WiGLE API token", "optional", secret = true)
        button(content, "Save provider credentials") { saveProviderSettings() }
        button(content, "Preview nearby Wi-Fi / cells") { previewRadioEnvironment() }
        loadProviderSettings()
    }

    override fun onStart() {
        super.onStart()
        GeoShiftApp.addServiceListener(serviceListener)
        vpnCallback = vpnDetector.register { state ->
            runOnUiThread { if (!syncInFlight) geoStatus.text = if (state.active) "VPN detected" else "VPN not detected" }
        }
    }

    override fun onStop() {
        vpnDetector.unregister(vpnCallback); vpnCallback = null
        GeoShiftApp.removeServiceListener(serviceListener)
        super.onStop()
    }

    @Deprecated("Deprecated in Android API; retained for framework-only document picker support")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            REQUEST_EXPORT -> runCatching {
                val profile = profileFromUi(); val errors = profile.validate(); require(errors.isEmpty()) { errors.first() }
                contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { it.write(ProfileCodec.encode(profile)) }
                    ?: error("Could not open export destination")
            }.onSuccess { toast("Profile exported") }.onFailure { toast("Export failed: ${it.message}") }
            REQUEST_IMPORT -> runCatching {
                val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Could not read profile file")
                ProfileCodec.decode(text)
            }.onSuccess { imported ->
                val errors = imported.validate()
                if (errors.isNotEmpty()) toast("Import rejected: ${errors.first()}") else {
                    storedProfile = imported; populateProfile(imported); saveProfile(false); runDiagnostics(); toast("Profile imported and saved")
                }
            }.onFailure { toast("Import failed: ${it.message}") }
        }
    }

    private fun requestScope() {
        val service = GeoShiftApp.service ?: return toast("LSPosed service is not connected")
        val pkg = targetPackage.text.toString().trim(); if (pkg.isBlank()) return toast("Enter a target package first")
        service.requestScope(listOf(pkg), object : XposedService.OnScopeEventListener {
            override fun onScopeRequestApproved(approved: List<String>) { runOnUiThread { toast("Scope approved: ${approved.joinToString()}") } }
            override fun onScopeRequestFailed(message: String) { runOnUiThread { toast("Scope request failed: $message") } }
        })
    }

    private fun loadProfile(service: XposedService) {
        val profile = ProfileStore.load(service); storedProfile = profile
        if (profile.targetPackage.isBlank()) return
        populateProfile(profile); updateLastSyncStatus(profile)
        if (profile.enabled && profile.followVpn) { requestNotificationPermissionIfUseful(); VpnFollowService.start(this) }
    }

    private fun populateProfile(profile: GeoProfile) {
        enabled.isChecked = profile.enabled; followVpn.isChecked = profile.followVpn
        timezoneEnabled.isChecked = profile.timezoneEnabled; localeEnabled.isChecked = profile.localeEnabled
        locationEnabled.isChecked = profile.locationEnabled; targetPackage.setText(profile.targetPackage)
        timezone.setText(profile.timezoneId); locale.setText(profile.localeTag); country.setText(profile.countryCode)
        latitude.setText(profile.latitude.toString()); longitude.setText(profile.longitude.toString())
    }

    private fun profileFromUi(): GeoProfile = storedProfile.copy(
        enabled = enabled.isChecked, targetPackage = targetPackage.text.toString().trim(), followVpn = followVpn.isChecked,
        timezoneEnabled = timezoneEnabled.isChecked, timezoneId = timezone.text.toString().trim(),
        localeEnabled = localeEnabled.isChecked, localeTag = locale.text.toString().trim(), countryCode = country.text.toString().trim().uppercase(),
        locationEnabled = locationEnabled.isChecked, latitude = latitude.text.toString().toDoubleOrNull() ?: Double.NaN,
        longitude = longitude.text.toString().toDoubleOrNull() ?: Double.NaN,
    )

    private fun saveProfile(showToast: Boolean) {
        val service = GeoShiftApp.service ?: run { if (showToast) toast("LSPosed service is not connected"); return }
        val profile = profileFromUi(); val errors = profile.validate()
        if (errors.isNotEmpty()) { if (showToast) toast(errors.first()); return }
        if (!ProfileStore.save(service, profile)) { if (showToast) toast("Could not save profile"); return }
        storedProfile = profile
        if (profile.enabled && profile.followVpn) { requestNotificationPermissionIfUseful(); VpnFollowService.start(this) }
        else VpnFollowService.stop(this)
        runDiagnostics(); if (showToast) toast("Profile saved")
    }

    private fun syncFromExitIp() {
        if (syncInFlight) return
        syncInFlight = true; geoStatus.text = "GeoIP: resolving current exit…"; val base = profileFromUi()
        Thread {
            try {
                val outcome = synchronizer.synchronize(base)
                runOnUiThread {
                    storedProfile = outcome.profile; populateProfile(outcome.profile); updateLastSyncStatus(outcome.profile)
                    if (followVpn.isChecked) saveProfile(false); runDiagnostics()
                }
            } catch (error: Throwable) { runOnUiThread { geoStatus.text = "GeoIP failed: ${error.message}" } }
            finally { syncInFlight = false }
        }.start()
    }

    private fun runDiagnostics() {
        val issues = ProfileDiagnostics.evaluate(profileFromUi())
        diagnosticsStatus.text = if (issues.isEmpty()) "Consistency: no obvious profile conflicts"
        else "Consistency: " + issues.joinToString(" · ") { "${it.severity}: ${it.message}" }
    }

    private fun loadProviderSettings() {
        val settings = ProviderSettings.load(this)
        openCellIdKey.setText(settings.openCellIdApiKey); wigleTokenName.setText(settings.wigleTokenName); wigleToken.setText(settings.wigleToken)
        radioProvider = buildRadioProvider(settings); updateProviderStatus(settings)
    }

    private fun saveProviderSettings() {
        val settings = ProviderSettings.Snapshot(openCellIdKey.text.toString(), wigleTokenName.text.toString(), wigleToken.text.toString())
        if (settings.wigleTokenName.isBlank() != settings.wigleToken.isBlank()) return toast("Enter both WiGLE token name and token")
        ProviderSettings.save(this, settings); radioProvider = buildRadioProvider(settings); updateProviderStatus(settings)
        toast("Provider credentials saved locally")
    }

    private fun buildRadioProvider(settings: ProviderSettings.Snapshot): RadioEnvironmentProvider? {
        val providers = buildList {
            if (settings.openCellIdApiKey.isNotBlank()) add(OpenCellIdRadioEnvironmentProvider(settings.openCellIdApiKey))
            if (settings.wigleTokenName.isNotBlank() && settings.wigleToken.isNotBlank()) add(WigleRadioEnvironmentProvider(settings.wigleTokenName, settings.wigleToken))
        }
        return if (providers.isEmpty()) null else CachingRadioEnvironmentProvider(CompositeRadioEnvironmentProvider(providers))
    }

    private fun updateProviderStatus(settings: ProviderSettings.Snapshot) {
        val active = buildList {
            if (settings.openCellIdApiKey.isNotBlank()) add("OpenCellID cells")
            if (settings.wigleTokenName.isNotBlank() && settings.wigleToken.isNotBlank()) add("WiGLE Wi-Fi")
        }
        radioStatus.text = if (active.isEmpty()) "Radio environment: providers not configured" else "Radio providers: ${active.joinToString()}"
    }

    private fun previewRadioEnvironment() {
        if (radioQueryInFlight) return
        val provider = radioProvider ?: return toast("Configure at least one radio data provider first")
        val lat = latitude.text.toString().toDoubleOrNull(); val lon = longitude.text.toString().toDoubleOrNull()
        if (lat == null || lon == null || !lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return toast("Enter valid latitude/longitude first")
        radioQueryInFlight = true; radioStatus.text = "Radio environment: querying nearby data…"
        Thread {
            try {
                val wifi = provider.nearbyWifi(lat, lon, 750)
                val cells = provider.nearbyCells(lat, lon, 900)
                runOnUiThread {
                    val wifiPreview = wifi.take(3).joinToString { it.ssid?.ifBlank { it.bssid } ?: it.bssid }
                    val cellPreview = cells.take(3).joinToString { "${it.radio} ${it.mcc}/${it.mnc}/${it.areaCode}/${it.cellId}" }
                    radioStatus.text = buildString {
                        append("Radio environment: ${wifi.size} Wi-Fi, ${cells.size} cells")
                        if (wifiPreview.isNotBlank()) append(" · Wi-Fi: $wifiPreview")
                        if (cellPreview.isNotBlank()) append(" · Cells: $cellPreview")
                    }
                }
            } catch (error: Throwable) { runOnUiThread { radioStatus.text = "Radio provider query failed: ${error.message}" } }
            finally { radioQueryInFlight = false }
        }.start()
    }

    private fun requestNotificationPermissionIfUseful() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    private fun updateLastSyncStatus(profile: GeoProfile) {
        if (profile.lastSyncIp.isBlank()) return
        val whenText = if (profile.lastSyncAtEpochMs > 0L) DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(profile.lastSyncAtEpochMs)) else "unknown time"
        val place = listOf(profile.lastSyncCity, profile.lastSyncRegion, profile.countryCode).filter { it.isNotBlank() }.joinToString(", ")
        geoStatus.text = "Last sync ${profile.lastSyncIp}: $place · $whenText"
    }

    private fun exportProfile() {
        val errors = profileFromUi().validate(); if (errors.isNotEmpty()) return toast(errors.first())
        val pkg = targetPackage.text.toString().trim().ifBlank { "profile" }.replace('.', '-')
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "application/json"; putExtra(Intent.EXTRA_TITLE, "GeoShift-$pkg.json")
        }, REQUEST_EXPORT)
    }

    private fun importProfile() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "application/json" }, REQUEST_IMPORT)
    }

    private fun field(parent: ViewGroup, label: String, hint: String, decimal: Boolean = false, secret: Boolean = false): EditText {
        parent.addView(TextView(this).apply { text = label })
        return EditText(this).also {
            it.hint = hint
            it.inputType = when {
                decimal -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
                secret -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                else -> InputType.TYPE_CLASS_TEXT
            }
            parent.addView(it)
        }
    }

    private fun button(parent: ViewGroup, text: String, action: () -> Unit) {
        parent.addView(Button(this).apply { this.text = text; setOnClickListener { action() } })
    }

    private fun checkBox(text: String, checked: Boolean) = CheckBox(this).apply { this.text = text; isChecked = checked }
    private fun statusView(initial: String) = TextView(this).apply { text = initial; setPadding(0, dp(10), 0, dp(10)) }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_EXPORT = 2001
        private const val REQUEST_IMPORT = 2002
        private const val REQUEST_NOTIFICATIONS = 2003
    }
}
