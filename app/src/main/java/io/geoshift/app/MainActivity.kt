package io.geoshift.app

import android.app.Activity
import android.content.Intent
import android.net.ConnectivityManager
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
import io.geoshift.app.network.GeoProfileSynchronizer
import io.geoshift.app.network.VpnDetector
import io.github.libxposed.service.XposedService
import java.text.DateFormat
import java.util.Date

class MainActivity : Activity() {
    private lateinit var serviceStatus: TextView
    private lateinit var geoStatus: TextView
    private lateinit var diagnosticsStatus: TextView
    private lateinit var targetPackage: EditText
    private lateinit var timezone: EditText
    private lateinit var locale: EditText
    private lateinit var country: EditText
    private lateinit var latitude: EditText
    private lateinit var longitude: EditText
    private lateinit var enabled: CheckBox
    private lateinit var followVpn: CheckBox
    private lateinit var timezoneEnabled: CheckBox
    private lateinit var localeEnabled: CheckBox
    private lateinit var locationEnabled: CheckBox

    private val vpnDetector by lazy { VpnDetector(this) }
    private val synchronizer = GeoProfileSynchronizer()
    private var vpnCallback: ConnectivityManager.NetworkCallback? = null
    @Volatile private var syncInFlight = false
    private var storedProfile: GeoProfile = GeoProfile()

    private val serviceListener: (XposedService?) -> Unit = { service ->
        runOnUiThread {
            serviceStatus.text = if (service == null) {
                "LSPosed service: unavailable"
            } else {
                "LSPosed service: ${service.frameworkName} / API ${service.apiVersion}"
            }
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
        val scroll = ScrollView(this).apply { addView(content) }
        setContentView(scroll)

        content.addView(TextView(this).apply {
            text = "GeoShift 0.2-dev"
            textSize = 24f
        })
        content.addView(TextView(this).apply {
            text = "Per-app geographic profile for privacy and compatibility testing."
            textSize = 14f
        })

        serviceStatus = statusView("LSPosed service: loading")
        geoStatus = statusView("VPN/GeoIP: idle")
        diagnosticsStatus = statusView("Consistency: not checked")
        content.addView(serviceStatus)
        content.addView(geoStatus)
        content.addView(diagnosticsStatus)

        enabled = checkBox("Enable profile", true)
        followVpn = checkBox("Follow VPN/public exit IP in background", false)
        timezoneEnabled = checkBox("Override time zone", true)
        localeEnabled = checkBox("Override locale", true)
        locationEnabled = checkBox("Override latitude/longitude", true)
        content.addView(enabled)
        content.addView(followVpn)

        targetPackage = field(content, "Target package", "com.example.app")
        content.addView(timezoneEnabled)
        timezone = field(content, "Time zone", "America/Los_Angeles")
        content.addView(localeEnabled)
        locale = field(content, "Locale tag", "en-US")
        country = field(content, "Country code", "US")
        content.addView(locationEnabled)
        latitude = field(content, "Latitude", "34.0522", decimal = true)
        longitude = field(content, "Longitude", "-118.2437", decimal = true)

        content.addView(Button(this).apply {
            text = "Request LSPosed scope"
            setOnClickListener { requestScope() }
        })
        content.addView(Button(this).apply {
            text = "Sync now from exit IP"
            setOnClickListener { syncFromExitIp() }
        })
        content.addView(Button(this).apply {
            text = "Run consistency check"
            setOnClickListener { runDiagnostics() }
        })
        content.addView(Button(this).apply {
            text = "Export profile JSON"
            setOnClickListener { exportProfile() }
        })
        content.addView(Button(this).apply {
            text = "Import profile JSON"
            setOnClickListener { importProfile() }
        })
        content.addView(Button(this).apply {
            text = "Save profile"
            setOnClickListener { saveProfile(showToast = true) }
        })
    }

    override fun onStart() {
        super.onStart()
        GeoShiftApp.addServiceListener(serviceListener)
        vpnCallback = vpnDetector.register { state ->
            runOnUiThread {
                if (!syncInFlight) {
                    geoStatus.text = if (state.active) "VPN detected" else "VPN not detected"
                }
            }
        }
    }

    override fun onStop() {
        vpnDetector.unregister(vpnCallback)
        vpnCallback = null
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
                val profile = profileFromUi()
                val errors = profile.validate()
                require(errors.isEmpty()) { errors.first() }
                contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use {
                    it.write(ProfileCodec.encode(profile))
                } ?: error("Could not open export destination")
            }.onSuccess {
                toast("Profile exported")
            }.onFailure {
                toast("Export failed: ${it.message}")
            }

            REQUEST_IMPORT -> runCatching {
                val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Could not read profile file")
                ProfileCodec.decode(text)
            }.onSuccess { imported ->
                val errors = imported.validate()
                if (errors.isNotEmpty()) {
                    toast("Import rejected: ${errors.first()}")
                } else {
                    storedProfile = imported
                    populateProfile(imported)
                    saveProfile(showToast = false)
                    runDiagnostics()
                    toast("Profile imported and saved")
                }
            }.onFailure {
                toast("Import failed: ${it.message}")
            }
        }
    }

    private fun requestScope() {
        val service = GeoShiftApp.service ?: return toast("LSPosed service is not connected")
        val pkg = targetPackage.text.toString().trim()
        if (pkg.isBlank()) return toast("Enter a target package first")

        service.requestScope(listOf(pkg), object : XposedService.OnScopeEventListener {
            override fun onScopeRequestApproved(approved: List<String>) {
                runOnUiThread { toast("Scope approved: ${approved.joinToString()}") }
            }

            override fun onScopeRequestFailed(message: String) {
                runOnUiThread { toast("Scope request failed: $message") }
            }
        })
    }

    private fun loadProfile(service: XposedService) {
        val profile = ProfileStore.load(service)
        storedProfile = profile
        if (profile.targetPackage.isBlank()) return
        populateProfile(profile)
        updateLastSyncStatus(profile)
        if (profile.enabled && profile.followVpn) VpnFollowService.start(this)
    }

    private fun populateProfile(profile: GeoProfile) {
        enabled.isChecked = profile.enabled
        followVpn.isChecked = profile.followVpn
        timezoneEnabled.isChecked = profile.timezoneEnabled
        localeEnabled.isChecked = profile.localeEnabled
        locationEnabled.isChecked = profile.locationEnabled
        targetPackage.setText(profile.targetPackage)
        timezone.setText(profile.timezoneId)
        locale.setText(profile.localeTag)
        country.setText(profile.countryCode)
        latitude.setText(profile.latitude.toString())
        longitude.setText(profile.longitude.toString())
    }

    private fun profileFromUi(): GeoProfile = storedProfile.copy(
        enabled = enabled.isChecked,
        targetPackage = targetPackage.text.toString().trim(),
        followVpn = followVpn.isChecked,
        timezoneEnabled = timezoneEnabled.isChecked,
        timezoneId = timezone.text.toString().trim(),
        localeEnabled = localeEnabled.isChecked,
        localeTag = locale.text.toString().trim(),
        countryCode = country.text.toString().trim().uppercase(),
        locationEnabled = locationEnabled.isChecked,
        latitude = latitude.text.toString().toDoubleOrNull() ?: Double.NaN,
        longitude = longitude.text.toString().toDoubleOrNull() ?: Double.NaN,
    )

    private fun saveProfile(showToast: Boolean) {
        val service = GeoShiftApp.service ?: run {
            if (showToast) toast("LSPosed service is not connected")
            return
        }
        val profile = profileFromUi()
        val errors = profile.validate()
        if (errors.isNotEmpty()) {
            if (showToast) toast(errors.first())
            return
        }
        if (!ProfileStore.save(service, profile)) {
            if (showToast) toast("Could not save profile")
            return
        }
        storedProfile = profile
        if (profile.enabled && profile.followVpn) {
            VpnFollowService.start(this)
        } else {
            VpnFollowService.stop(this)
        }
        runDiagnostics()
        if (showToast) toast("Profile saved")
    }

    private fun syncFromExitIp() {
        if (syncInFlight) return
        syncInFlight = true
        geoStatus.text = "GeoIP: resolving current exit…"
        val base = profileFromUi()

        Thread {
            try {
                val outcome = synchronizer.synchronize(base)
                runOnUiThread {
                    storedProfile = outcome.profile
                    populateProfile(outcome.profile)
                    updateLastSyncStatus(outcome.profile)
                    if (followVpn.isChecked) saveProfile(showToast = false)
                    runDiagnostics()
                }
            } catch (error: Throwable) {
                runOnUiThread { geoStatus.text = "GeoIP failed: ${error.message}" }
            } finally {
                syncInFlight = false
            }
        }.start()
    }

    private fun runDiagnostics() {
        val issues = ProfileDiagnostics.evaluate(profileFromUi())
        diagnosticsStatus.text = if (issues.isEmpty()) {
            "Consistency: no obvious profile conflicts"
        } else {
            "Consistency: " + issues.joinToString(" · ") { "${it.severity}: ${it.message}" }
        }
    }

    private fun updateLastSyncStatus(profile: GeoProfile) {
        if (profile.lastSyncIp.isBlank()) return
        val whenText = if (profile.lastSyncAtEpochMs > 0L) {
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(Date(profile.lastSyncAtEpochMs))
        } else {
            "unknown time"
        }
        val place = listOf(profile.lastSyncCity, profile.lastSyncRegion, profile.countryCode)
            .filter { it.isNotBlank() }
            .joinToString(", ")
        geoStatus.text = "Last sync ${profile.lastSyncIp}: $place · $whenText"
    }

    private fun exportProfile() {
        val errors = profileFromUi().validate()
        if (errors.isNotEmpty()) return toast(errors.first())
        val pkg = targetPackage.text.toString().trim().ifBlank { "profile" }.replace('.', '-')
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "GeoShift-$pkg.json")
        }, REQUEST_EXPORT)
    }

    private fun importProfile() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }, REQUEST_IMPORT)
    }

    private fun field(parent: ViewGroup, label: String, hint: String, decimal: Boolean = false): EditText {
        parent.addView(TextView(this).apply { text = label })
        return EditText(this).also {
            it.hint = hint
            if (decimal) {
                it.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or
                    InputType.TYPE_NUMBER_FLAG_SIGNED
            }
            parent.addView(it)
        }
    }

    private fun checkBox(text: String, checked: Boolean) = CheckBox(this).apply {
        this.text = text
        isChecked = checked
    }

    private fun statusView(initial: String) = TextView(this).apply {
        text = initial
        setPadding(0, dp(10), 0, dp(10))
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_EXPORT = 2001
        private const val REQUEST_IMPORT = 2002
    }
}
