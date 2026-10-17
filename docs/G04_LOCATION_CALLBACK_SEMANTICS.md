# G04 · Location API semantics and listener lifecycle

Date: 2026-10-17. Target: GeoShift v0.5.0-alpha, branch `feat/v0.5-privacy-reliability`.

## Reason for this change
GeoShift v0.4 used `synthesize = true` for cached LocationManager calls, Consumer callback values, and Google `LocationResult` data. When the original provider returned `null` or an empty batch, the module could incorrectly manufacture a successful location. That breaks Android's legitimate no-fix, cancellation, timeout, and permission-result semantics. The old `WeakHashMap<listener, proxy>` also retained its key indirectly through proxy → original references.

## Implemented (G04)
- **No invented location:** `getLastKnownLocation`, `getCurrentLocation` Consumer callbacks, Google `LocationResult.getLastLocation`, and `getLocations` preserve a `null` result and empty lists; only non-null `Location` objects are copied and translated when an enabled Profile exists.
- **Do not mutate provider-owned locations:** produce copies only for actual returned Locations; original timing fields remain unmodified, with a fallback accuracy when missing.
- **Cancellation and executor semantics:** leave the original `CancellationSignal`, callback executor, and Android registration methods untouched; never immediately invoke the callback to fabricate a success.
- **Listener unregister correctness:** `LocationListenerRegistry` uses reference identity (`IdentityHashMap`), not `equals()`. `removeUpdates` supplies the same proxy used during registration, and removes the mapping *only after* Android accepts removal. Failed initial registration releases the unused proxy. Single-update listeners release their mapping when `onLocationChanged` fires (even if client code throws). Duplicated simultaneous registration of the same listener in mixed modes still requires device testing.
- **Geocoder API 33+:** runtime guard for async `getFromLocation(double,double,int,Geocoder.GeocodeListener)`. Wrap original callback without changing request arguments or dispatch timing. `onError` is passed through unmodified, as are empty `onGeocode` result lists. Only a non-empty success for the configured source point can be replaced; if the Profile changes before callback, leave the result untouched. Unsupported versions retain the existing synchronous compatibility path.
- **System callbacks:** leave null, empty or unrelated list arguments unchanged in `SystemLocationRewriter`; skip rewriting when profile inactive. Strict broker-global behavior remains disabled by G02.
- **No impact on VPN encryption, proxy routing or third-party account controls.**

## Tests and limits
- `LocationCallbackPolicyTest`: missing/null value never calls the replacement, non-null replacement, empty/nonempty batch, original batch immutability, empty/error geocoder outcomes.
- `LocationListenerRegistryTest`: identity isolation even if `equals` matches, reuse, successful removal and stale one-shot cleanup.
- CI: `:app:testDebugUnitTest`, `:app:assembleBenchmark :benchmark:assembleBenchmark`, `:app:assembleDebug`, APK upload.
- Android 17 / HyperOS instrumentation with actual `LocationManager`, cancellation timing, asynchronous `Geocoder`, SDK adapters, and shared UID applications: **NOT RUN** pending G08/G09. The tests do not establish zero leakage or service compatibility.
- Existing *synchronous* Geocoder returns a synthetic configured address for a matching valid request; this behavior is unchanged in G04. It is not evidence that a backend geocoder produced a successful lookup. New async behavior preserves backend errors and empty responses.

## Regression matrix for owned-device Probe (G08/G09)
| Case | Expected |
|---|---|
| Missing cached location | `null` unchanged |
| Cancelled/timed-out current location | `null` or no callback, per Android API |
| One-shot callback | One delivery, unregister mapping freed |
| Continuous updates | Rewritten copies in target; other apps unaffected in normal mode |
| `removeUpdates` | Same wrapper passed, no later callbacks |
| Registration/remove exception | No stale first-registration proxy; retry removal possible |
| Empty Google `LocationResult` | Empty unchanged, no synthetic location |
| Async Geocoder `onError` / `onGeocode(emptyList())` | Original callback semantics |
| Async Geocoder successful nonempty | Matching Profile only; callback exactly once |
| Runtime Profile toggle | Disable returns original values; still properly unregisters earlier wrappers |
