# Scam Monitor

A personal Android security assistant that reads the notifications Android exposes to it, scores them locally for scam indicators (with a focus on Ghanaian mobile-money fraud patterns), and posts a warning explaining *why* a message looks risky. It is a risk assessor, **not an antivirus and not a guarantee**.

- Native Kotlin, Jetpack Compose (Material 3), Room, DataStore, coroutines/Flow.
- Package `com.shariftechnologies.scammonitor`, minSdk 26 (Android 8.0), compile/target SDK 35.
- Fully offline: the app declares **no INTERNET permission**. Nothing leaves the device.
- Version: single source of truth is [`version.properties`](version.properties) (currently `1.0.0`; `versionCode = MAJOR*10000 + MINOR*100 + PATCH`).

## Project layout

| Module | Contents |
|---|---|
| `engine/` | Pure Kotlin/JVM: rule parser + scoring engine, URL extraction, domain reputation, queue dispatcher and scan processor. Fully unit-tested without Android. |
| `app/` | Android app: listener service, notification extraction, Room database, warning notifications, Compose UI, bundled JSON rules (`app/src/main/assets`). |
| `testsender/` | Tiny helper app that posts synthetic scam/normal notifications so the real listener path can be tested. |
| `docs/` | Architecture, rule schema, test plan, releasing. |

## Download (GitHub Releases)

Each tagged release is published permanently at the repository's **Releases** page with the APK(s) and `SHA256SUMS.txt`. See [docs/RELEASING.md](docs/RELEASING.md) for how versions are produced and how signing works.

## Build locally

Requires JDK 17+ and the Android SDK (API 35).

```bash
./gradlew :app:assembleDebug :testsender:assembleDebug   # APKs: app/build/outputs/apk/debug/app-debug.apk
./gradlew :engine:test :app:testDebugUnitTest            # unit tests
./gradlew :app:lintDebug                                  # lint
./gradlew :app:connectedDebugAndroidTest                  # needs an emulator/device
./gradlew -PengineOnly=true :engine:test                  # engine only, no Android SDK needed
```

## Install on a phone

```bash
adb devices                                  # phone with USB debugging enabled must be listed
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r testsender/build/outputs/apk/debug/testsender-debug.apk   # optional test helper
```

## First run and real-device test

1. Open **Scam Monitor** and read the onboarding screen.
2. Tap **Open notification access settings** and switch **Scam Monitor** on. Opening Settings does not grant anything; you must toggle it.
   - *Greyed-out toggle (Android 13+, sideloaded APK):* tap **Open app info (restricted settings)**, open the ⋮ menu, choose **Allow restricted settings**, then return and enable access.
3. Return to the app and tap **Recheck access**. The dashboard shows "enabled" and, once Android binds the service, "Listener service: connected".
4. Android 13+: allow the app's *own* notifications when prompted (or via the dashboard button). This is separate from notification access: one lets the app read other apps' notifications, the other lets it show its own warnings.
5. Open **Scam Monitor Test Sender** and tap **PIN request**. Within a second you should get a *High-risk message detected* warning; the item appears under **Flagged**, and the scan under **Log**.
6. Tap **Normal transaction** and **Legit OTP notice**: they appear in the Log as low risk, with no warning.
7. Tap **Burst: 12 notifications at once**: all are processed without UI lag; repeated identical findings are warning-suppressed.
8. Turn on airplane mode and repeat step 5: detection is identical (no network is involved).

Without the helper app: `scripts/post-test-notifications.sh` posts via `adb shell cmd notification post` (best effort; options vary by Android version). The full checklist is in [docs/TEST_PLAN.md](docs/TEST_PLAN.md).

## Permissions

| Permission / declaration | Why |
|---|---|
| `BIND_NOTIFICATION_LISTENER_SERVICE` (on the `<service>`) | Lets only the Android system bind the listener. It is *not* a runtime permission. The user grants **notification access** in system Settings. |
| `POST_NOTIFICATIONS` | Android 13+ runtime permission to show this app's own warnings. |
| `<queries>` for launcher apps | Android 11+ package visibility, so source app names can be shown. Deliberately **not** `QUERY_ALL_PACKAGES`. |

Not requested: internet, accessibility, SMS, contacts, call log, overlay (`SYSTEM_ALERT_WINDOW`), foreground service.

## Privacy

- Analysis is on-device. No analytics, accounts, servers or AI services.
- Ordinary scans store only: time, source package, score, severity, rule ids, suspicious domains. **No message text.**
- Flagged items additionally store explanations and short evidence excerpts with digit runs (PINs, OTPs, numbers) masked.
- Retention: scans 30 days (max 5,000 rows), flagged items 180 days; **Settings → Clear scan history** deletes everything at once.
- Data lives in app-private storage, is excluded from cloud backup/device transfer (`allowBackup=false`, extraction rules), and is **not encrypted by the app**. Someone with access to the unlocked device or a rooted/debuggable environment may be able to read it.
- Logcat contains only exception class names, never notification content.
- Warning notifications use private lock-screen visibility with a generic public version.

## Limitations

- Only notification content Android exposes to a listener can be analysed. Apps or lock-screen settings that hide content, empty notifications, and apps that never post notifications are invisible to it.
- Android, OEM battery managers, force-stop, or the user can stop the listener at any time. The app requests a rebind when disconnected but cannot guarantee uninterrupted monitoring; the dashboard shows the live connection state.
- Detection is rule-based and heuristic. Expect misses (novel wording, other languages) and false positives. The rules are English-centric with Ghanaian terms (MoMo, GH₵, cedis).
- The local domain blocklist is a small synthetic starter set (reserved `.example` domains). A local list goes stale unless deliberately updated. No online reputation lookup exists in v1; `ReputationProvider` is the extension point (e.g. Safe Browsing later).
- Brand/lookalike detection uses a short editable brand list and an approximate registrable-domain calculation (no full public-suffix list).
- Under the queue's bounded capacity, an extreme burst drops the *oldest* waiting notifications (counted and shown in Settings → diagnostics).
- UI strings are English only.
- Installed-app permission auditing is **not implemented**; `audit/InstalledAppAuditor.kt` documents the future boundary only.

## Verification status of this repository snapshot

The development container used to create this code could not reach Google's Maven repository, so the Android SDK/AGP/AndroidX were unavailable there. What was actually verified:

- `:engine` built and its **45 unit tests passed** (rules, scoring, URL/domain analysis, parser validation, queue/backpressure/dedup, processor warning policy).
- The framework-only Android sources (extractor, access manager, listener service, warning manager, test sender) were compiled against the Android 15 framework API.
- **Not yet built or run:** the full `:app` module (Compose UI, Room, DataStore, resources, manifest merge), app unit tests, lint, instrumented tests, and any on-device testing. The CI workflow performs the first real `assembleDebug`; treat any failure there as a bug to fix before relying on a release.
