package io.geoshift.app.network

data class WifiEnvironment(
    val bssid: String,
    val ssid: String?,
    val latitude: Double,
    val longitude: Double,
    val estimatedRssiDbm: Int? = null,
    val source: String = "",
)

data class CellEnvironment(
    val radio: String,
    val mcc: Int,
    val mnc: Int,
    val areaCode: Long,
    val cellId: Long,
    val latitude: Double,
    val longitude: Double,
    val estimatedSignalDbm: Int? = null,
    val source: String = "",
)

interface RadioEnvironmentProvider {
    fun nearbyWifi(latitude: Double, longitude: Double, radiusMeters: Int): List<WifiEnvironment>
    fun nearbyCells(latitude: Double, longitude: Double, radiusMeters: Int): List<CellEnvironment>
}
