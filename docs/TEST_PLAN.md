# Test plan

## Automated (run in CI)
| Suite | Where | Covers |
|---|---|---|
| Engine unit tests (45) | `engine/src/test` | PIN request, reversal + payment, block threat + lookalike link, reward + advance fee, urgency + synthetic blocked domain, normal transaction, legit OTP notice, plain account notice, malformed URLs, deceptive hostnames, official domains, evidence masking, pathological input, missing/invalid rule data, invalid individual rules, URL parsing (IDN, userinfo, ports, IP), content assembly, rapid bursts, duplicate updates, changed content, own-package exclusion, empty events, queue saturation, processor exceptions, stopped dispatcher, warning dedupe, rate cap, HIGH exemption, user thresholds |
| App unit tests | `app/src/test` | Listener-access setting parsing |
| Instrumented | `app/src/androidTest` (manual workflow `instrumented.yml`) | Room insert/retrieve, duplicate event, reopen database, clear all, evidence masking; warning notification on the right channel with a private lock-screen version; onboarding UI |

Room migrations: schema version 1 has none. When the schema changes, add a `Migration`, commit the exported schema JSON from `app/schemas`, and add a `MigrationTestHelper` test.

## Manual, on a physical device (cannot be automated here)
1. Install both APKs; enable notification access (and "Allow restricted settings" if needed); allow own notifications on Android 13+.
2. Dashboard shows access *enabled* and service *connected* (disable access → status flips after returning to the app).
3. Test Sender → each scenario; compare with the expectation printed on the button.
4. Flagged item detail shows explanations, masked excerpts, suspicious domains (never opened).
5. Log filters (severity, flagged only) and scan detail dialog.
6. Burst button: UI stays responsive; Settings → diagnostics shows processed/skipped counts.
7. Lock the phone and trigger a warning: lock screen shows only the generic text.
8. Airplane mode: same results.
9. Settings: toggle a rule off, resend its scenario, confirm it no longer contributes; clear history; deny notification permission and confirm warnings are suppressed but scans still recorded.
10. Reboot the phone: listener reconnects without reopening the app (OEM battery managers may prevent this; note the device).
11. Test on Android 8, 12, 13+ if available.

## Known gaps
No on-device run has been performed yet (see README → Verification status). Dedicated UI navigation tests are not automated.
