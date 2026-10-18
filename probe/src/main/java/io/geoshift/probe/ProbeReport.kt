package io.geoshift.probe

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** Evidence describes what the app actually observed, never a claimed external guarantee. */
enum class ProbeVerdict { PASS, FAIL, NOT_RUN }
enum class ProbeActual { MATCH_TARGET, DIFFERENT, PRESENT, ABSENT, DENIED, TIMEOUT, ERROR, UNSUPPORTED, NOT_RUN }
enum class ProbePhase { baseline, active }

data class ProbeObservation(
    val api: String,
    val method: String,
    val expected: String,
    val actual: ProbeActual,
    val verdict: ProbeVerdict = ProbeVerdict.NOT_RUN,
    val fingerprint: String? = null,
) {
    init {
        require(api.matches(Regex("[a-z][a-z0-9_]{1,40}")))
        require(method.matches(Regex("[a-zA-Z][a-zA-Z0-9_]{1,60}")))
        require(expected in setOf("TARGET_MATCH", "UNCHANGED_FROM_BASELINE", "OBSERVE_ONLY"))
        require(fingerprint == null || fingerprint.matches(Regex("[a-f0-9]{32}")))
    }
}

data class ProbeReport(
    val session: String,
    val packageName: String,
    val role: String,
    val phase: ProbePhase,
    val sdk: Int,
    val manufacturer: String,
    val model: String,
    val appVersion: String,
    val sourceRevision: String,
    val locationGrant: String,
    val recordedAtUtc: String,
    val observations: List<ProbeObservation>,
) {
    init {
        require(session.matches(Regex("[A-Za-z0-9_-]{6,32}")))
        require(role in setOf("target", "control1", "control2", "control3"))
        require(packageName == "io.geoshift.probe.$role")
        require(locationGrant in setOf("FINE", "COARSE", "NONE"))
        require(recordedAtUtc.isNotBlank())
    }
}

object ProbeReportCodec {
    const val SCHEMA_VERSION = 1

    fun encode(report: ProbeReport): String {
        val rows = JSONArray()
        report.observations.forEach { sample ->
            rows.put(JSONObject().apply {
                put("api", sample.api)
                put("method", sample.method)
                put("expected", sample.expected)
                put("actual", sample.actual.name)
                put("status", sample.verdict.name)
                put("fingerprint", sample.fingerprint ?: JSONObject.NULL)
            })
        }
        return JSONObject().apply {
            put("schemaVersion", SCHEMA_VERSION)
            put("session", report.session)
            put("packageName", report.packageName)
            put("role", report.role)
            put("phase", report.phase.name)
            put("androidSdk", report.sdk)
            put("manufacturer", report.manufacturer)
            put("model", report.model)
            put("probeVersion", report.appVersion)
            put("geoshiftSourceRevision", report.sourceRevision)
            put("locationGrant", report.locationGrant)
            put("collectedAtUtc", report.recordedAtUtc)
            put("method", "user-initiated-owned-device")
            put("redacted", true)
            put("observations", rows)
        }.toString(2)
    }

    fun newTimestamp(): String = Instant.now().toString()
}
