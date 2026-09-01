# GeoShift

GeoShift is a free Android/LSPosed environment-profile tool for privacy, development, and location/region compatibility testing on devices you control.

## v0.1 foundation

The first implementation uses the Modern Xposed API 102 and keeps one coherent per-app geographic profile.

### Implemented

- Dynamic LSPosed scope request for a target package
- Remote Preferences shared between manager app and hooked process
- Manual profile editing
- VPN transport detection
- Public exit-IP GeoIP synchronization
- Automatic country, time zone and approximate coordinates from the current exit IP
- Per-app `TimeZone.getDefault()` override
- Per-app `Locale.getDefault()` override
- Per-app latitude/longitude override for Android `Location` objects
- Wi-Fi/cellular environment provider interfaces for later database-backed implementations
- Android CI build with downloadable debug APK artifact

### Follow VPN flow

```text
VPN/network changes
    -> detect active VPN transport
    -> resolve public exit IP
    -> GeoIP (country/city/time zone/coordinates)
    -> save GeoProfile
    -> target app reads updated Remote Preferences
```

The current automatic listener runs while the GeoShift manager is open. Persistent background synchronization is a later milestone.

## Build

Requirements:

- JDK 21
- Android SDK Platform 37.0 / Build Tools 37.0.0
- Gradle 9.5.1

```bash
gradle :app:assembleDebug
```

The project uses Android Gradle Plugin 9.2.1, Android API 37, `io.github.libxposed:api:102.0.0`, and `io.github.libxposed:service:102.0.0`. AGP 9's built-in Kotlin support is used instead of applying the legacy Kotlin Android Gradle plugin.

Every CI build uploads `app-debug.apk` as a `GeoShift-debug-<commit>` GitHub Actions artifact for 14 days.

## Next milestones

- [x] Android application skeleton
- [x] Profile model and validation
- [x] VPN network detection
- [x] Replaceable GeoIP provider
- [x] Time zone / locale hooks
- [x] Basic location hook
- [x] Wi-Fi / cellular provider interfaces
- [ ] Persistent VPN-follow background service
- [ ] Geocoder/address profile
- [ ] Database-backed nearby Wi-Fi provider
- [ ] Database-backed nearby cellular provider
- [ ] Profile import/export
- [ ] Automated consistency diagnostics
- [ ] Test matrix for Android 14/15/16

## Scope

GeoShift does not attempt to bypass hardware-backed attestation, Play Integrity, financial-service controls, anti-cheat systems, or server-side account risk systems.

## Licensing

No source code from paid applications has been copied into GeoShift. Before adapting code from GPL/AGPL projects, GeoShift will adopt a compatible project license and preserve upstream attribution and source obligations.
