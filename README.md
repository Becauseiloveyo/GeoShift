# GeoShift

GeoShift is an Android environment-profile tool for privacy, development, and location/region compatibility testing on rooted devices with LSPosed.

## Goals

- Per-app environment profiles
- Manual and VPN-following geographic profiles
- Time zone, locale, country, location and geocoder overrides
- Extensible providers for Wi-Fi and cellular test data
- Consistency checks so one profile remains internally coherent
- Clear separation between stable device identity and changing geographic state

## Scope

GeoShift is intended for testing, privacy, and compatibility work on devices you control. It does not attempt to bypass hardware-backed attestation, Play Integrity, financial-service controls, anti-cheat systems, or server-side account risk systems.

## Planned architecture

```text
app/
  UI + profile management
core/
  profile model + validation
hooks/
  LSPosed entry point + hook providers
network/
  VPN detection + GeoIP abstraction
```

## Initial milestones

- [x] Repository bootstrap
- [ ] Android application skeleton
- [ ] Profile data model
- [ ] VPN network detection
- [ ] GeoIP provider interface
- [ ] Time zone / locale hook providers
- [ ] Location test provider
- [ ] Consistency validator
- [ ] Wi-Fi / cellular data provider interfaces

## License

To be decided before importing or adapting code from third-party projects. Any reused code must preserve its original license obligations.
