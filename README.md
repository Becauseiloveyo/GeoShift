# GeoShift

GeoShift is an Android 10+ / libxposed module for per-app geographic environment profiles and controlled location compatibility testing.

## Current development milestone: v0.4.2-dev

The v0.4 branch keeps the v0.3.4 per-app profile, Follow-VPN, Wi-Fi/cell provider, localization, diagnostics, and performance work while adding an Android 17 / HyperOS system-server compatibility layer.

### Normal per-app mode

Normal operation remains fail-open and per-app where client identity is available:

- `LocationManagerService.getLastLocation`
- `getCurrentLocation` callback wrapping
- continuous `registerLocationListener` callback wrapping
- target-process Android `Location` APIs
- Google `LocationResult` when present
- AMap `AMapLocation` when present
- Baidu `BDLocation` when present
- timezone, locale, Geocoder, Wi-Fi identity and telephony identity profile overrides

Profiles use an in-memory `ProfileKey(userId, packageName)` identity model in the system layer. Ambiguous broker paths are not guessed in normal mode.

### Android 17 / HyperOS system foundation

The Modern libxposed `system` scope uses `onSystemServerStarting()` and requires both `PROP_CAP_SYSTEM` and `PROP_CAP_REMOTE`.

Runtime capability probing currently checks for:

- `LocationManagerService`
- `LocationProviderManager`
- `AbstractLocationProvider`
- `GnssLocationProvider`
- Xiaomi `MiuiBlurLocationManagerImpl`

The system hook hot path consumes an immutable snapshot through `AtomicReference`; network access, provider HTTP calls and JSON work stay outside location delivery.

### Strict Map Session test gate

v0.4.2 adds an explicit, runtime-only Strict Map Session for cases where a brokered fused/network provider initially accepts the profile location but a later shared provider update moves the map back to the real device location.

Strict mode is intentionally **inactive by default**. While inactive, the new provider-global hooks immediately proceed with the original system values.

When explicitly armed for an existing enabled location profile, strict mode also rewrites provider-delivery locations in:

- `AbstractLocationProvider.reportLocation` / compatible report callbacks
- `LocationProviderManager.onReportLocation`
- passive provider update paths when available
- `GnssLocationProvider` report-location paths

`android.location.LocationResult` is copied through its `create(List<Location>)` factory where available instead of mutating the provider-owned `mLocations` list in place. Plain `Location`, `List<Location>` and `Location[]` arguments are also copied/replaced. Unsupported shapes fail open.

Strict mode also sanitizes the copied Android 12+ `Location.isMock` flag. Original provider timing, accuracy and other copied metadata remain intact unless a profile-owned field is explicitly replaced.

**Strict mode is a shared system location session, not a guaranteed per-app isolation mode.** It exists specifically for broker/Fused paths where the final app identity may have been lost before the update reaches `system_server`. Stop the session after the map compatibility test.

#### Shell/root-only control during this validation milestone

The temporary command receiver is protected by `android.permission.DUMP`, so ordinary third-party apps cannot arm the provider-global session.

Start strict mode for a profile in the primary user:

```sh
su -c 'am broadcast -n io.geoshift.app/.StrictSessionCommandReceiver --ez enable true --es package <target.package>'
```

Stop strict mode:

```sh
su -c 'am broadcast -n io.geoshift.app/.StrictSessionCommandReceiver --ez enable false'
```

For another Android user/profile, add `--ei user <userId>` to the start command.

A strict session is never automatically restored after reboot. `system_server` records the previous command id as historical state and only arms strict mode after a new post-boot command.

During strict-session validation, disable other location-modification Xposed modules first so their system-provider hooks do not contaminate results.

### Radio environment

Profiles can hold up to 8 nearby Wi-Fi access points plus cellular identity data:

- Wi-Fi SSID/BSSID/RSSI and nearby AP list
- MCC/MNC
- cellular radio type
- TAC/LAC / area code
- cell ID

WiGLE and OpenCellID provider support is optional. Provider data is target-region observation data, not guaranteed real-time radio state.

### Scope reconciliation

When the libxposed service binds, GeoShift checks the current module scope and requests missing packages for enabled profiles. This prevents a configured profile from silently existing without target-process fallback hooks being loaded.

### Safety / stability rules

System-server work follows these constraints:

- fail open on unknown classes, signatures or values
- no network or synchronous disk work in location hot paths
- no framework.jar patching
- no SELinux disabling
- no static-final mutation
- no MessageQueue private-layout dependency
- no automatic strict-session restore after reboot
- no native inline hook requirement for the v0.4 location path

GeoShift is intended for privacy, development, QA, and location/region compatibility testing on devices and apps you control. It does not target hardware-backed attestation, Play Integrity, financial-service controls, anti-cheat, licensing/payment eligibility, reward systems, or server-side fraud/risk controls.

## Build

Current app toolchain:

- compileSdk / targetSdk 37
- Java 21
- libxposed API/service 102.0.0
- Compose Material 3
- Macrobenchmark 1.4.1
- UIAutomator 2.4.0
- ProfileInstaller 1.4.1

CI runs JVM tests, compiles the Macrobenchmark suite, builds the debug APK and uploads the debug artifact.
