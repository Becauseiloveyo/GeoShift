package io.geoshift.app.network

data class GeoIpResult(
    val ip: String,
    val countryCode: String,
    val region: String,
    val city: String,
    val latitude: Double,
    val longitude: Double,
    val timezoneId: String,
)

fun interface GeoIpProvider {
    @Throws(Exception::class)
    fun lookupCurrentExit(): GeoIpResult
}
