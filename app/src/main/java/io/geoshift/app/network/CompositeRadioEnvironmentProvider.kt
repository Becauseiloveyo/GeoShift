package io.geoshift.app.network

import kotlin.math.roundToInt

class CompositeRadioEnvironmentProvider(
    private val providers: List<RadioEnvironmentProvider>,
) : RadioEnvironmentProvider {
    override fun nearbyWifi(latitude: Double, longitude: Double, radiusMeters: Int): List<WifiEnvironment> {
        return providers.flatMap { it.nearbyWifi(latitude, longitude, radiusMeters) }
            .filter { GeoMath.distanceMeters(latitude, longitude, it.latitude, it.longitude) <= radiusMeters }
            .distinctBy { it.bssid.uppercase() }
            .sortedBy { GeoMath.distanceMeters(latitude, longitude, it.latitude, it.longitude) }
    }

    override fun nearbyCells(latitude: Double, longitude: Double, radiusMeters: Int): List<CellEnvironment> {
        return providers.flatMap { it.nearbyCells(latitude, longitude, radiusMeters) }
            .filter { GeoMath.distanceMeters(latitude, longitude, it.latitude, it.longitude) <= radiusMeters }
            .distinctBy { "${it.radio.uppercase()}:${it.mcc}:${it.mnc}:${it.areaCode}:${it.cellId}" }
            .sortedBy { GeoMath.distanceMeters(latitude, longitude, it.latitude, it.longitude) }
    }
}

class CachingRadioEnvironmentProvider(
    private val delegate: RadioEnvironmentProvider,
    private val ttlMs: Long = 10 * 60 * 1000L,
) : RadioEnvironmentProvider {
    private data class Key(val latBucket: Int, val lonBucket: Int, val radius: Int, val kind: String)
    private data class Entry<T>(val createdAt: Long, val value: T)
    private val cache = mutableMapOf<Key, Entry<Any>>()

    override fun nearbyWifi(latitude: Double, longitude: Double, radiusMeters: Int): List<WifiEnvironment> =
        cached(key(latitude, longitude, radiusMeters, "wifi")) {
            delegate.nearbyWifi(latitude, longitude, radiusMeters)
        }

    override fun nearbyCells(latitude: Double, longitude: Double, radiusMeters: Int): List<CellEnvironment> =
        cached(key(latitude, longitude, radiusMeters, "cell")) {
            delegate.nearbyCells(latitude, longitude, radiusMeters)
        }

    private fun key(latitude: Double, longitude: Double, radiusMeters: Int, kind: String) = Key(
        latBucket = (latitude * 10_000).roundToInt(),
        lonBucket = (longitude * 10_000).roundToInt(),
        radius = radiusMeters,
        kind = kind,
    )

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> cached(key: Key, loader: () -> T): T {
        val now = System.currentTimeMillis()
        synchronized(cache) {
            val current = cache[key]
            if (current != null && now - current.createdAt <= ttlMs) return current.value as T
        }
        val loaded = loader()
        synchronized(cache) { cache[key] = Entry(System.currentTimeMillis(), loaded) }
        return loaded
    }
}
