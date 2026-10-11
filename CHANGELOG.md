# Changelog

Versions follow `version.properties`; each entry corresponds to a `vX.Y.Z` git tag and GitHub Release.

## 1.0.0 - 2026-10-11

- Notification listener that scores accessible notifications locally for scam indicators.
- 10 editable starter rules (Ghanaian mobile-money focus), rule combinations, URL/domain reputation with a local synthetic blocklist.
- Warning notifications with deduplication and rate limiting; Room history with retention; Compose UI (dashboard, flagged items, security log, settings, onboarding).
- Test Sender helper app for reproducible real-listener testing.
- GitHub Actions: CI, manual instrumented tests, tag-driven releases.
- Known limitations: see README. Not yet verified on a physical device.
