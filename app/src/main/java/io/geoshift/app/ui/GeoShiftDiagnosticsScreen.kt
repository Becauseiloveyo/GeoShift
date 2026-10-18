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
import io.geoshift.app.core.NativeLocationAccess
import io.geoshift.app.core.PrivacyEvidence

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
                Text(
                    "A configuration score is not privacy protection. Grant snapshots, " +
                        "historical Hook evidence, and actual Probe results are independent.",
                    style = MaterialTheme.typography.bodySmall,
                )
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
                        val permission = state.nativePermissions[profile.targetPackage]
                            ?: NativeLocationAccess.UNKNOWN
                        val evidence = PrivacyEvidence.summarize(
                            profile, permission, state.auditEvents,
                        )
                        Text(
                            "Configuration completeness: ${evidence.configurationScore}/100 — not a protection score",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "Android location permission: ${permissionLabel(permission)} (read-only snapshot)",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Button(onClick = { actions.openNativePermissions(profile.targetPackage) }) {
                            Text("Open Android app permissions")
                        }
                        Text(
                            when (permission) {
                                NativeLocationAccess.PRECISE ->
                                    if (state.androidSdk >= 31)
                                        "If precision is unnecessary, select Approximate or Deny in Android settings."
                                    else "If location is unnecessary, deny it in Android settings."
                                NativeLocationAccess.APPROXIMATE ->
                                    "Approximate location is granted; deny it if no location access is required."
                                NativeLocationAccess.DENIED ->
                                    "Location is not currently granted; other signals can still reveal region."
                                NativeLocationAccess.NOT_REQUESTED ->
                                    "The app does not declare fine/coarse location permissions."
                                NativeLocationAccess.UNKNOWN ->
                                    "Package visibility or read errors prevent an accurate grant snapshot."
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "Actual API / cross-app Probe: ${evidence.probeOutcome.name} (no results imported)",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "Historical Hook evidence: installed=${evidence.installedSurfaces}, " +
                                "hit=${evidence.hitSurfaces}, rewritten=${evidence.rewrittenSurfaces}, " +
                                "failed=${evidence.failedSurfaces}. Log events can be forged.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            "Profile ${if (profile.enabled) "enabled" else "disabled"} · " +
                                "Historical event details (not live proof):",
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
                    "Use a separate Probe on owned devices for actual output/isolation verification. " +
                    "Android Settings manages native grants; GeoShift cannot change them " +
                    "or certify background location, AppOps, or server-side location sources.",
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


private fun permissionLabel(value: NativeLocationAccess): String = when (value) {
    NativeLocationAccess.UNKNOWN -> "Unknown"
    NativeLocationAccess.NOT_REQUESTED -> "Not requested"
    NativeLocationAccess.DENIED -> "Denied / not granted"
    NativeLocationAccess.APPROXIMATE -> "Approximate (coarse)"
    NativeLocationAccess.PRECISE -> "Precise (fine)"
}
