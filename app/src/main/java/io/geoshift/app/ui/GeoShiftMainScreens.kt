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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.geoshift.app.R
import io.geoshift.app.core.GeoProfile
import io.geoshift.app.core.ProviderSettings

private enum class ProfileSort(val labelRes: Int) {
    Recent(R.string.sort_recent),
    Name(R.string.sort_name),
    Enabled(R.string.sort_enabled),
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

@Composable
private fun destinationLabel(destination: Destination): String = when (destination) {
    Destination.Overview -> stringResource(R.string.nav_overview)
    Destination.Profiles -> stringResource(R.string.nav_profiles)
    Destination.Providers -> stringResource(R.string.nav_providers)
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
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.tagline),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item { StatusHero(state, latest, actions.syncAll) }
            item {
                SectionTitle(
                    title = stringResource(R.string.nav_profiles),
                    action = if (state.profiles.isEmpty()) stringResource(R.string.action_add) else stringResource(R.string.action_view_all),
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
                    Text(stringResource(R.string.vpn_environment), style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (place.isBlank()) stringResource(R.string.ready_first_sync) else place,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                StatusBadge(
                    if (state.vpnActive) stringResource(R.string.vpn_active) else stringResource(R.string.no_vpn),
                    state.vpnActive,
                )
            }

            if (latest?.lastSyncIp?.isNotBlank() == true) {
                Text(
                    stringResource(R.string.exit_format, latest.lastSyncIp, latest.timezoneId),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MiniStatus(stringResource(R.string.status_lsposed), state.serviceConnected)
                MiniStatus(stringResource(R.string.status_profiles), state.profiles.isNotEmpty(), state.profiles.size.toString())
                MiniStatus(stringResource(R.string.status_radio), radioProfiles > 0, radioProfiles.toString())
            }

            FilledTonalButton(
                onClick = onSync,
                enabled = state.serviceConnected && !state.isSyncing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isSyncing) stringResource(R.string.synchronizing) else stringResource(R.string.sync_followed_profiles))
            }

            Row(verticalAlignment = Alignment.Top) {
                Symbol(
                    R.drawable.ms_warning_24,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.split_tunnel_warning),
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
            Text(stringResource(R.string.no_profiles_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.no_profiles_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAdd) { Text(stringResource(R.string.create_profile)) }
                TextButton(onClick = onImport) { Text(stringResource(R.string.import_json)) }
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
                        Text(stringResource(R.string.nav_profiles), style = MaterialTheme.typography.headlineLarge)
                        Text(
                            stringResource(R.string.profiles_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = actions.addProfile) {
                        Symbol(R.drawable.ms_add_24, contentDescription = stringResource(R.string.add_profile_cd))
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
                        placeholder = { Text(stringResource(R.string.search_profiles)) },
                        supportingText = {
                            Text(stringResource(R.string.profile_count_format, visibleProfiles.size, state.profiles.size))
                        },
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
                                label = { Text(stringResource(mode.labelRes)) },
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
                                Text(stringResource(R.string.no_matching_profiles), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(R.string.no_matching_profiles_desc),
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
                        Text(stringResource(R.string.import_profile_json))
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
                if (profile.followVpn) FeatureTag(stringResource(R.string.tag_vpn))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (profile.locationEnabled) FeatureTag(stringResource(R.string.tag_gps))
                if (profile.geocoderEnabled) FeatureTag(stringResource(R.string.tag_address))
                if (profile.timezoneEnabled) FeatureTag(stringResource(R.string.tag_timezone))
                if (profile.localeEnabled) FeatureTag(stringResource(R.string.tag_locale))
                if (profile.wifiEnabled) FeatureTag(stringResource(R.string.tag_wifi))
                if (profile.telephonyEnabled) FeatureTag(stringResource(R.string.tag_cell))
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
                Text(stringResource(R.string.radio_identity_data), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                StatusBadge(
                    if (wifi || cells) stringResource(R.string.configured) else stringResource(R.string.optional),
                    wifi || cells,
                )
            }
            Text(radioStatus, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                FeatureTag(if (wifi) stringResource(R.string.wigle_ready) else stringResource(R.string.wigle_off))
                FeatureTag(if (cells) stringResource(R.string.opencellid_ready) else stringResource(R.string.opencellid_off))
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
                    Text(stringResource(R.string.nav_providers), style = MaterialTheme.typography.headlineLarge)
                    Text(
                        stringResource(R.string.providers_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item {
                SectionCard(title = stringResource(R.string.credentials), iconRes = R.drawable.ms_settings_24) {
                    Text(
                        stringResource(R.string.credentials_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = openCell,
                        onValueChange = { openCell = it.trim() },
                        label = { Text(stringResource(R.string.open_cell_api_key)) },
                        singleLine = true,
                        visualTransformation = if (revealSecrets) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = wigleName,
                        onValueChange = { wigleName = it.trim() },
                        label = { Text(stringResource(R.string.wigle_token_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = wigleToken,
                        onValueChange = { wigleToken = it.trim() },
                        label = { Text(stringResource(R.string.wigle_token)) },
                        singleLine = true,
                        visualTransformation = if (revealSecrets) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = { revealSecrets = !revealSecrets }) {
                        Text(if (revealSecrets) stringResource(R.string.hide_secrets) else stringResource(R.string.show_secrets))
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
                    ) { Text(stringResource(R.string.save_provider_settings)) }
                }
            }
            item {
                SectionCard(title = stringResource(R.string.radio_environment), iconRes = R.drawable.ms_wifi_24) {
                    Text(state.radioStatus, style = MaterialTheme.typography.bodyMedium)
                    FilledTonalButton(
                        onClick = actions.previewRadio,
                        enabled = !state.isRadioBusy && state.profiles.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (state.isRadioBusy) stringResource(R.string.querying) else stringResource(R.string.preview_latest_profile))
                    }
                    Button(
                        onClick = actions.applyRadioSuggestion,
                        enabled = !state.isRadioBusy && state.serviceConnected && state.profiles.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (state.isRadioBusy) stringResource(R.string.querying) else stringResource(R.string.apply_nearest_radio))
                    }
                    Text(
                        stringResource(R.string.radio_apply_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
