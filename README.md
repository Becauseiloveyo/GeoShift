# GeoShift

GeoShift is a free Android/LSPosed environment-profile tool for privacy, development, and location/region compatibility testing on devices you control.

## v0.2 development branch

The current implementation uses Modern Xposed API 102 and keeps one coherent per-app geographic profile.

### Implemented

- Dynamic LSPosed scope request for a target package
- Remote Preferences shared between manager app and hooked process
- Manual profile editing with JSON import/export
- VPN transport detection and public exit-IP GeoIP synchronization
- Persistent user-enabled foreground Follow-VPN service
- Automatic country, time zone, locale heuristic and approximate coordinates from the current exit IP
- Last successful exit IP / city / region / timestamp metadata
- Profile consistency diagnostics
- Per-app `TimeZone.getDefault()` override
- Per-app `Locale.getDefault()` override
- Per-app latitude/longitude override for Android `Location` objects
- Dynamic hook checks so profile enable/disable changes can take effect without reinstalling hooks in the already-targeted process
- Optional nearby radio provider layer with caching and de-duplication
- Optional OpenCellID nearby-cell adapter
- Optional WiGLE nearby-Wi-Fi adapter
- Provider credentials stored only in private local app preferences; they are not copied into LSPosed Remote Preferences or exported profiles
- Android CI build with downloadable debug APK artifact

### Follow VPN flow

```text
VPN/network changes
    -> foreground Follow-VPN service
    -> detect active VPN transport
    -> resolve public exit IP
    -> GeoIP (country/city/time zone/coordinates)
    -> save GeoProfile + sync metadata
    -> target app reads updated Remote Preferences
```

The foreground service continues to monitor network changes after the manager UI is closed. It is user-enabled and shows an Android foreground-service notification. It is not currently configured to auto-start after device reboot.

### Optional radio-data flow

```text
GeoProfile coordinates
    -> provider adapters
       -> WiGLE: nearby Wi-Fi (requires user's WiGLE API token)
       -> OpenCellID: nearby cells (requires user's OpenCellID API key)
    -> radius filtering
    -> identity de-duplication
    -> 10-minute in-memory cache
    -> preview in GeoShift
```

GeoShift does not bundle or redistribute third-party radio databases. OpenCellID data has its own attribution/share-alike requirements, and WiGLE access is subject to WiGLE's API terms and account limits. The current radio integration is a provider/preview layer; framework Wi-Fi and telephony result overrides are intentionally not enabled yet.

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

## Milestones

- [x] Android application skeleton
- [x] Profile model and validation
- [x] VPN network detection
- [x] Replaceable GeoIP provider
- [x] Time zone / locale hooks
- [x] Basic location hook
- [x] Persistent VPN-follow foreground service
- [x] Profile import/export
- [x] Automated consistency diagnostics
- [x] Nearby radio provider abstraction, cache and de-duplication
- [x] Optional OpenCellID nearby-cell adapter
- [x] Optional WiGLE nearby-Wi-Fi adapter
- [ ] Geocoder/address profile
- [ ] Provider coverage diagnostics and quota-aware retry/backoff
- [ ] Safe test implementation for framework Wi-Fi/cellular environment overrides
- [ ] Multi-profile manager for multiple target apps
- [ ] Auto-start/recovery policy after reboot
- [ ] Test matrix for Android 14/15/16/17

## Scope

GeoShift does not attempt to bypass hardware-backed attestation, Play Integrity, financial-service controls, anti-cheat systems, or server-side account risk systems.

## Licensing

No source code from paid applications has been copied into GeoShift. Third-party provider APIs/data remain under their own terms and licenses. Before adapting code from GPL/AGPL projects, GeoShift will adopt a compatible project license and preserve upstream attribution and source obligations.
