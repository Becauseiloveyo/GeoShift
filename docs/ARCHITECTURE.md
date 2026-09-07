# GeoShift architecture

## Design rule

GeoShift separates stable device identity from geographic state. VPN changes should update geographic state only.

```text
Network change
    -> VPN detector
    -> public exit GeoIP
    -> GeoProfile
    -> Remote Preferences
    -> LSPosed target process
    -> TimeZone / Locale / Location providers
```

## Current v0.1 providers

- `TimeZone.getDefault()` override
- `Locale.getDefault()` override
- `Location.getLatitude()/getLongitude()` override
- VPN transport detection via `ConnectivityManager`
- Exit-IP geolocation through a replaceable `GeoIpProvider`

## Radio environment

`RadioEnvironmentProvider` intentionally defines Wi-Fi and cell models without binding GeoShift to one database. A later implementation can use a licensed/open dataset to retrieve nearby AP/cell records from the coordinates produced by GeoIP. This keeps data acquisition separate from Android hooks and makes caching/rate limits testable.

## Safety and compatibility boundary

GeoShift is for devices and applications the user is authorized to test. The project will not implement hardware-attestation bypasses, Play Integrity bypasses, anti-cheat bypasses, financial-service risk-control bypasses, or server-side account-history evasion.
