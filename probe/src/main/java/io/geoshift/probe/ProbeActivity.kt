package io.geoshift.probe

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import java.util.concurrent.Executors

/**
 * An independent, locally initiated test app. No network uploads, no analytics,
 * no silent run on startup, no global Hook activation, no raw sample log export.
 */
class ProbeActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor { job ->
        Thread(job, "GeoShiftProbe").apply { isDaemon = true }
    }
    private lateinit var sessionField: EditText
    private lateinit var secretField: EditText
    private lateinit var latitudeField: EditText
    private lateinit var longitudeField: EditText
    private lateinit var phaseField: Spinner
    private lateinit var runButton: Button
    private lateinit var exportButton: Button
    private lateinit var output: TextView
    private var lastReport: String? = null
    private val saveDocumentRequest = 405

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        fun label(value: String) {
            container.addView(TextView(this).apply {
                text = value
                textSize = 15f
                setPadding(0, 8, 0, 8)
            })
        }
        fun entry(hintText: String): EditText = EditText(this).also {
            it.hint = hintText
            it.isSingleLine = true
            container.addView(it)
        }

        label("GeoShift Controlled Probe — ${BuildConfig.PROBE_ROLE.uppercase()}")
        label("Only install on an owned test device. Never put target Probe in LSPosed scope for control APKs.")
        label("This app performs API calls only when you press Run. It exports no raw coordinates, SSID, BSSID or cell IDs.")
        sessionField = entry("Shared session ID, 6–32 letters/numbers/-/_")
        secretField = entry("Shared random secret (16–128 chars; never exported)").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        latitudeField = entry("Expected target latitude (optional)").apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or
                InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        longitudeField = entry("Expected target longitude (optional)").apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL or
                InputType.TYPE_NUMBER_FLAG_SIGNED
        }
        label("Phase: baseline before enabling target Profile; active after enabling target Profile.")
        phaseField = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@ProbeActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("baseline", "active"),
            )
        }
        container.addView(phaseField)

        container.addView(Button(this).apply {
            text = "Request test permissions (explicit)"
            setOnClickListener {
                val permissions = mutableListOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.READ_PHONE_STATE,
                )
                if (Build.VERSION.SDK_INT >= 33) {
                    permissions += Manifest.permission.NEARBY_WIFI_DEVICES
                }
                requestPermissions(permissions.toTypedArray(), 401)
            }
        })
        runButton = Button(this).apply {
            text = "Run API measurements"
            setOnClickListener { runTest() }
        }
        container.addView(runButton)
        exportButton = Button(this).apply {
            text = "Export redacted JSON report"
            isEnabled = false
            setOnClickListener { exportReport() }
        }
        container.addView(exportButton)
        output = TextView(this).apply {
            text = "NOT RUN — no device measurements recorded yet"
            textSize = 13f
        }
        container.addView(output)
        setContentView(ScrollView(this).apply {
            addView(container, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        })
    }

    private fun runTest() {
        val session = sessionField.text.toString().trim()
        if (!session.matches(Regex("[A-Za-z0-9_-]{6,32}"))) {
            output.text = "Invalid shared session ID"
            return
        }
        val secret = secretField.text.toString()
        if (!ProbeFingerprint.validSecret(secret)) {
            output.text = "Enter a shared random secret of at least 16 characters"
            return
        }
        val latText = latitudeField.text.toString().trim()
        val lonText = longitudeField.text.toString().trim()
        val latitude = latText.toDoubleOrNull()
        val longitude = lonText.toDoubleOrNull()
        if ((latText.isNotEmpty() || lonText.isNotEmpty()) &&
            (latitude == null || longitude == null || latitude !in -90.0..90.0 ||
                longitude !in -180.0..180.0)) {
            output.text = "Enter both valid target coordinates, or leave both blank"
            return
        }
        val phase = if (phaseField.selectedItemPosition == 0) ProbePhase.baseline else ProbePhase.active
        runButton.isEnabled = false
        exportButton.isEnabled = false
        lastReport = null
        output.text = "Measuring 10 API surfaces; each async call has a bounded timeout…"
        val config = ProbeConfig(secret, latitude, longitude, BuildConfig.PROBE_ROLE, phase)
        worker.execute {
            try {
                val observations = ProbeEngine(this, config).run()
                val report = ProbeReport(
                    session = session,
                    packageName = packageName,
                    role = BuildConfig.PROBE_ROLE,
                    phase = phase,
                    sdk = Build.VERSION.SDK_INT,
                    manufacturer = Build.MANUFACTURER,
                    model = Build.MODEL,
                    appVersion = BuildConfig.VERSION_NAME,
                    sourceRevision = BuildConfig.SOURCE_REVISION,
                    recordedAtUtc = ProbeReportCodec.newTimestamp(),
                    observations = observations,
                )
                val content = ProbeReportCodec.encode(report)
                val summary = observations.joinToString("\n") { row ->
                    "${row.api}.${row.method}: ${row.actual.name} / ${row.verdict.name}"
                }
                runOnUiThread {
                    lastReport = content
                    output.text = "Evidence collected for ${BuildConfig.PROBE_ROLE} / ${phase.name}.\n" +
                        "Control comparison is NOT_RUN until an offline baseline-vs-active report check.\n" +
                        summary
                    exportButton.isEnabled = true
                    runButton.isEnabled = true
                }
            } catch (_: Throwable) {
                runOnUiThread {
                    output.text = "Measurement or serialization error; no report produced"
                    runButton.isEnabled = true
                }
            }
        }
    }

    private fun exportReport() {
        if (lastReport == null) return
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(
                Intent.EXTRA_TITLE,
                "geoshift-${BuildConfig.PROBE_ROLE}-${phaseField.selectedItem}.json",
            )
        }
        startActivityForResult(intent, saveDocumentRequest)
    }

    @Deprecated("Framework callback for ACTION_CREATE_DOCUMENT")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != saveDocumentRequest || resultCode != RESULT_OK) return
        val json = lastReport ?: return
        val uri = data?.data ?: return
        worker.execute {
            val success = runCatching {
                contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use {
                    it.write(json)
                } ?: error("Export stream unavailable")
            }.isSuccess
            runOnUiThread { output.append(if (success) "\nRedacted JSON exported." else "\nExport failed.") }
        }
    }

    override fun onDestroy() {
        worker.shutdown()
        super.onDestroy()
    }
}
