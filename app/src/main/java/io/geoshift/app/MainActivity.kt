package io.geoshift.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.geoshift.app.core.GeoProfile
import io.geoshift.app.core.ProfileCodec
import io.geoshift.app.core.ProfileStoreV2
import io.geoshift.app.core.ProviderSettings
import io.geoshift.app.network.CachingRadioEnvironmentProvider
import io.geoshift.app.network.CompositeRadioEnvironmentProvider
import io.geoshift.app.network.GeoProfileSynchronizer
import io.geoshift.app.network.OpenCellIdRadioEnvironmentProvider
import io.geoshift.app.network.RadioEnvironmentProvider
import io.geoshift.app.network.VpnDetector
import io.geoshift.app.network.WigleRadioEnvironmentProvider
import io.geoshift.app.ui.AppChoice
import io.geoshift.app.ui.Destination
import io.geoshift.app.ui.GeoShiftActions
import io.geoshift.app.ui.GeoShiftAppScreen
import io.geoshift.app.ui.GeoShiftTheme
import io.geoshift.app.ui.GeoShiftUiState
import io.github.libxposed.service.XposedService
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    private val vpnDetector by lazy { VpnDetector(this) }
    private val synchronizer = GeoProfileSynchronizer()
    private var vpnCallback: ConnectivityManager.NetworkCallback? = null

    private var destination by mutableStateOf(Destination.Overview)
    private var serviceConnected by mutableStateOf(false)
    private var serviceLabel by mutableStateOf("Connecting to LSPosed…")
    private var vpnActive by mutableStateOf(false)
    private var profiles by mutableStateOf<List<GeoProfile>>(emptyList())
    private var providerSettings by mutableStateOf(ProviderSettings.Snapshot())
    private var syncStatus by mutableStateOf("No synchronization yet")
    private var radioStatus by mutableStateOf("Radio providers not configured")
    private var notice by mutableStateOf<String?>(null)
    private var syncInFlight by mutableStateOf(false)
    private var radioQueryInFlight by mutableStateOf(false)
    private var editingProfile by mutableStateOf<GeoProfile?>(null)
    private var editingOriginalPackage by mutableStateOf<String?>(null)
    private var launchableApps by mutableStateOf<List<AppChoice>>(emptyList())
    private var pendingExportProfile: GeoProfile? = null

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val profile = pendingExportProfile
        pendingExportProfile = null
        if (uri == null || profile == null) return@registerForActivityResult
        runCatching {
            contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use {
                it.write(ProfileCodec.encode(profile))
            } ?: error("Could not open export destination")
        }.onSuccess { showNotice("Profile exported") }
            .onFailure { toast("Export failed: ${it.message}") }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        val service = GeoShiftApp.service ?: return@registerForActivityResult toast("LSPosed service is not connected")
        runCatching {
            val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("Could not read profile file")
            ProfileCodec.decode(text)
        }.onSuccess { imported ->
            val errors = imported.validate()
            if (errors.isNotEmpty()) {
                toast("Import rejected: ${errors.first()}")
            } else if (ProfileStoreV2.save(service, imported)) {
                refreshProfiles(service)
                destination = Destination.Profiles
                editingProfile = imported
                editingOriginalPackage = imported.targetPackage
                showNotice("Profile imported")
            }
        }.onFailure { toast("Import failed: ${it.message}") }
    }

    private val serviceListener: (XposedService?) -> Unit = { service ->
        runOnUiThread {
            serviceConnected = service != null
            serviceLabel = if (service == null) "LSPosed service unavailable"
            else "${service.frameworkName} · API ${service.apiVersion}"
            if (service != null) refreshProfiles(service)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        providerSettings = ProviderSettings.load(this)
        updateProviderStatus()

        Thread {
            val apps = loadLaunchableApps()
            runOnUiThread { launchableApps = apps }
        }.start()

        setContent {
            GeoShiftTheme {
                GeoShiftAppScreen(
                    state = GeoShiftUiState(
                        destination = destination,
                        serviceConnected = serviceConnected,
                        serviceLabel = serviceLabel,
                        vpnActive = vpnActive,
                        profiles = profiles,
                        providerSettings = providerSettings,
                        syncStatus = if (serviceConnected) "$serviceLabel · $syncStatus" else serviceLabel,
                        radioStatus = radioStatus,
                        notice = notice,
                        isSyncing = syncInFlight,
                        isRadioBusy = radioQueryInFlight,
                        editingProfile = editingProfile,
                        editingOriginalPackage = editingOriginalPackage,
                    ),
                    apps = launchableApps,
                    actions = GeoShiftActions(
                        navigate = { destination = it },
                        addProfile = { openNewProfile() },
                        editProfile = { openProfile(it) },
                        closeEditor = { closeEditor() },
                        saveProfile = ::saveProfile,
                        deleteProfile = ::deleteProfile,
                        toggleProfile = ::toggleProfile,
                        syncProfile = ::syncProfile,
                        syncAll = ::syncAllFollowed,
                        requestScope = ::requestScope,
                        exportProfile = ::exportProfile,
                        importProfile = { importLauncher.launch(arrayOf("application/json", "text/json")) },
                        saveProviders = ::saveProviderSettings,
                        previewRadio = ::previewRadioEnvironment,
                        applyRadioSuggestion = ::applyRadioSuggestion,
                        clearNotice = { notice = null },
                    ),
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        GeoShiftApp.addServiceListener(serviceListener)
        vpnCallback = vpnDetector.register { state ->
            runOnUiThread { vpnActive = state.active }
        }
    }

    override fun onStop() {
        vpnDetector.unregister(vpnCallback)
        vpnCallback = null
        GeoShiftApp.removeServiceListener(serviceListener)
        super.onStop()
    }

    private fun refreshProfiles(service: XposedService) {
        profiles = ProfileStoreV2.list(service)
        val latest = profiles.maxByOrNull { it.lastSyncAtEpochMs }
        if (!syncInFlight && latest?.lastSyncIp?.isNotBlank() == true) {
            syncStatus = formatLastSync(latest)
        }
        updateFollowService(requestPermission = false)
    }

    private fun openNewProfile() {
        editingOriginalPackage = null
        editingProfile = GeoProfile(followVpn = true)
    }

    private fun openProfile(profile: GeoProfile) {
        editingOriginalPackage = profile.targetPackage
        editingProfile = profile
    }

    private fun closeEditor() {
        editingProfile = null
        editingOriginalPackage = null
    }

    private fun saveProfile(profile: GeoProfile, originalPackage: String?) {
        val service = GeoShiftApp.service ?: return toast("LSPosed service is not connected")
        val errors = profile.validate()
        if (errors.isNotEmpty()) return toast(errors.first())
        if (!originalPackage.isNullOrBlank() && originalPackage != profile.targetPackage) {
            ProfileStoreV2.delete(service, originalPackage)
        }
        if (!ProfileStoreV2.save(service, profile)) return toast("Could not save profile")
        refreshProfiles(service)
        if (profile.enabled && profile.followVpn) updateFollowService(requestPermission = true)
        closeEditor()
        showNotice("Profile saved")
    }

    private fun toggleProfile(profile: GeoProfile) {
        val service = GeoShiftApp.service ?: return toast("LSPosed service is not connected")
        if (!ProfileStoreV2.save(service, profile)) return toast("Could not update profile")
        refreshProfiles(service)
    }

    private fun deleteProfile(packageName: String) {
        val service = GeoShiftApp.service ?: return toast("LSPosed service is not connected")
        if (ProfileStoreV2.delete(service, packageName)) {
            refreshProfiles(service)
            closeEditor()
            showNotice("Profile deleted")
        }
    }

    private fun syncProfile(base: GeoProfile) {
        val service = GeoShiftApp.service ?: return toast("LSPosed service is not connected")
        if (syncInFlight) return
        val errors = base.validate()
        if (errors.isNotEmpty()) return toast(errors.first())
        syncInFlight = true
        syncStatus = "Resolving current public exit…"
        Thread {
            try {
                val outcome = synchronizer.synchronize(base)
                val updatedErrors = outcome.profile.validate()
                if (updatedErrors.isNotEmpty()) error(updatedErrors.first())
                if (!ProfileStoreV2.save(service, outcome.profile)) error("Could not save synchronized profile")
                runOnUiThread {
                    editingProfile = outcome.profile
                    editingOriginalPackage = outcome.profile.targetPackage
                    syncStatus = "${outcome.geoIp.ip} · ${outcome.geoIp.city}, ${outcome.geoIp.countryCode}"
                    refreshProfiles(service)
                    showNotice("Profile synchronized with VPN exit")
                }
            } catch (error: Throwable) {
                runOnUiThread {
                    syncStatus = "Sync failed: ${error.message ?: error.javaClass.simpleName}"
                    showNotice(syncStatus)
                }
            } finally {
                runOnUiThread { syncInFlight = false }
            }
        }.start()
    }

    private fun syncAllFollowed() {
        val service = GeoShiftApp.service ?: return toast("LSPosed service is not connected")
        if (syncInFlight) return
        val followed = profiles.filter { it.enabled && it.followVpn }
        if (followed.isEmpty()) return toast("No enabled Follow VPN profiles")
        syncInFlight = true
        syncStatus = "Resolving one shared exit for ${followed.size} profiles…"
        Thread {
            try {
                val geoIp = synchronizer.resolveCurrentExit()
                val now = System.currentTimeMillis()
                var saved = 0
                followed.forEach { profile ->
                    val outcome = synchronizer.synchronize(profile, geoIp, now)
                    if (outcome.profile.validate().isEmpty() && ProfileStoreV2.save(service, outcome.profile)) saved++
                }
                runOnUiThread {
                    syncStatus = "Synced $saved/${followed.size} · ${geoIp.ip} · ${geoIp.city}, ${geoIp.countryCode}"
                    refreshProfiles(service)
                    showNotice("Synchronized $saved Follow VPN profiles")
                }
            } catch (error: Throwable) {
                runOnUiThread {
                    syncStatus = "Sync failed: ${error.message ?: error.javaClass.simpleName}"
                    showNotice(syncStatus)
                }
            } finally {
                runOnUiThread { syncInFlight = false }
            }
        }.start()
    }

    private fun requestScope(packageName: String) {
        val service = GeoShiftApp.service ?: return toast("LSPosed service is not connected")
        val pkg = packageName.trim()
        if (pkg.isBlank()) return toast("Choose an app first")
        service.requestScope(listOf(pkg), object : XposedService.OnScopeEventListener {
            override fun onScopeRequestApproved(approved: List<String>) {
                runOnUiThread { showNotice("Scope approved: ${approved.joinToString()}") }
            }

            override fun onScopeRequestFailed(message: String) {
                runOnUiThread { toast("Scope request failed: $message") }
            }
        })
    }

    private fun exportProfile(profile: GeoProfile) {
        val errors = profile.validate()
        if (errors.isNotEmpty()) return toast(errors.first())
        pendingExportProfile = profile
        val name = profile.targetPackage.replace('.', '-').ifBlank { "profile" }
        exportLauncher.launch("GeoShift-$name.json")
    }

    private fun saveProviderSettings(settings: ProviderSettings.Snapshot) {
        if (settings.wigleTokenName.isBlank() != settings.wigleToken.isBlank()) {
            return toast("Enter both WiGLE token name and token")
        }
        ProviderSettings.save(this, settings)
        providerSettings = ProviderSettings.load(this)
        updateProviderStatus()
        showNotice("Provider settings saved locally")
    }

    private fun updateProviderStatus() {
        val active = buildList {
            if (providerSettings.openCellIdApiKey.isNotBlank()) add("OpenCellID cells")
            if (providerSettings.wigleTokenName.isNotBlank() && providerSettings.wigleToken.isNotBlank()) add("WiGLE Wi-Fi")
        }
        radioStatus = if (active.isEmpty()) "Radio providers not configured" else "Ready: ${active.joinToString()}"
    }

    private fun buildRadioProvider(): RadioEnvironmentProvider? {
        val providers = buildList {
            if (providerSettings.openCellIdApiKey.isNotBlank()) {
                add(OpenCellIdRadioEnvironmentProvider(providerSettings.openCellIdApiKey))
            }
            if (providerSettings.wigleTokenName.isNotBlank() && providerSettings.wigleToken.isNotBlank()) {
                add(WigleRadioEnvironmentProvider(providerSettings.wigleTokenName, providerSettings.wigleToken))
            }
        }
        return if (providers.isEmpty()) null
        else CachingRadioEnvironmentProvider(CompositeRadioEnvironmentProvider(providers))
    }

    private fun previewRadioEnvironment() {
        queryRadioEnvironment(apply = false)
    }

    private fun applyRadioSuggestion() {
        queryRadioEnvironment(apply = true)
    }

    private fun queryRadioEnvironment(apply: Boolean) {
        if (radioQueryInFlight) return
        val provider = buildRadioProvider() ?: return toast("Configure at least one provider first")
        val profile = profiles.maxByOrNull { it.lastSyncAtEpochMs }
            ?: profiles.firstOrNull()
            ?: return toast("Create a profile first")
        if (!profile.latitude.isFinite() || !profile.longitude.isFinite()) return toast("Profile coordinates are invalid")

        radioQueryInFlight = true
        radioStatus = "Querying around ${profile.lastSyncCity.ifBlank { profile.targetPackage }}…"
        Thread {
            try {
                val wifi = provider.nearbyWifi(profile.latitude, profile.longitude, 750)
                val cells = provider.nearbyCells(profile.latitude, profile.longitude, 900)
                if (apply) {
                    val service = GeoShiftApp.service ?: error("LSPosed service is not connected")
                    val nearestWifi = wifi.firstOrNull()
                    val nearestCell = cells.firstOrNull()
                    if (nearestWifi == null && nearestCell == null) error("No nearby radio records were returned")
                    val source = listOfNotNull(nearestWifi?.source, nearestCell?.source)
                        .filter { it.isNotBlank() }
                        .distinct()
                        .joinToString(" + ")
                    val updated = profile.copy(
                        wifiEnabled = nearestWifi != null,
                        wifiSsid = nearestWifi?.ssid.orEmpty(),
                        wifiBssid = nearestWifi?.bssid.orEmpty(),
                        telephonyEnabled = nearestCell != null,
                        mcc = nearestCell?.mcc?.toString()?.padStart(3, '0').orEmpty(),
                        mnc = nearestCell?.mnc?.toString()?.padStart(2, '0').orEmpty(),
                        radioSource = source.ifBlank { "provider suggestion" },
                    )
                    val errors = updated.validate()
                    if (errors.isNotEmpty()) error(errors.first())
                    if (!ProfileStoreV2.save(service, updated)) error("Could not save radio suggestion")
                    runOnUiThread {
                        refreshProfiles(service)
                        editingProfile = editingProfile?.takeIf { it.targetPackage != updated.targetPackage } ?: updated
                        radioStatus = "Applied ${if (nearestWifi != null) "Wi-Fi" else ""}${if (nearestWifi != null && nearestCell != null) " + " else ""}${if (nearestCell != null) "cell identity" else ""} to ${updated.targetPackage}"
                        showNotice("Applied nearby radio identity to ${appLabelFor(updated.targetPackage)}")
                    }
                } else {
                    val wifiPreview = wifi.take(3).joinToString { it.ssid?.ifBlank { it.bssid } ?: it.bssid }
                    val cellPreview = cells.take(3).joinToString { "${it.radio} ${it.mcc}/${it.mnc}/${it.areaCode}/${it.cellId}" }
                    runOnUiThread {
                        radioStatus = buildString {
                            append("${wifi.size} Wi-Fi · ${cells.size} cells")
                            if (wifiPreview.isNotBlank()) append(" · $wifiPreview")
                            if (cellPreview.isNotBlank()) append(" · $cellPreview")
                        }
                    }
                }
            } catch (error: Throwable) {
                runOnUiThread {
                    radioStatus = "Provider query failed: ${error.message ?: error.javaClass.simpleName}"
                    if (apply) showNotice(radioStatus)
                }
            } finally {
                runOnUiThread { radioQueryInFlight = false }
            }
        }.start()
    }

    private fun updateFollowService(requestPermission: Boolean) {
        val shouldRun = profiles.any { it.enabled && it.followVpn }
        RuntimeState.setFollowEnabled(this, shouldRun)
        if (shouldRun) {
            if (requestPermission) requestNotificationPermissionIfUseful()
            VpnFollowService.start(this)
        } else {
            VpnFollowService.stop(this)
        }
    }

    private fun requestNotificationPermissionIfUseful() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    @Suppress("DEPRECATION")
    private fun loadLaunchableApps(): List<AppChoice> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intent, 0)
            .mapNotNull { info ->
                val pkg = info.activityInfo?.packageName ?: return@mapNotNull null
                if (pkg == packageName) return@mapNotNull null
                AppChoice(
                    label = info.loadLabel(packageManager)?.toString()?.ifBlank { pkg } ?: pkg,
                    packageName = pkg,
                )
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    private fun appLabelFor(packageName: String): String =
        launchableApps.firstOrNull { it.packageName == packageName }?.label ?: packageName

    private fun formatLastSync(profile: GeoProfile): String {
        val whenText = if (profile.lastSyncAtEpochMs > 0L) {
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(Date(profile.lastSyncAtEpochMs))
        } else "unknown time"
        val place = listOf(profile.lastSyncCity, profile.lastSyncRegion, profile.countryCode)
            .filter { it.isNotBlank() }
            .joinToString(", ")
        return "Last sync ${profile.lastSyncIp} · $place · $whenText"
    }

    private fun showNotice(message: String) {
        notice = message
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val REQUEST_NOTIFICATIONS = 1003
    }
}
