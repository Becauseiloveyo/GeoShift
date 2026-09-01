package io.geoshift.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.geoshift.app.core.GeoProfile
import io.geoshift.app.core.ProfileDiagnostics
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

@Composable
fun GeoShiftAppScreen(
    state: GeoShiftUiState,
    apps: List<AppChoice>,
    actions: GeoShiftActions,
) {
    val editing = state.editingProfile
    if (editing != null) {
        ProfileEditor(
            profile = editing,
            originalPackage = state.editingOriginalPackage,
            apps = apps,
            serviceConnected = state.serviceConnected,
            isSyncing = state.isSyncing,
            onBack = actions.closeEditor,
            onSave = actions.saveProfile,
            onDelete = actions.deleteProfile,
            onSync = actions.syncProfile,
            onRequestScope = actions.requestScope,
            onExport = actions.exportProfile,
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Destination.entries.forEach { destination ->
                    NavigationBarItem(
                        selected = state.destination == destination,
                        onClick = { actions.navigate(destination) },
                        icon = {
                            Icon(
                                when (destination) {
                                    Destination.Overview -> Icons.Rounded.Home
                                    Destination.Profiles -> Icons.Rounded.Apps
                                    Destination.Providers -> Icons.Rounded.Settings
                                },
                                contentDescription = null,
                            )
                        },
                        label = {
                            Text(
                                when (destination) {
                                    Destination.Overview -> "Overview"
                                    Destination.Profiles -> "Profiles"
                                    Destination.Providers -> "Providers"
                                }
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        when (state.destination) {
            Destination.Overview -> OverviewScreen(state, apps, actions, padding)
            Destination.Profiles -> ProfilesScreen(state, apps, actions, padding)
            Destination.Providers -> ProvidersScreen(state, actions, padding)
        }
    }
}

@Composable
private fun OverviewScreen(
    state: GeoShiftUiState,
    apps: List<AppChoice>,
    actions: GeoShiftActions,
    padding: PaddingValues,
) {
    val latest = state.profiles.maxByOrNull { it.lastSyncAtEpochMs }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                Text("GeoShift", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "One coherent geographic profile for each app.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { StatusHero(state, latest, actions.syncAll) }
        item {
            SectionTitle(
                title = "Profiles",
                action = if (state.profiles.isEmpty()) "Add" else "View all",
                onAction = {
                    if (state.profiles.isEmpty()) actions.addProfile()
                    else actions.navigate(Destination.Profiles)
                },
            )
        }
        if (state.profiles.isEmpty()) {
            item { EmptyProfilesCard(actions.addProfile, actions.importProfile) }
        } else {
            items(state.profiles.take(3), key = { it.targetPackage }) { profile ->
                ProfileCard(
                    profile = profile,
                    label = appLabel(profile.targetPackage, apps),
                    onClick = { actions.editProfile(profile) },
                    onToggle = { actions.toggleProfile(profile.copy(enabled = it)) },
                )
            }
        }
        item {
            ProviderSummaryCard(
                settings = state.providerSettings,
                radioStatus = state.radioStatus,
                onOpen = { actions.navigate(Destination.Providers) },
            )
        }
    }
}

@Composable
private fun StatusHero(state: GeoShiftUiState, latest: GeoProfile?, onSync: () -> Unit) {
    val place = latest?.let {
        listOf(it.lastSyncCity, it.lastSyncRegion, it.countryCode)
            .filter(String::isNotBlank)
            .distinct()
            .joinToString(" · ")
    }.orEmpty()

    Card(
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Public, contentDescription = null)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("VPN-aware environment", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (place.isBlank()) "Ready for the first exit-IP sync" else place,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                StatusBadge(if (state.vpnActive) "VPN active" else "No VPN", state.vpnActive)
            }

            if (latest?.lastSyncIp?.isNotBlank() == true) {
                Text(
                    "Exit ${latest.lastSyncIp} · ${latest.timezoneId}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniStatus("LSPosed", state.serviceConnected)
                MiniStatus("Profiles", state.profiles.isNotEmpty(), state.profiles.size.toString())
            }

            FilledTonalButton(
                onClick = onSync,
                enabled = state.serviceConnected && !state.isSyncing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (state.isSyncing) "Synchronizing…" else "Sync followed profiles")
            }

            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Rounded.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Exit IP is verified from GeoShift itself. Split-tunnel routing for each target app is not independently verified yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text(
                state.syncStatus,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun MiniStatus(label: String, active: Boolean, value: String? = null) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (active) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(if (value == null) label else "$label $value", style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun EmptyProfilesCard(onAdd: () -> Unit, onImport: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.Apps, contentDescription = null, modifier = Modifier.size(30.dp))
            Text("No app profiles yet", style = MaterialTheme.typography.titleMedium)
            Text(
                "Create one profile per target app. GeoShift can then keep location, time zone and locale coherent.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAdd) { Text("Create profile") }
                TextButton(onClick = onImport) { Text("Import JSON") }
            }
        }
    }
}

@Composable
private fun ProfilesScreen(
    state: GeoShiftUiState,
    apps: List<AppChoice>,
    actions: GeoShiftActions,
    padding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Profiles", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        "Independent settings, one shared VPN exit when Follow VPN is enabled.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = actions.addProfile) {
                    Icon(Icons.Rounded.Add, contentDescription = "Add profile")
                }
            }
        }
        if (state.profiles.isEmpty()) {
            item { EmptyProfilesCard(actions.addProfile, actions.importProfile) }
        } else {
            items(state.profiles, key = { it.targetPackage }) { profile ->
                ProfileCard(
                    profile = profile,
                    label = appLabel(profile.targetPackage, apps),
                    onClick = { actions.editProfile(profile) },
                    onToggle = { actions.toggleProfile(profile.copy(enabled = it)) },
                )
            }
            item {
                OutlinedButton(onClick = actions.importProfile, modifier = Modifier.fillMaxWidth()) {
                    Text("Import profile JSON")
                }
            }
        }
    }
}

@Composable
private fun ProfileCard(
    profile: GeoProfile,
    label: String,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppAvatar(label)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        profile.targetPackage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Switch(checked = profile.enabled, onCheckedChange = onToggle)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
                Text(
                    listOf(profile.countryCode, profile.timezoneId).filter(String::isNotBlank).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (profile.followVpn) FeatureTag("VPN")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (profile.locationEnabled) FeatureTag("GPS")
                if (profile.timezoneEnabled) FeatureTag("Time zone")
                if (profile.localeEnabled) FeatureTag("Locale")
            }
        }
    }
}

@Composable
private fun ProviderSummaryCard(
    settings: ProviderSettings.Snapshot,
    radioStatus: String,
    onOpen: () -> Unit,
) {
    val wifi = settings.wigleTokenName.isNotBlank() && settings.wigleToken.isNotBlank()
    val cells = settings.openCellIdApiKey.isNotBlank()
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Wifi, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Text("Nearby radio data", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                StatusBadge(if (wifi || cells) "Configured" else "Optional", wifi || cells)
            }
            Text(radioStatus, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FeatureTag(if (wifi) "WiGLE ready" else "WiGLE off")
                FeatureTag(if (cells) "OpenCellID ready" else "OpenCellID off")
            }
        }
    }
}

@Composable
private fun ProvidersScreen(
    state: GeoShiftUiState,
    actions: GeoShiftActions,
    padding: PaddingValues,
) {
    var openCell by remember(state.providerSettings) { mutableStateOf(state.providerSettings.openCellIdApiKey) }
    var wigleName by remember(state.providerSettings) { mutableStateOf(state.providerSettings.wigleTokenName) }
    var wigleToken by remember(state.providerSettings) { mutableStateOf(state.providerSettings.wigleToken) }
    var revealSecrets by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                Text("Providers", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "Optional public datasets for nearby Wi-Fi and cellular previews.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            SectionCard(title = "Credentials", icon = { Icon(Icons.Rounded.VpnKey, contentDescription = null) }) {
                Text(
                    "Secrets stay in GeoShift's private local preferences. They are never written to LSPosed Remote Preferences or exported with profiles.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = openCell,
                    onValueChange = { openCell = it },
                    label = { Text("OpenCellID API key") },
                    singleLine = true,
                    visualTransformation = if (revealSecrets) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = wigleName,
                    onValueChange = { wigleName = it },
                    label = { Text("WiGLE token name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = wigleToken,
                    onValueChange = { wigleToken = it },
                    label = { Text("WiGLE token") },
                    singleLine = true,
                    visualTransformation = if (revealSecrets) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { revealSecrets = !revealSecrets }) {
                    Text(if (revealSecrets) "Hide secrets" else "Show secrets")
                }
                Button(
                    onClick = {
                        actions.saveProviders(
                            ProviderSettings.Snapshot(
                                openCellIdApiKey = openCell,
                                wigleTokenName = wigleName,
                                wigleToken = wigleToken,
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Save, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save provider settings")
                }
            }
        }
        item {
            SectionCard(title = "Radio preview", icon = { Icon(Icons.Rounded.Wifi, contentDescription = null) }) {
                Text(state.radioStatus, style = MaterialTheme.typography.bodyMedium)
                FilledTonalButton(
                    onClick = actions.previewRadio,
                    enabled = !state.isRadioBusy && state.profiles.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.isRadioBusy) "Querying…" else "Preview around latest profile")
                }
                Text(
                    "This currently validates and previews data only; Wi-Fi and CellInfo hooks are a later stage.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileEditor(
    profile: GeoProfile,
    originalPackage: String?,
    apps: List<AppChoice>,
    serviceConnected: Boolean,
    isSyncing: Boolean,
    onBack: () -> Unit,
    onSave: (GeoProfile, String?) -> Unit,
    onDelete: (String) -> Unit,
    onSync: (GeoProfile) -> Unit,
    onRequestScope: (String) -> Unit,
    onExport: (GeoProfile) -> Unit,
) {
    var draft by remember(profile) { mutableStateOf(profile) }
    var latitudeText by remember(profile) { mutableStateOf(profile.latitude.toString()) }
    var longitudeText by remember(profile) { mutableStateOf(profile.longitude.toString()) }
    var showAppPicker by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val issues = remember(draft) { ProfileDiagnostics.evaluate(draft) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (originalPackage == null) "New profile" else appLabel(draft.targetPackage, apps)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = { onSave(draft, originalPackage) },
                    enabled = serviceConnected && draft.validate().isEmpty(),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Icon(Icons.Rounded.Save, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save profile")
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SectionCard(title = "Application", icon = { Icon(Icons.Rounded.Apps, contentDescription = null) }) {
                    OutlinedTextField(
                        value = draft.targetPackage,
                        onValueChange = { draft = draft.copy(targetPackage = it.trim()) },
                        label = { Text("Package name") },
                        placeholder = { Text("com.example.app") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(onClick = { showAppPicker = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.Search, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Choose installed app")
                    }
                    SwitchSetting(
                        title = "Profile enabled",
                        supporting = "Hooks stay installed but return original values while disabled.",
                        checked = draft.enabled,
                        onCheckedChange = { draft = draft.copy(enabled = it) },
                    )
                }
            }
            item {
                SectionCard(title = "Follow VPN", icon = { Icon(Icons.Rounded.Public, contentDescription = null) }) {
                    SwitchSetting(
                        title = "Synchronize with current exit IP",
                        supporting = "Updates country, time zone, locale and approximate coordinates when the VPN network changes.",
                        checked = draft.followVpn,
                        onCheckedChange = { draft = draft.copy(followVpn = it) },
                    )
                    FilledTonalButton(
                        onClick = { onSync(draft) },
                        enabled = serviceConnected && !isSyncing && draft.targetPackage.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (isSyncing) "Synchronizing…" else "Sync this profile now")
                    }
                }
            }
            item {
                SectionCard(title = "Region identity", icon = { Icon(Icons.Rounded.LocationOn, contentDescription = null) }) {
                    OutlinedTextField(
                        value = draft.timezoneId,
                        onValueChange = { draft = draft.copy(timezoneId = it) },
                        label = { Text("Time zone") },
                        placeholder = { Text("America/Los_Angeles") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = draft.localeTag,
                            onValueChange = { draft = draft.copy(localeTag = it) },
                            label = { Text("Locale") },
                            placeholder = { Text("en-US") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = draft.countryCode,
                            onValueChange = { draft = draft.copy(countryCode = it.uppercase()) },
                            label = { Text("Country") },
                            placeholder = { Text("US") },
                            singleLine = true,
                            modifier = Modifier.weight(0.7f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = latitudeText,
                            onValueChange = {
                                latitudeText = it
                                draft = draft.copy(latitude = it.toDoubleOrNull() ?: Double.NaN)
                            },
                            label = { Text("Latitude") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = longitudeText,
                            onValueChange = {
                                longitudeText = it
                                draft = draft.copy(longitude = it.toDoubleOrNull() ?: Double.NaN)
                            },
                            label = { Text("Longitude") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            item {
                SectionCard(title = "Overrides", icon = { Icon(Icons.Rounded.Settings, contentDescription = null) }) {
                    SwitchSetting("Location", "Override latitude and longitude returned by Android Location objects.", draft.locationEnabled) {
                        draft = draft.copy(locationEnabled = it)
                    }
                    HorizontalDivider()
                    SwitchSetting("Time zone", "Covers TimeZone.getDefault() and ZoneId.systemDefault().", draft.timezoneEnabled) {
                        draft = draft.copy(timezoneEnabled = it)
                    }
                    HorizontalDivider()
                    SwitchSetting("Locale", "Covers Locale defaults and LocaleList defaults.", draft.localeEnabled) {
                        draft = draft.copy(localeEnabled = it)
                    }
                }
            }
            item {
                SectionCard(title = "Consistency", icon = {
                    Icon(if (issues.isEmpty()) Icons.Rounded.CheckCircle else Icons.Rounded.Warning, contentDescription = null)
                }) {
                    if (issues.isEmpty()) {
                        Text("No obvious profile conflicts detected.", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        issues.forEach { issue ->
                            Text("${issue.severity}: ${issue.message}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            item {
                SectionCard(title = "Tools") {
                    OutlinedButton(
                        onClick = { onRequestScope(draft.targetPackage) },
                        enabled = serviceConnected && draft.targetPackage.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Request LSPosed scope") }
                    OutlinedButton(
                        onClick = { onExport(draft) },
                        enabled = draft.validate().isEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Export profile JSON") }
                    if (originalPackage != null) {
                        TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.Delete, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Delete profile")
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }

    if (showAppPicker) {
        AppPickerSheet(
            apps = apps,
            onDismiss = { showAppPicker = false },
            onSelect = {
                draft = draft.copy(targetPackage = it.packageName)
                showAppPicker = false
            },
        )
    }

    if (confirmDelete && originalPackage != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete profile?") },
            text = { Text("This removes the GeoShift configuration for $originalPackage.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete(originalPackage) }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppPickerSheet(
    apps: List<AppChoice>,
    onDismiss: () -> Unit,
    onSelect: (AppChoice) -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(apps, query) {
        if (query.isBlank()) apps
        else apps.filter { it.label.contains(query, true) || it.packageName.contains(query, true) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text("Choose app", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                placeholder = { Text("Search apps or package names") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            LazyColumn(Modifier.heightIn(max = 520.dp)) {
                items(filtered, key = { it.packageName }) { app ->
                    ListItem(
                        headlineContent = { Text(app.label) },
                        supportingContent = { Text(app.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { AppAvatar(app.label) },
                        modifier = Modifier.clickable { onSelect(app) },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    icon()
                    Spacer(Modifier.width(9.dp))
                }
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

@Composable
private fun SwitchSetting(
    title: String,
    supporting: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(supporting, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SectionTitle(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun StatusBadge(text: String, active: Boolean) {
    Surface(
        shape = CircleShape,
        color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
    }
}

@Composable
private fun FeatureTag(text: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
    }
}

@Composable
private fun AppAvatar(label: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Text(label.trim().firstOrNull()?.uppercase() ?: "?", style = MaterialTheme.typography.titleMedium)
        }
    }
}

private fun appLabel(packageName: String, apps: List<AppChoice>): String =
    apps.firstOrNull { it.packageName == packageName }?.label
        ?: packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }.ifBlank { "New app" }
