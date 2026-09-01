package io.geoshift.app.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.geoshift.app.R
import io.geoshift.app.core.GeoProfile
import io.geoshift.app.core.ProfileDiagnostics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileEditorScreen(
    profile: GeoProfile,
    originalPackage: String?,
    existingPackages: Set<String>,
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
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }

    val coreErrors = draft.validate()
    val packageConflict = draft.targetPackage.isNotBlank() && draft.targetPackage in existingPackages
    val validationErrors = if (packageConflict) coreErrors + "A profile for this app already exists" else coreErrors
    val diagnosticIssues = remember(draft) { ProfileDiagnostics.evaluate(draft) }
    val hasChanges = draft != profile ||
        latitudeText != profile.latitude.toString() || longitudeText != profile.longitude.toString()

    val packageError = when {
        draft.targetPackage.isBlank() -> "Choose an app or enter its package name"
        packageConflict -> "A profile for this app already exists"
        else -> null
    }
    val timezoneError = validationErrors.firstOrNull { it.startsWith("Unknown time zone") }
    val localeError = validationErrors.firstOrNull { it.startsWith("Invalid locale tag") }
    val countryError = validationErrors.firstOrNull { it.startsWith("Country code") }
    val latitudeError = validationErrors.firstOrNull { it.startsWith("Latitude") }
    val longitudeError = validationErrors.firstOrNull { it.startsWith("Longitude") }

    fun requestClose() {
        if (hasChanges) confirmDiscard = true else onBack()
    }

    PredictiveBackHandler {
        try {
            it.collect { }
            requestClose()
        } catch (_: CancellationException) {
            // A cancelled predictive-back gesture must keep the editor open.
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (originalPackage == null) "New profile" else appLabel(draft.targetPackage, apps)) },
                navigationIcon = {
                    IconButton(onClick = ::requestClose) {
                        Symbol(R.drawable.ms_arrow_back_24, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp, modifier = Modifier.imePadding()) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier.adaptiveContentWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        when {
                            !serviceConnected -> Text(
                                "Connect the framework service before saving.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            validationErrors.isNotEmpty() -> Text(
                                "Fix ${validationErrors.size} ${if (validationErrors.size == 1) "issue" else "issues"} before saving.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Button(
                            onClick = { onSave(draft, originalPackage) },
                            enabled = serviceConnected && validationErrors.isEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Save profile")
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxHeight().adaptiveContentWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    SectionCard(title = "Application", iconRes = R.drawable.ms_apps_24) {
                        if (draft.targetPackage.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AppIcon(draft.targetPackage, appLabel(draft.targetPackage, apps))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        appLabel(draft.targetPackage, apps),
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        draft.targetPackage,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = draft.targetPackage,
                            onValueChange = { draft = draft.copy(targetPackage = it.trim()) },
                            label = { Text("Package name") },
                            placeholder = { Text("com.example.app") },
                            singleLine = true,
                            isError = packageError != null,
                            supportingText = packageError?.let { message -> { Text(message) } },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedButton(onClick = { showAppPicker = true }, modifier = Modifier.fillMaxWidth()) {
                            Symbol(R.drawable.ms_search_24)
                            Spacer(Modifier.width(8.dp))
                            Text("Choose installed app")
                        }
                        SwitchSetting(
                            title = "Profile enabled",
                            supporting = "Temporarily disable this profile without deleting its settings.",
                            checked = draft.enabled,
                            onCheckedChange = { draft = draft.copy(enabled = it) },
                        )
                    }
                }
                item {
                    SectionCard(title = "Follow VPN", iconRes = R.drawable.ms_public_24) {
                        SwitchSetting(
                            title = "Synchronize with current exit IP",
                            supporting = "Keep region fields aligned with the current network exit.",
                            checked = draft.followVpn,
                            onCheckedChange = { draft = draft.copy(followVpn = it) },
                        )
                        FilledTonalButton(
                            onClick = { onSync(draft) },
                            enabled = serviceConnected && !isSyncing && draft.targetPackage.isNotBlank() && !packageConflict,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (isSyncing) "Synchronizing…" else "Sync this profile now")
                        }
                    }
                }
                item {
                    SectionCard(title = "Region identity", iconRes = R.drawable.ms_location_on_24) {
                        OutlinedTextField(
                            value = draft.timezoneId,
                            onValueChange = { draft = draft.copy(timezoneId = it.trim()) },
                            label = { Text("Time zone") },
                            placeholder = { Text("America/Los_Angeles") },
                            singleLine = true,
                            isError = timezoneError != null,
                            supportingText = timezoneError?.let { message -> { Text(message) } },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = draft.localeTag,
                            onValueChange = { draft = draft.copy(localeTag = it.trim()) },
                            label = { Text("Locale") },
                            placeholder = { Text("en-US") },
                            singleLine = true,
                            isError = localeError != null,
                            supportingText = localeError?.let { message -> { Text(message) } },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = draft.countryCode,
                            onValueChange = { draft = draft.copy(countryCode = it.trim().uppercase()) },
                            label = { Text("Country") },
                            placeholder = { Text("US") },
                            singleLine = true,
                            isError = countryError != null,
                            supportingText = countryError?.let { message -> { Text(message) } },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = latitudeText,
                                onValueChange = {
                                    latitudeText = it
                                    draft = draft.copy(latitude = it.toDoubleOrNull() ?: Double.NaN)
                                },
                                label = { Text("Latitude") },
                                singleLine = true,
                                isError = latitudeError != null,
                                supportingText = latitudeError?.let { { Text("−90…90") } },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
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
                                isError = longitudeError != null,
                                supportingText = longitudeError?.let { { Text("−180…180") } },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                item {
                    SectionCard(title = "Overrides", iconRes = R.drawable.ms_settings_24) {
                        SwitchSetting("Location", "Use the profile coordinates where supported.", draft.locationEnabled) {
                            draft = draft.copy(locationEnabled = it)
                        }
                        HorizontalDivider()
                        SwitchSetting("Time zone", "Use the profile time zone where supported.", draft.timezoneEnabled) {
                            draft = draft.copy(timezoneEnabled = it)
                        }
                        HorizontalDivider()
                        SwitchSetting("Locale", "Use the profile locale where supported.", draft.localeEnabled) {
                            draft = draft.copy(localeEnabled = it)
                        }
                    }
                }
                item {
                    SectionCard(
                        title = "Consistency",
                        iconRes = if (diagnosticIssues.isEmpty()) R.drawable.ms_check_circle_24 else R.drawable.ms_warning_24,
                    ) {
                        if (diagnosticIssues.isEmpty()) {
                            Text("No obvious profile conflicts detected.", style = MaterialTheme.typography.bodyMedium)
                        } else {
                            diagnosticIssues.forEach { issue ->
                                Text("${issue.severity}: ${issue.message}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                item {
                    SectionCard(title = "Tools") {
                        OutlinedButton(
                            onClick = { onRequestScope(draft.targetPackage) },
                            enabled = serviceConnected && draft.targetPackage.isNotBlank() && !packageConflict,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Request app scope") }
                        OutlinedButton(
                            onClick = { onExport(draft) },
                            enabled = validationErrors.isEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Export profile JSON") }
                        if (originalPackage != null) {
                            TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("Delete profile")
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
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
            text = { Text("This removes the saved profile for $originalPackage.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete(originalPackage) }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("This profile has unsaved changes.") },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; onBack() }) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") } },
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
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.adaptiveContentWidth().padding(horizontal = 20.dp).imePadding()) {
                Text("Choose app", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Symbol(R.drawable.ms_search_24) },
                    placeholder = { Text("Search apps or package names") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                if (filtered.isEmpty()) {
                    Text(
                        "No matching launchable apps.",
                        modifier = Modifier.padding(vertical = 24.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 520.dp)) {
                        items(filtered, key = { it.packageName }) { app ->
                            ListItem(
                                headlineContent = { Text(app.label) },
                                supportingContent = {
                                    Text(app.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                leadingContent = { AppIcon(app.packageName, app.label) },
                                modifier = Modifier.clickable { onSelect(app) },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
