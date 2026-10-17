# GeoShift agent execution rules

This file applies to any assistant or coding agent operating on this repository.

1. Before editing or planning, read `docs/GEOSHIFT_V0.5_PLAN.md` and `docs/GEOSHIFT_V0.5_EXECUTION_LOG.md`, then inspect the actual current code and branch. These files are the authoritative execution ledger.
2. Work through the highest-priority unblocked task by ID; never mark a task DONE without logged test evidence. `IMPLEMENTED_UNVERIFIED` and `NOT RUN` are valid states.
3. Keep VPN encryption, tunneling, proxy configuration, account-restriction evasion, attestation bypass, anti-cheat, and platform risk-control bypass out of scope. Existing Follow-VPN GeoIP-to-profile syncing remains in scope.
4. Prefer user-authorized per-app behavior, Android native permissions, fail-safe system behavior, explicit warnings, reversible changes, no sensitive data in logs, and tests with owned Probe apps.
5. Preserve existing tests, record exact commit SHA and runnable commands, update the plan and log after each development round.
6. Never automatically enable provider-global strict rewriting. Do not claim zero-leak protection or production readiness without real device evidence.
7. Use `feat/v0.5-privacy-reliability` for v0.5 development; do not silently merge into `main`.
