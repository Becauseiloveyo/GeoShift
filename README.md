# GeoShift

GeoShift is a free Android/LSPosed geographic-environment profile tool for privacy, development, and location/region compatibility testing on devices and apps you control.

## v0.3.4 development branch

GeoShift uses Modern Xposed API 102 and maintains independent, reactive profiles for multiple target apps. A profile can keep location, address, time zone, locale, VPN-derived region metadata, nearby Wi-Fi, and cellular identity internally consistent.

### Implemented

#### Multi-profile manager

- Multiple independent target-app profiles
- Dynamic LSPosed scope requests per target package
- Remote Preferences shared between the manager and hooked processes
- Reactive profile reloads without reinstalling hooks when a saved profile changes
- Manual editing plus JSON import/export; current export schema is v4
- Material 3 Compose UI with phone/tablet layouts, dynamic color, edge-to-edge rendering, English and Simplified Chinese
- Basic and Advanced editor modes so raw radio identifiers are hidden unless needed
- Installed-app picker with off-main-thread icon loading and an LRU bitmap cache
- 0–100 profile-health report covering map readiness, Wi-Fi environment, cellular completeness, VPN-sync freshness, and consistency warnings

#### Follow VPN and regional identity

- VPN transport detection
- Public exit-IP GeoIP synchronization
- One resolved exit result can be reused across all enabled Follow VPN profiles
- Automatic country, time zone, locale heuristic, approximate coordinates, city/region, IP, and sync timestamp
- User-enabled foreground Follow-VPN service
- Reboot/package-replacement recovery when Follow VPN was previously enabled
- Per-app overrides for `TimeZone.getDefault()` and `ZoneId.systemDefault()`
- Per-app overrides for `Locale.getDefault()` and default `LocaleList`

#### Location and map compatibility

- Android `Location` latitude/longitude overrides
- `LocationManager.getLastKnownLocation()` and `getCurrentLocation()` result paths
- `requestLocationUpdates()` / `requestSingleUpdate()` listener wrapping with identity-safe `removeUpdates()` handling
- Google Play services `LocationResult` getter coverage when the SDK is present
- AMap `AMapLocation` and Baidu `BDLocation` coordinate getter adapters when those SDK classes are present
- Synthetic configured locations retain normal time, elapsed-realtime, and accuracy metadata
- Per-profile `Geocoder.getFromLocation()` result for the configured profile coordinates

#### Wi-Fi and cellular environment

- Manual Wi-Fi SSID, BSSID, and RSSI profile fields
- Provider-backed Wi-Fi environment with up to 8 distinct nearby APs per profile
- `WifiInfo.getSSID()`, `getBSSID()`, and `getRssi()` overrides
- Controlled `WifiManager.getScanResults()` support on Android 11+ using the profile/provider-backed AP set
- Network/SIM country, MCC/MNC, and optional operator-name overrides
- Cell radio, area code (TAC/LAC), and cell ID/NCI persistence
- Cell-identity getter adapters for LTE, NR, GSM, WCDMA, and TD-SCDMA where the corresponding Android class/API is available

GeoShift does **not** claim to fabricate every `TelephonyManager.getAllCellInfo()` object or every vendor-private radio API. Current cellular support focuses on stable public identity getter paths that can be tested safely.

#### Optional public radio providers

- WiGLE nearby-Wi-Fi adapter using the user's WiGLE API credentials
- OpenCellID nearby-cell adapter using the user's OpenCellID API key
- Radius filtering, identity de-duplication, and a 10-minute in-memory cache
- Provider results can be previewed before applying them to a profile
- Applying a provider suggestion preserves existing manual data when a provider returns no replacement for that category
- Limited retry/backoff for transient I/O failures, HTTP 429, and 5xx responses; credential failures such as 401/403 are not blindly retried
- Provider credentials remain only in GeoShift's private local app preferences and are never copied to Remote Preferences or exported profile JSON

GeoShift does not bundle or redistribute third-party radio databases. OpenCellID and WiGLE data remain subject to their respective licenses, attribution requirements, account limits, and API terms.

## Follow VPN flow

```text
VPN/network changes
    -> foreground Follow-VPN service
    -> detect active VPN transport
    -> resolve public exit once
    -> GeoIP country/city/time zone/coordinates
    -> update all enabled Follow VPN profiles
    -> save per-app Remote Preferences
    -> hooked target processes react to the updated profile
```

The exit-IP check is performed by GeoShift itself. When split tunneling is used, GeoShift cannot yet prove that every target app exits through the same route; target-app-specific exit verification remains a separate validation item.

## Radio-data flow

```text
GeoProfile coordinates
    -> WiGLE / OpenCellID adapters
    -> radius filtering + de-duplication + cache
    -> preview
    -> optional apply
       -> up to 8 Wi-Fi APs
       -> primary Wi-Fi RSSI
       -> MCC / MNC / radio / TAC-LAC / Cell ID
    -> target app public Wi-Fi/cell identity getter paths
```

## Build

Requirements:

- JDK 21
- Android SDK Platform 37.0 / Build Tools 37.0.0
- Gradle 9.5.1
- Android Gradle Plugin 9.2.1

Run unit tests:

```bash
gradle :app:testDebugUnitTest
```

Build the normal debug APK:

```bash
gradle :app:assembleDebug
```

Compile the release-like benchmark target and Macrobenchmark test APK:

```bash
gradle :app:assembleBenchmark :benchmark:assembleBenchmark
```

Every GitHub Actions PR build runs unit tests, compiles the Macrobenchmark suite, builds the debug APK, and uploads `app-debug.apk` as a `GeoShift-debug-<commit>` artifact for 14 days.

## Performance testing

The `benchmark` module uses AndroidX Macrobenchmark 1.4.1 and UiAutomator 2.4.0. It currently contains:

- Cold-start timing with no pre-compilation
- Frame-timing measurement while scrolling the installed-app picker, which exercises the large app/icon list
- A Baseline Profile generator covering startup and the same app-picker path

GitHub-hosted runners only **compile** these tests because they do not provide a controlled Android device suitable for trustworthy frame/startup measurements. Run them on a stable physical device or suitable test device instead:

```bash
gradle :benchmark:connectedBenchmarkAndroidTest
```

Performance numbers should be compared on the same device, Android build, refresh-rate/thermal conditions, and benchmark configuration. A generated Baseline Profile should be committed only after a real-device run is reviewed.

## CI baseline

The v0.3.4-dev code baseline is validated with:

- 13 JVM unit tests
- API 37 / Java 21 / libxposed API 102 compilation
- Release-like `:app:assembleBenchmark`
- Macrobenchmark test APK compilation
- Debug APK assembly and artifact upload

## Milestones

- [x] Android application skeleton
- [x] Multi-profile manager and per-package Remote Preferences
- [x] Profile validation, JSON import/export, and health diagnostics
- [x] VPN network detection and replaceable GeoIP provider
- [x] Persistent Follow-VPN foreground service and reboot/package-replacement recovery
- [x] Time zone, ZoneId, locale, and LocaleList hooks
- [x] Location getter and LocationManager delivery-path coverage
- [x] Google LocationResult, AMap, and Baidu coordinate adapters
- [x] Geocoder/address profile response
- [x] Nearby radio provider abstraction, cache, de-duplication, and transient retry/backoff
- [x] Optional OpenCellID nearby-cell adapter
- [x] Optional WiGLE nearby-Wi-Fi adapter
- [x] Provider-backed multi-AP Wi-Fi profiles and scan-result hook
- [x] Public cellular identity getter coverage for common radio generations
- [x] Compose Material 3 adaptive manager UI with Basic/Advanced editing
- [x] Macrobenchmark startup/scroll harness and Baseline Profile generator
- [ ] Real-device compatibility matrix for Android 14/15/16/17
- [ ] Individual Google Maps / AMap / Baidu behavior verification across supported Android versions
- [ ] Target-app-specific split-tunnel exit verification
- [ ] Broader `CellInfo` object coverage only where public APIs are stable and real-device tests justify it
- [ ] Generate, review, and commit a production Baseline Profile from controlled device measurements

## Scope

GeoShift is intended for privacy, development, QA, and compatibility testing on devices and apps the user controls. It does not attempt to bypass hardware-backed attestation, Play Integrity, financial-service controls, anti-cheat systems, licensing checks, or server-side account/risk systems.

## Licensing

No source code from paid applications has been copied into GeoShift. Third-party provider APIs/data remain under their own terms and licenses. Before adapting GPL/AGPL code, GeoShift will adopt a compatible project license and preserve required attribution and source obligations.
