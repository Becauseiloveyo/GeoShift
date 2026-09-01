package io.geoshift.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.geoshift.app.R
import io.geoshift.app.core.GeoProfile
import io.geoshift.app.core.ProviderSettings

private enum class ProfileSort(val label: String) {
    Recent("Recent"),
    Name("Name"),
    Enabled("Enabled"),
}

@Composable
fun GeoShiftAppScreen(
    state: GeoShiftUiState,
    apps: List<AppChoice>,
    actions: GeoShiftActions,
) {
    val editing = state.editingProfile
    if (editing != null) {
        ProfileEditorScreen(
            profile = editing,
            originalPackage = state.editingOriginalPackage,
            existingPackages = state.profiles.mapTo(mutableSetOf()) { it.targetPackage }.apply {
                state.editingOriginalPackage?.let(::remove)
            },
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

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.notice) {
        val message = state.notice ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        actions.clearNotice()
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 720.dp
        if (wide) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.surface,
                snackbarHost = { SnackbarHost(snackbarHostState) },
            ) { padding ->
                Row(Modifier.fillMaxSize().padding(padding)) {
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        Spacer(Modifier.height(12.dp))
                        Destination.entries.forEach { destination ->
                            NavigationRailItem(
                                selected = state.destination == destination,
                                onClick = { actions.navigate(destination) },
                                icon = { Symbol(destinationIcon(destination)) },
                                label = { Text(destinationLabel(destination)) },
                            )
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        DestinationContent(state, apps, actions, PaddingValues())
                    }
                }
            }
        } else {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.surface,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        Destination.entries.forEach { destination ->
                            NavigationBarItem(
                                selected = state.destination == destination,
                                onClick = { actions.navigate(destination) },
                                icon = { Symbol(destinationIcon(destination)) },
                                label = { Text(destinationLabel(destination)) },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                                ),
                            )
                        }
                    }
                },
            ) { padding -> DestinationContent(state, apps, actions, padding) }
        }
    }
}

@Composable
private fun DestinationContent(
    state: GeoShiftUiState,
    apps: List<AppChoice>,
    actions: GeoShiftActions,
    padding: PaddingValues,
) {
    when (state.destination) {
        Destination.Overview -> OverviewScreen(state, apps, actions, padding)
        Destination.Profiles -> ProfilesScreen(state, apps, actions, padding)
        Destination.Providers -> ProvidersScreen(state, actions, padding)
    }
}

private fun destinationIcon(destination: Destination): Int = when (destination) {
    Destination.Overview -> R.drawable.ms_home_24
    Destination.Profiles -> R.drawable.ms_apps_24
    Destination.Providers -> R.drawable.ms_settings_24
}

private fun destinationLabel(destination: Destination): String = when (destination) {
    Destination.Overview -> "Overview"
    Destination.Profiles -> "Profiles"
    Destination.Providers -> "Providers"
}

@Composable
private fun OverviewScreen(
    state: GeoShiftUiState,
    apps: List<AppChoice>,
    actions: GeoShiftActions,
    padding: PaddingValues,
) {
    val recentProfiles = remember(state.profiles) { state.profiles.sortedByDescending { it.lastSyncAtEpochMs } }
    val latest = recentProfiles.firstOrNull()

    Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.fillMaxHeight().adaptiveContentWidth(),
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
                items(recentProfiles.take(3), key = { it.targetPackage }) { profile ->
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
}

@Composable
private fun StatusHero(state: GeoShiftUiState, latest: GeoProfile?, onSync: () -> Unit) {
    val place = latest?.let {
        listOf(it.lastSyncCity, it.lastSyncRegion, it.countryCode)
            .filter(String::isNotBlank)
            .distinct()
            .joinToString(" · ")
    }.orEmpty()
    val radioProfiles = state.profiles.count { it.wifiEnabled || it.telephonyEnabled }

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
                        Symbol(R.drawable.ms_public_24)
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

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MiniStatus("LSPosed", state.serviceConnected)
                MiniStatus("Profiles", state.profiles.isNotEmpty(), state.profiles.size.toString())
                MiniStatus("Radio", radioProfiles > 0, radioProfiles.toString())
            }

            FilledTonalButton(
                onClick = onSync,
                enabled = state.serviceConnected && !state.isSyncing,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (state.isSyncing) "Synchronizing…" else "Sync followed profiles") }

            Row(verticalAlignment = Alignment.Top) {
                Symbol(
                    R.drawable.ms_warning_24,
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
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Symbol(
                if (active) R.drawable.ms_check_circle_24 else R.drawable.ms_warning_24,
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
            Symbol(R.drawable.ms_apps_24, modifier = Modifier.size(30.dp))
            Text("No app profiles yet", style = MaterialTheme.typography.titleMedium)
            Text(
                "Create one profile per target app. GeoShift can keep location, address, time zone, locale and radio identity coherent.",
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
    var query by rememberSaveable { mutableStateOf("") }
    var sortMode by rememberSaveable { mutableStateOf(ProfileSort.Recent.name) }
    val selectedSort = ProfileSort.entries.firstOrNull { it.name == sortMode } ?: ProfileSort.Recent

    val visibleProfiles = remember(state.profiles, apps, query, selectedSort) {
        val filtered = state.profiles.filter { profile ->
            val label = appLabel(profile.targetPackage, apps)
            query.isBlank() || label.contains(query, true) || profile.targetPackage.contains(query, true) ||
                profile.countryCode.contains(query, true) || profile.timezoneId.contains(query, true) ||
                profile.wifiSsid.contains(query, true) || profile.operatorName.contains(query, true)
        }
        when (selectedSort) {
            ProfileSort.Recent -> filtered.sortedByDescending { it.lastSyncAtEpochMs }
            ProfileSort.Name -> filtered.sortedBy { appLabel(it.targetPackage, apps).lowercase() }
            ProfileSort.Enabled -> filtered.sortedWith(
                compareByDescending<GeoProfile> { it.enabled }.thenBy { appLabel(it.targetPackage, apps).lowercase() }
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.fillMaxHeight().adaptiveContentWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Profiles", style = MaterialTheme.typography.headlineLarge)
                        Text(
                            "Independent app environments sharing one verified VPN exit when Follow VPN is enabled.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = actions.addProfile) {
                        Symbol(R.drawable.ms_add_24, contentDescription = "Add profile")
                    }
                }
            }

            if (state.profiles.isEmpty()) {
                item { EmptyProfilesCard(actions.addProfile, actions.importProfile) }
            } else {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        leadingIcon = { Symbol(R.drawable.ms_search_24) },
                        placeholder = { Text("Search profiles") },
                        supportingText = { Text("${visibleProfiles.size} of ${state.profiles.size} profiles") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ProfileSort.entries.forEach { mode ->
                            FilterChip(
                                selected = selectedSort == mode,
                                onClick = { sortMode = mode.name },
                                label = { Text(mode.label) },
                            )
                        }
                    }
                }

                if (visibleProfiles.isEmpty()) {
                    item {
                        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text("No matching profiles", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Try an app name, package, country, time zone, Wi-Fi name or operator.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    items(visibleProfiles, key = { it.targetPackage }) { profile ->
                        ProfileCard(
                            profile = profile,
                            label = appLabel(profile.targetPackage, apps),
                            onClick = { actions.editProfile(profile) },
                            onToggle = { actions.toggleProfile(profile.copy(enabled = it)) },
                        )
                    }
                }

                item {
                    OutlinedButton(onClick = actions.importProfile, modifier = Modifier.fillMaxWidth()) {
                        Text("Import profile JSON")
                    }
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
                AppIcon(profile.targetPackage, label)
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
                Symbol(R.drawable.ms_location_on_24, modifier = Modifier.size(18.dp))
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
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (profile.locationEnabled) FeatureTag("GPS")
                if (profile.geocoderEnabled) FeatureTag("Address")
                if (profile.timezoneEnabled) FeatureTag("Time zone")
                if (profile.localeEnabled) FeatureTag("Locale")
                if (profile.wifiEnabled) FeatureTag("Wi-Fi")
                if (profile.telephonyEnabled) FeatureTag("Cell")
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
                Symbol(R.drawable.ms_wifi_24)
                Spacer(Modifier.width(10.dp))
                Text("Radio identity data", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                StatusBadge(if (wifi || cells) "Configured" else "Optional", wifi || cells)
            }
            Text(radioStatus, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
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

    Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.fillMaxHeight().adaptiveContentWidth().imePadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Column {
                    Text("Providers", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        "Optional public datasets can populate a profile's nearby Wi-Fi and cellular identity.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                SectionCard(title = "Credentials", iconRes = R.drawable.ms_settings_24) {
                    Text(
                        "Secrets stay in GeoShift's private local preferences and are never included with exported profiles.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = openCell,
                        onValueChange = { openCell = it.trim() },
                        label = { Text("OpenCellID API key") },
                        singleLine = true,
                        visualTransformation = if (revealSecrets) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = wigleName,
                        onValueChange = { wigleName = it.trim() },
                        label = { Text("WiGLE token name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = wigleToken,
                        onValueChange = { wigleToken = it.trim() },
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
                    ) { Text("Save provider settings") }
                }
            }
            item {
                SectionCard(title = "Radio environment", iconRes = R.drawable.ms_wifi_24) {
                    Text(state.radioStatus, style = MaterialTheme.typography.bodyMedium)
                    FilledTonalButton(
                        onClick = actions.previewRadio,
                        enabled = !state.isRadioBusy && state.profiles.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (state.isRadioBusy) "Querying…" else "Preview around latest profile") }
                    Button(
                        onClick = actions.applyRadioSuggestion,
                        enabled = !state.isRadioBusy && state.serviceConnected && state.profiles.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (state.isRadioBusy) "Querying…" else "Apply nearest radio identity") }
                    Text(
                        "Apply uses the nearest returned Wi-Fi and cell record for the most recently synchronized profile. Review the profile before relying on it for testing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
