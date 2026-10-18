package io.geoshift.probe

import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The manually shared, non-exported secret permits same-session comparisons
 * without putting actual GPS or radio identifiers into exported reports.
 *
 * Quantization is only to reduce noise, NOT a reliable movement detector;
 * coarse/fine permission differences invalidate direct fingerprint comparison.
 */
object ProbeFingerprint {
    fun validSecret(secret: String): Boolean =
        secret.length >= 16 && secret.length <= 128

    fun digest(secret: String, domain: String, value: String): String? {
        if (!validSecret(secret) || value.isBlank()) return null
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        val bytes = mac.doFinal("$domain:$value".toByteArray(StandardCharsets.UTF_8))
        return bytes.take(16).joinToString("") { "%02x".format(it) }
    }

    fun location(secret: String, latitude: Double, longitude: Double): String? {
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0 ||
            !latitude.isFinite() || !longitude.isFinite()) return null
        val latBucket = (latitude * 100.0).roundToInt()
        val lonBucket = (longitude * 100.0).roundToInt()
        return digest(secret, "location-v1", "$latBucket,$lonBucket")
    }

    fun nearExpected(latitude: Double, longitude: Double, expectedLat: Double, expectedLon: Double): Boolean {
        if (!latitude.isFinite() || !longitude.isFinite() ||
            !expectedLat.isFinite() || !expectedLon.isFinite()) return false
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0 ||
            expectedLat !in -90.0..90.0 || expectedLon !in -180.0..180.0) return false
        // A deliberately generous ±0.01 degree bound for human-entered test points.
        // This is a *comparison*, not a claim of spoofing correctness.
        return abs(latitude - expectedLat) <= 0.01 &&
            abs(longitude - expectedLon) <= 0.01
    }
}
