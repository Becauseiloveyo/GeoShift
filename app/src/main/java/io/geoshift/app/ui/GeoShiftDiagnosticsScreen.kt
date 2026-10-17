package io.geoshift.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.geoshift.app.core.HookAudit
import io.geoshift.app.core.HookAuditStatus

/**
 * G03: imported hook-log evidence, not a live security measurement.
 * Never display 'verified protection' without a separate owned-device Probe.
 */
@Composable
internal fun DiagnosticsScreen(
    state: GeoShiftUiState,
    actions: GeoShiftActions,
    padding: PaddingValues,
) {
    val surfaces = listOf(
        "location_getter" to "Location getters",
        "framework_last" to "Framework cached location",
        "framework_callback" to "Framework callbacks",
        "system_last" to "System direct cached location",
        "system_callback" to "System direct callbacks",
        "timezone" to "Timezone",
        "locale" to "Locale",
        "geocoder" to "Geocoder",
        "wifi" to "Wi-Fi",
        "telephony" to "Telephony",
        "cell_identity" to "Cell identity",
    )
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Hook diagnostics", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Historical log evidence, not live proof of privacy protection. " +
                        "Unobserved or unsupported APIs remain Not verified. " +
                        "Imported logs can be edited or forged.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(state.auditLogStatus, style = MaterialTheme.typography.labelMedium)
                Button(onClick = actions.importAudit) { Text("Import Xposed / logcat text") }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("System process registration", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Registration is system-wide. Per-profile events require separate UID attribution.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    listOf("system_last" to "Cached location", "system_callback" to "Callbacks").forEach { (surface, name) ->
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Text(
                                statusLabel(HookAudit.summary(state.auditEvents, "io.geoshift.system", surface)),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
        }
        if (state.profiles.isEmpty()) {
            item { Text("Create a test profile to correlate a Hook audit with an app.") }
        } else {
            items(state.profiles, key = { it.targetPackage }) { profile ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(profile.targetPackage, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Profile ${if (profile.enabled) "enabled" else "disabled"} · " +
                                "Log events are historical and may be stale.",
                            style = MaterialTheme.typography.labelSmall,
                        )
                        surfaces.forEach { (surface, label) ->
                            val status = HookAudit.summary(state.auditEvents, profile.targetPackage, surface)
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                Text(statusLabel(status), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
        item {
            Text(
                "Installed = installation event reported; Hit = call observed; " +
                    "Rewritten = a replacement was reported; Failed = failure reported. " +
                    "Identity unknown never counts as a verified Hit or Rewritten. " +
                    "Use a separate Probe on owned devices for actual output/isolation verification.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun statusLabel(value: HookAuditStatus): String = when (value) {
    HookAuditStatus.NOT_VERIFIED -> "Not verified"
    HookAuditStatus.INSTALLED -> "Installed"
    HookAuditStatus.HIT -> "Hit"
    HookAuditStatus.REWRITTEN -> "Rewritten"
    HookAuditStatus.FAILED -> "Failed"
    HookAuditStatus.NOT_COVERED -> "Not covered"
}
