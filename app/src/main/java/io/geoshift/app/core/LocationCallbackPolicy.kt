package io.geoshift.app.core

/**
 * Preserves Android callback absence semantics. An unavailable, cancelled or timed-out
 * location is never fabricated into a successful callback by GeoShift.
 * Kept independent of android.location to allow plain JVM regression tests.
 */
internal object LocationCallbackPolicy {
    fun <T : Any> transform(value: T?, rewrite: (T) -> T): T? =
        value?.let(rewrite)

    fun <T> transformList(values: List<T>, rewrite: (T) -> T): List<T> =
        if (values.isEmpty()) values else values.map(rewrite)

    /** Do not change error/empty geocoder outcomes to successful results. */
    fun <T> replaceNonEmptySuccess(
        values: List<T>,
        shouldRewrite: Boolean,
        replacement: () -> List<T>,
    ): List<T> =
        if (!shouldRewrite || values.isEmpty()) values else replacement()
}
