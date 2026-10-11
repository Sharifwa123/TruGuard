# Test plan

## Automated (run in CI)
| Suite | Where | Covers |
|---|---|---|
| Engine unit tests (73) | `engine/src/test` | PIN request, reversal + payment, block threat + lookalike link, reward + advance fee, urgency + synthetic blocked domain, normal transaction, legit OTP notice, plain account notice, malformed URLs, deceptive hostnames, official domains, evidence masking, pathological input, missing/invalid rule data, invalid individual rules, URL parsing (IDN, userinfo, ports, IP), fuzzy wording (typos, shorthand, leetspeak, spaced/stretched letters, homoglyphs, negation) plus an everyday-message corpus that must not flag, content assembly, rapid bursts, duplicate updates, changed content, own-package exclusion, empty events, queue saturation, processor exceptions, stopped dispatcher, warning dedupe, rate cap, HIGH exemption, user thresholds |
| App unit tests | `app/src/test` | Listener-access setting parsing |
| Instrumented | `app/src/androidTest` (manual workflow `instrumented.yml`) | Room insert/retrieve, duplicate event, reopen database, clear all, evidence masking; warning notifications on the right channel per severity, sender in the text, lock-screen visibility setting with a generic fallback; onboarding UI |

Room migrations: schema version 1 has none. When the schema changes, add a `Migration`, commit the exported schema JSON from `app/schemas`, and add a `MigrationTestHelper` test.

## Screen monitoring
Engine tests cover config edits and persistence, scope matching (names, groups, broadcast lists, phone numbers across formats, websites and subdomains vs look-alikes, fail-closed cases), the analyzer (header title and host vs message text, editable text excluded), the never-monitor list, new-text tracking, a scam split across chat bubbles, and that ordinary screen text leaves no record.

On a device: complete set-up in Settings (disclosure → Accessibility → "Allow restricted settings" if needed); open WhatsApp, tap the floating shield, choose "Only this chat"; have a synthetic scam text arrive in that chat and confirm a warning labelled "(on screen)"; open a different chat and confirm nothing is read; in a browser choose "Only this website" and confirm the same; confirm the status notification shows while active, that Pause stops reading immediately, and that password fields, the keyboard and finance apps are never read (no shield appears in them).

## Phone-security checks
Engine tests cover app scoring (store chat app stays low; sideloaded app with SMS + notification + accessibility + overlay is high with the expected combinations; ungranted vs just-installed permissions; system apps skipped; unreadable table), protection findings (Play Protect off/unknown, no screen lock, USB debugging, old patch, powerful apps with labels) and change detection (only weakening changes, only newly added apps, none on first run, JSON round trip).

On a device: open the Security tab and compare with Settings; sideload the Test Sender or any APK from outside Play and confirm an alert arrives right after install if it looks risky (the Test Sender requests only notifications, so expect a mild "not from a store" rating at most); turn developer options on and confirm the medium alert within seconds (and off again); on a phone with an unlocked bootloader confirm the high-severity finding, on a locked one confirm no bootloader finding; turn USB debugging on/off and confirm an alert within seconds; turn Play Protect off in the Play Store and confirm the alert or the "could not be checked" note; enable/disable an Accessibility app and confirm the alert; deny notifications and confirm nothing crashes.

## Manual, on a physical device (cannot be automated here)
1. Install both APKs; enable notification access (and "Allow restricted settings" if needed); allow own notifications on Android 13+.
2. Dashboard shows access *enabled* and service *connected* (disable access → status flips after returning to the app).
3. Test Sender → each scenario; compare with the expectation printed on the button.
4. Flagged item detail shows explanations, masked excerpts, suspicious domains (never opened).
5. Log filters (severity, flagged only) and scan detail dialog.
6. Burst button: UI stays responsive; Settings → diagnostics shows processed/skipped counts.
7. Lock the phone and trigger a warning: by default the lock screen shows the app, sender and reason. Turn off Settings → "Show warning text on the lock screen" and repeat: only the generic "Possible scam detected" text shows. Without opening Scam Monitor, confirm you can tell which message is risky and why.
8. Airplane mode: same results.
9. Settings: toggle a rule off, resend its scenario, confirm it no longer contributes; clear history; deny notification permission and confirm warnings are suppressed but scans still recorded.
10. Reboot the phone: listener reconnects without reopening the app (OEM battery managers may prevent this; note the device).
11. Test on Android 8, 12, 13+ if available.

## Known gaps
No on-device run has been performed yet (see README → Verification status). Dedicated UI navigation tests are not automated.
