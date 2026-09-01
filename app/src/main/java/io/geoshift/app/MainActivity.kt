package io.geoshift.app

import android.app.Activity
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
import io.geoshift.app.core.ProfileStore
import io.geoshift.app.network.IpWhoIsGeoIpProvider
import io.geoshift.app.network.VpnDetector
import io.github.libxposed.service.XposedService
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var serviceStatus: TextView
    private lateinit var geoStatus: TextView
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
    private val geoIpProvider = IpWhoIsGeoIpProvider()
    private var vpnCallback: ConnectivityManager.NetworkCallback? = null
    @Volatile private var syncInFlight = false

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
            text = "GeoShift 0.1"
            textSize = 24f
        })
        content.addView(TextView(this).apply {
            text = "Per-app geographic profile for privacy and compatibility testing."
            textSize = 14f
        })

        serviceStatus = statusView("LSPosed service: loading")
        geoStatus = statusView("VPN/GeoIP: idle")
        content.addView(serviceStatus)
        content.addView(geoStatus)

        enabled = checkBox("Enable profile", true)
        followVpn = checkBox("Follow VPN/public exit IP while GeoShift is open", false)
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
            setOnClickListener { syncFromExitIp(force = true) }
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
                geoStatus.text = if (state.active) "VPN detected" else "VPN not detected"
                if (state.active && followVpn.isChecked) syncFromExitIp(force = false)
            }
        }
    }

    override fun onStop() {
        vpnDetector.unregister(vpnCallback)
        vpnCallback = null
        GeoShiftApp.removeServiceListener(serviceListener)
        super.onStop()
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
        if (profile.targetPackage.isBlank()) return
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

    private fun profileFromUi(): GeoProfile = GeoProfile(
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
        if (ProfileStore.save(service, profile) && showToast) toast("Profile saved")
    }

    private fun syncFromExitIp(force: Boolean) {
        if (syncInFlight) return
        if (!force && !vpnDetector.currentState().active) return
        syncInFlight = true
        geoStatus.text = "GeoIP: resolving current exit…"

        Thread {
            runCatching { geoIpProvider.lookupCurrentExit() }
                .onSuccess { result ->
                    runOnUiThread {
                        if (result.timezoneId.isNotBlank()) timezone.setText(result.timezoneId)
                        country.setText(result.countryCode)
                        latitude.setText(result.latitude.toString())
                        longitude.setText(result.longitude.toString())
                        if (locale.text.isNullOrBlank() || followVpn.isChecked) {
                            locale.setText(defaultLocaleForCountry(result.countryCode))
                        }
                        geoStatus.text = "Exit ${result.ip}: ${result.city}, ${result.region}, ${result.countryCode}"
                        if (followVpn.isChecked) saveProfile(showToast = false)
                    }
                }
                .onFailure { error ->
                    runOnUiThread { geoStatus.text = "GeoIP failed: ${error.message}" }
                }
            syncInFlight = false
        }.start()
    }

    private fun defaultLocaleForCountry(code: String): String {
        val countryCode = code.uppercase()
        val language = when (countryCode) {
            "CN", "TW", "HK", "MO" -> "zh"
            "JP" -> "ja"
            "KR" -> "ko"
            "DE", "AT" -> "de"
            "FR" -> "fr"
            "ES", "MX" -> "es"
            "IT" -> "it"
            "BR", "PT" -> "pt"
            "RU" -> "ru"
            else -> "en"
        }
        return Locale(language, countryCode).toLanguageTag()
    }

    private fun field(parent: ViewGroup, label: String, hint: String, decimal: Boolean = false): EditText {
        parent.addView(TextView(this).apply { text = label })
        return EditText(this).also {
            it.hint = hint
            if (decimal) it.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or InputType.TYPE_NUMBER_FLAG_SIGNED
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
}
