package io.geoshift.app.core

/**
 * G03 diagnostic evidence, intentionally separate from ProfileHealth.
 * These records show observed hook activity, NOT proof that private data cannot leak.
 * Logs carry package name (needed to correlate scope), but never coordinates, IP, SSID,
 * cell IDs, tokens, arbitrary method arguments or source exceptions.
 */
enum class AuditOutcome { INSTALLED, HIT, REWRITTEN, FAILED, NOT_COVERED }

data class HookAuditEvent(
    val packageName: String,
    val surface: String,
    val outcome: AuditOutcome,
    val identityVerified: Boolean,
)

enum class HookAuditStatus { NOT_VERIFIED, INSTALLED, HIT, REWRITTEN, FAILED, NOT_COVERED }

object HookAudit {
    const val MARKER = "GS_AUDIT_V1"
    const val MAX_DOCUMENT_CHARS = 1_000_000
    const val MAX_RECORDS = 2_000
    val SURFACES = setOf("location_getter", "framework_last", "framework_callback",
        "system_last", "system_callback", "timezone", "locale", "geocoder",
        "wifi", "telephony", "cell_identity", "strict_provider")
    private val packagePattern = Regex("[a-zA-Z0-9_]+(\\.[a-zA-Z0-9_]+)+")
    private val tokenPattern = Regex("[A-Za-z_]+")

    fun encode(event: HookAuditEvent): String? {
        if (!valid(event)) return null
        return "$MARKER pkg=${event.packageName} surface=${event.surface} " +
            "outcome=${event.outcome.name} identity=${if (event.identityVerified) "verified" else "unknown"}"
    }

    fun parseLine(line: String): HookAuditEvent? {
        val begin = line.indexOf(MARKER)
        if (begin < 0) return null
        val tail = line.substring(begin).trim()
        // Reject trailing arbitrary tokens rather than storing raw logs.
        val fields = tail.split(' ')
        if (fields.size != 5 || fields.first() != MARKER) return null
        val parsed = fields.drop(1).map { it.split('=', limit = 2) }
        if (parsed.any { it.size != 2 }) return null
        val values = parsed.associate { it[0] to it[1] }
        if (values.keys != setOf("pkg", "surface", "outcome", "identity")) return null
        val outcome = runCatching { AuditOutcome.valueOf(values.getValue("outcome")) }.getOrNull() ?: return null
        return HookAuditEvent(
            packageName = values.getValue("pkg"),
            surface = values.getValue("surface"),
            outcome = outcome,
            identityVerified = values.getValue("identity") == "verified",
        ).takeIf { valid(it) && values.getValue("identity") in setOf("verified", "unknown") }
    }

    fun parseDocument(content: String): List<HookAuditEvent> {
        require(content.length <= MAX_DOCUMENT_CHARS) { "Diagnostic log is too large" }
        return content.lineSequence().mapNotNull(::parseLine)
            .distinct().take(MAX_RECORDS).toList()
    }

    fun summary(
        events: List<HookAuditEvent>,
        packageName: String,
        surface: String,
    ): HookAuditStatus {
        val relevant = events.filter { it.packageName == packageName && it.surface == surface }
        if (relevant.isEmpty()) return HookAuditStatus.NOT_VERIFIED
        if (relevant.any { it.outcome == AuditOutcome.FAILED }) return HookAuditStatus.FAILED
        if (relevant.any { it.outcome == AuditOutcome.REWRITTEN && it.identityVerified }) return HookAuditStatus.REWRITTEN
        if (relevant.any { it.outcome == AuditOutcome.HIT && it.identityVerified }) return HookAuditStatus.HIT
        if (relevant.any { it.outcome == AuditOutcome.INSTALLED }) return HookAuditStatus.INSTALLED
        if (relevant.any { it.outcome == AuditOutcome.NOT_COVERED }) return HookAuditStatus.NOT_COVERED
        return HookAuditStatus.NOT_VERIFIED
    }

    private fun valid(e: HookAuditEvent): Boolean =
        e.packageName.length in 3..180 && packagePattern.matches(e.packageName) &&
            e.surface in SURFACES && tokenPattern.matches(e.surface)
}
