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
import androidx.compose.ui.res.stringResource
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
        draft.targetPackage.isBlank() -> stringResource(R.string.choose_app_error)
        packageConflict -> stringResource(R.string.profile_exists_error)
        else -> null
    }
    val timezoneError = validationErrors.any { it.startsWith("Unknown time zone") }
    val localeError = validationErrors.any { it.startsWith("Invalid locale tag") }
    val countryError = validationErrors.any { it.startsWith("Country code") }
    val latitudeError = validationErrors.any { it.startsWith("Latitude") }
    val longitudeError = validationErrors.any { it.startsWith("Longitude") }
    val bssidError = validationErrors.any { it.startsWith("Wi-Fi BSSID") }
    val mccError = validationErrors.any { it.startsWith("MCC must") }
    val mncError = validationErrors.any { it.startsWith("MNC must") }
    val operatorPairError = validationErrors.any { it.startsWith("MCC and MNC") }

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
                title = { Text(if (originalPackage == null) stringResource(R.string.new_profile) else appLabel(draft.targetPackage, apps)) },
                navigationIcon = {
                    IconButton(onClick = ::requestClose) {
                        Symbol(R.drawable.ms_arrow_back_24, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp, modifier = Modifier.imePadding()) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(
                        modifier = Modifier.adaptiveContentWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        when {
                            !serviceConnected -> Text(
                                stringResource(R.string.connect_framework_before_save),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            validationErrors.isNotEmpty() -> Text(
                                stringResource(R.string.fix_issues_before_save, validationErrors.size),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Button(
                            onClick = { onSave(draft, originalPackage) },
                            enabled = serviceConnected && validationErrors.isEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.save_profile)) }
                    }
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.fillMaxHeight().adaptiveContentWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    SectionCard(title = stringResource(R.string.section_application), iconRes = R.drawable.ms_apps_24) {
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
                            label = { Text(stringResource(R.string.package_name)) },
                            placeholder = { Text("com.example.app") },
                            singleLine = true,
                            isError = packageError != null,
                            supportingText = packageError?.let { message -> { Text(message) } },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedButton(onClick = { showAppPicker = true }, modifier = Modifier.fillMaxWidth()) {
                            Symbol(R.drawable.ms_search_24)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.choose_installed_app))
                        }
                        SwitchSetting(
                            title = stringResource(R.string.profile_enabled),
                            supporting = stringResource(R.string.profile_enabled_desc),
                            checked = draft.enabled,
                            onCheckedChange = { draft = draft.copy(enabled = it) },
                        )
                    }
                }
                item {
                    SectionCard(title = stringResource(R.string.section_follow_vpn), iconRes = R.drawable.ms_public_24) {
                        SwitchSetting(
                            title = stringResource(R.string.sync_current_exit),
                            supporting = stringResource(R.string.sync_current_exit_desc),
                            checked = draft.followVpn,
                            onCheckedChange = { draft = draft.copy(followVpn = it) },
                        )
                        FilledTonalButton(
                            onClick = { onSync(draft) },
                            enabled = serviceConnected && !isSyncing && draft.targetPackage.isNotBlank() && !packageConflict,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (isSyncing) stringResource(R.string.synchronizing) else stringResource(R.string.sync_profile_now))
                        }
                    }
                }
                item {
                    SectionCard(title = stringResource(R.string.section_region_identity), iconRes = R.drawable.ms_location_on_24) {
                        OutlinedTextField(
                            value = draft.timezoneId,
                            onValueChange = { draft = draft.copy(timezoneId = it.trim()) },
                            label = { Text(stringResource(R.string.field_timezone)) },
                            placeholder = { Text("America/Los_Angeles") },
                            singleLine = true,
                            isError = timezoneError,
                            supportingText = if (timezoneError) ({ Text(stringResource(R.string.error_invalid_timezone)) }) else null,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = draft.localeTag,
                            onValueChange = { draft = draft.copy(localeTag = it.trim()) },
                            label = { Text(stringResource(R.string.field_locale)) },
                            placeholder = { Text("en-US") },
                            singleLine = true,
                            isError = localeError,
                            supportingText = if (localeError) ({ Text(stringResource(R.string.error_invalid_locale)) }) else null,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = draft.countryCode,
                            onValueChange = { draft = draft.copy(countryCode = it.trim().uppercase()) },
                            label = { Text(stringResource(R.string.field_country)) },
                            placeholder = { Text("US") },
                            singleLine = true,
                            isError = countryError,
                            supportingText = if (countryError) ({ Text(stringResource(R.string.error_country_code)) }) else null,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = latitudeText,
                                onValueChange = {
                                    latitudeText = it
                                    draft = draft.copy(latitude = it.toDoubleOrNull() ?: Double.NaN)
                                },
                                label = { Text(stringResource(R.string.field_latitude)) },
                                singleLine = true,
                                isError = latitudeError,
                                supportingText = if (latitudeError) ({ Text("−90…90") }) else null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                                modifier = Modifier.weight(1f),
                            )
                            OutlinedTextField(
                                value = longitudeText,
                                onValueChange = {
                                    longitudeText = it
                                    draft = draft.copy(longitude = it.toDoubleOrNull() ?: Double.NaN)
                                },
                                label = { Text(stringResource(R.string.field_longitude)) },
                                singleLine = true,
                                isError = longitudeError,
                                supportingText = if (longitudeError) ({ Text("−180…180") }) else null,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                item {
                    SectionCard(title = stringResource(R.string.section_radio_identity), iconRes = R.drawable.ms_wifi_24) {
                        SwitchSetting(
                            stringResource(R.string.wifi_identity),
                            stringResource(R.string.wifi_identity_desc),
                            draft.wifiEnabled,
                        ) { draft = draft.copy(wifiEnabled = it) }
                        if (draft.wifiEnabled) {
                            OutlinedTextField(
                                value = draft.wifiSsid,
                                onValueChange = { draft = draft.copy(wifiSsid = it) },
                                label = { Text("SSID") },
                                placeholder = { Text(stringResource(R.string.nearby_network)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = draft.wifiBssid,
                                onValueChange = { draft = draft.copy(wifiBssid = it.trim().lowercase()) },
                                label = { Text("BSSID") },
                                placeholder = { Text("aa:bb:cc:dd:ee:ff") },
                                singleLine = true,
                                isError = bssidError,
                                supportingText = if (bssidError) ({ Text(stringResource(R.string.error_invalid_bssid)) }) else null,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        HorizontalDivider()
                        SwitchSetting(
                            stringResource(R.string.telephony_identity),
                            stringResource(R.string.telephony_identity_desc),
                            draft.telephonyEnabled,
                        ) { draft = draft.copy(telephonyEnabled = it) }
                        if (draft.telephonyEnabled) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = draft.mcc,
                                    onValueChange = { draft = draft.copy(mcc = it.filter(Char::isDigit).take(3)) },
                                    label = { Text("MCC") },
                                    placeholder = { Text("310") },
                                    singleLine = true,
                                    isError = mccError || operatorPairError,
                                    supportingText = if (mccError) ({ Text(stringResource(R.string.error_invalid_mcc)) }) else null,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                )
                                OutlinedTextField(
                                    value = draft.mnc,
                                    onValueChange = { draft = draft.copy(mnc = it.filter(Char::isDigit).take(3)) },
                                    label = { Text("MNC") },
                                    placeholder = { Text("260") },
                                    singleLine = true,
                                    isError = mncError || operatorPairError,
                                    supportingText = if (mncError) ({ Text(stringResource(R.string.error_invalid_mnc)) }) else null,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (operatorPairError) {
                                Text(
                                    stringResource(R.string.error_mcc_mnc_pair),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                            OutlinedTextField(
                                value = draft.operatorName,
                                onValueChange = { draft = draft.copy(operatorName = it) },
                                label = { Text(stringResource(R.string.operator_name_optional)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (draft.radioSource.isNotBlank()) {
                            Text(
                                stringResource(R.string.source_format, draft.radioSource),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item {
                    SectionCard(title = stringResource(R.string.section_overrides), iconRes = R.drawable.ms_settings_24) {
                        SwitchSetting(
                            stringResource(R.string.override_location),
                            stringResource(R.string.override_location_desc),
                            draft.locationEnabled,
                        ) { draft = draft.copy(locationEnabled = it) }
                        HorizontalDivider()
                        SwitchSetting(
                            stringResource(R.string.override_geocoder),
                            stringResource(R.string.override_geocoder_desc),
                            draft.geocoderEnabled,
                        ) { draft = draft.copy(geocoderEnabled = it) }
                        HorizontalDivider()
                        SwitchSetting(
                            stringResource(R.string.field_timezone),
                            stringResource(R.string.override_timezone_desc),
                            draft.timezoneEnabled,
                        ) { draft = draft.copy(timezoneEnabled = it) }
                        HorizontalDivider()
                        SwitchSetting(
                            stringResource(R.string.field_locale),
                            stringResource(R.string.override_locale_desc),
                            draft.localeEnabled,
                        ) { draft = draft.copy(localeEnabled = it) }
                    }
                }
                item {
                    SectionCard(
                        title = stringResource(R.string.section_consistency),
                        iconRes = if (diagnosticIssues.isEmpty()) R.drawable.ms_check_circle_24 else R.drawable.ms_warning_24,
                    ) {
                        if (diagnosticIssues.isEmpty()) {
                            Text(stringResource(R.string.no_profile_conflicts), style = MaterialTheme.typography.bodyMedium)
                        } else {
                            diagnosticIssues.forEach { issue ->
                                val severity = when (issue.severity) {
                                    ProfileDiagnostics.Severity.ERROR -> stringResource(R.string.severity_error)
                                    ProfileDiagnostics.Severity.WARNING -> stringResource(R.string.severity_warning)
                                }
                                Text("$severity: ${localizedDiagnostic(issue.message)}", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                item {
                    SectionCard(title = stringResource(R.string.section_tools)) {
                        OutlinedButton(
                            onClick = { onRequestScope(draft.targetPackage) },
                            enabled = serviceConnected && draft.targetPackage.isNotBlank() && !packageConflict,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.request_app_scope)) }
                        OutlinedButton(
                            onClick = { onExport(draft) },
                            enabled = validationErrors.isEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.export_profile_json)) }
                        if (originalPackage != null) {
                            TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.delete_profile))
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
            title = { Text(stringResource(R.string.delete_profile_question)) },
            text = { Text(stringResource(R.string.delete_profile_desc, originalPackage)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete(originalPackage) }) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(stringResource(R.string.discard_changes_question)) },
            text = { Text(stringResource(R.string.discard_changes_desc)) },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; onBack() }) { Text(stringResource(R.string.discard)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text(stringResource(R.string.keep_editing)) }
            },
        )
    }
}

@Composable
private fun localizedDiagnostic(message: String): String {
    val localeMatch = Regex("Locale region (\\S+) differs from country (\\S+)").matchEntire(message)
    return when {
        message.startsWith("Unknown time zone") -> stringResource(R.string.error_invalid_timezone)
        message.startsWith("Invalid locale tag") -> stringResource(R.string.error_invalid_locale)
        message.startsWith("Country code") -> stringResource(R.string.error_country_code)
        message.startsWith("Latitude") -> "−90…90"
        message.startsWith("Longitude") -> "−180…180"
        message.startsWith("Wi-Fi BSSID") -> stringResource(R.string.error_invalid_bssid)
        message.startsWith("MCC must") -> stringResource(R.string.error_invalid_mcc)
        message.startsWith("MNC must") -> stringResource(R.string.error_invalid_mnc)
        message.startsWith("MCC and MNC") -> stringResource(R.string.error_mcc_mnc_pair)
        localeMatch != null -> stringResource(
            R.string.diag_locale_country,
            localeMatch.groupValues[1],
            localeMatch.groupValues[2],
        )
        message == "Location is 0,0; set a real test location or synchronize from GeoIP" -> stringResource(R.string.diag_zero_location)
        message == "Geocoder override is enabled while location override is disabled" -> stringResource(R.string.diag_geocoder_location_off)
        message == "Wi-Fi override is enabled but no SSID/BSSID is configured" -> stringResource(R.string.diag_wifi_empty)
        message == "Telephony override is enabled but MCC/MNC is not configured" -> stringResource(R.string.diag_cell_empty)
        message == "Radio identity has no recorded source; verify it matches the selected location" -> stringResource(R.string.diag_radio_source_empty)
        message == "Follow VPN is enabled but no successful exit-IP sync is recorded" -> stringResource(R.string.diag_follow_unsynced)
        message == "Last exit-IP sync is older than 24 hours" -> stringResource(R.string.diag_follow_stale)
        else -> message
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
                Text(stringResource(R.string.choose_app), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    leadingIcon = { Symbol(R.drawable.ms_search_24) },
                    placeholder = { Text(stringResource(R.string.search_apps)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                if (filtered.isEmpty()) {
                    Text(
                        stringResource(R.string.no_matching_apps),
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
