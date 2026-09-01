package io.geoshift.app.ui

import io.geoshift.app.core.GeoProfile
import io.geoshift.app.core.ProviderSettings

enum class Destination { Overview, Profiles, Providers }

data class AppChoice(val label: String, val packageName: String)

data class GeoShiftUiState(
    val destination: Destination = Destination.Overview,
    val serviceConnected: Boolean = false,
    val serviceLabel: String = "Connecting to LSPosed…",
    val vpnActive: Boolean = false,
    val profiles: List<GeoProfile> = emptyList(),
    val providerSettings: ProviderSettings.Snapshot = ProviderSettings.Snapshot(),
    val syncStatus: String = "No synchronization yet",
    val radioStatus: String = "Radio providers not configured",
    val isSyncing: Boolean = false,
    val isRadioBusy: Boolean = false,
    val editingProfile: GeoProfile? = null,
    val editingOriginalPackage: String? = null,
)

data class GeoShiftActions(
    val navigate: (Destination) -> Unit,
    val addProfile: () -> Unit,
    val editProfile: (GeoProfile) -> Unit,
    val closeEditor: () -> Unit,
    val saveProfile: (GeoProfile, String?) -> Unit,
    val deleteProfile: (String) -> Unit,
    val toggleProfile: (GeoProfile) -> Unit,
    val syncProfile: (GeoProfile) -> Unit,
    val syncAll: () -> Unit,
    val requestScope: (String) -> Unit,
    val exportProfile: (GeoProfile) -> Unit,
    val importProfile: () -> Unit,
    val saveProviders: (ProviderSettings.Snapshot) -> Unit,
    val previewRadio: () -> Unit,
)
