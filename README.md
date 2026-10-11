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
| `BIND_ACCESSIBILITY_SERVICE` (on the screen-text `<service>`) | Lets only the Android system bind the optional screen-monitoring service. It does nothing until you agree to the in-app disclosure **and** switch it on under Accessibility, and it reads only apps you choose. It declares no gesture, key-event or touch-exploration ability. |
| `QUERY_ALL_PACKAGES` | Lets the security audit list every installed app and its permissions (metadata only, never app data). Google Play restricts this permission and would require a declared justification, so this build is meant to be sideloaded. |
| `<queries>` for launcher apps | Android 11+ package visibility for app names (also covered by the permission above). |

Not requested: internet, SMS, contacts, call log, overlay permission (`SYSTEM_ALERT_WINDOW`; the floating shortcut is an accessibility overlay), foreground service.

## Screen monitoring (optional, off by default)

Notifications often hide their content, and many conversations (Instagram, LinkedIn, Snapchat, Facebook, Meet chat, forums, web pages) never produce one. With your explicit consent, Scam Monitor can read the text visible in **apps you choose** and run the same scam checks on it, in real time.

- **Set up:** Settings → Screen monitoring → read the disclosure → turn on “Scam Monitor” under Android's Accessibility settings (sideloaded: App info › ⋮ › Allow restricted settings first).
- **Choose what to monitor with the floating shield:** open an app (say WhatsApp), tap the shield, and pick **the whole app**, **only this chat / group / broadcast list / number**, or in a browser **only this website**. Stop the same way, or use “Stop monitoring all apps” in Settings. Nothing is monitored until you pick it.
- **Scope rules:** a chat- or site-restricted app is read only while a chosen chat (matched by the name or number shown in the header) or website (matched by host in the address bar) is open; if it cannot tell, it reads nothing.
- **Never read, whatever you choose:** password fields, text you are typing, the address bar's text as a message, keyboards, system screens, password managers, and banking or money apps.
- **Privacy:** screen text is analysed in memory and never stored. Only a flagged finding is kept, as a short excerpt with digits masked, labelled “(on screen)”. A persistent notification shows while it is active, and you can pause it in Settings. It cannot tap, type or change anything.
- **Limits:** it reads what apps expose as text. It cannot see text inside images or stickers, or voice and video calls (Meet captions only if you turn them on). Chat and tab detection is heuristic (a header title or address bar), so unusual layouts may not be recognised, in which case a restricted app is simply not read. Some apps block or hide their content from accessibility. Use it only on your own phone.

## Phone security checks

- **Security tab:** the state of Play Protect (when readable), screen lock, encryption, USB debugging, developer options and security-patch age; apps with Accessibility access, notification access or device-admin rights; and a list of installed apps worth a closer look, each with plain-language explanations of what its permissions can cause and what to do.
- **Alerts** (Settings → "Alert me about Play Protect and risky apps"): when Play Protect is turned off, the screen lock is removed, USB debugging or developer options are turned on, or an app gains Accessibility, notification-reading or device-admin power; and right after a new app is installed if what it asks for looks dangerous. Settings changes are noticed as they happen while the app process is alive, plus a daily background check.
- Rules for scoring permissions live in `app/src/main/assets/permission_risks.json` (editable; see docs/RULE_SCHEMA.md).

## Privacy

- Analysis is on-device. No analytics, accounts, servers or AI services.
- Ordinary scans store only: time, source package, score, severity, rule ids, suspicious domains. **No message text.**
- Flagged items additionally store explanations and short evidence excerpts with digit runs (PINs, OTPs, numbers) masked.
- Retention: scans 30 days (max 5,000 rows), flagged items 180 days; **Settings → Clear scan history** deletes everything at once.
- Data lives in app-private storage, is excluded from cloud backup/device transfer (`allowBackup=false`, extraction rules), and is **not encrypted by the app**. Someone with access to the unlocked device or a rooted/debuggable environment may be able to read it.
- Logcat contains only exception class names, never notification content.
- Warning notifications show the app, sender, reason and a safety reminder so you need not open the app. They appear on the lock screen by default; **Settings → Show warning text on the lock screen** switches to a generic "Possible scam detected" notice instead. Warning text never includes the message body, only the reason.

## Limitations

- Only notification content Android exposes to a listener can be analysed. Apps or lock-screen settings that hide content, empty notifications, and apps that never post notifications are invisible to it.
- Android, OEM battery managers, force-stop, or the user can stop the listener at any time. The app requests a rebind when disconnected but cannot guarantee uninterrupted monitoring; the dashboard shows the live connection state.
- Detection is rule-based and heuristic, but not tied to exact phrases: a concept layer tolerates misspellings, shorthand, leetspeak, spacing and reordering. It is still a lexicon (no machine-learning model, no understanding of meaning), so entirely new wording, other languages and scams without the usual signals can be missed, and some genuine messages may be flagged. The rules are English-centric with Ghanaian terms (MoMo, GH₵, cedis).
- The local domain blocklist is a small synthetic starter set (reserved `.example` domains). A local list goes stale unless deliberately updated. No online reputation lookup exists in v1; `ReputationProvider` is the extension point (e.g. Safe Browsing later).
- Brand/lookalike detection uses a short editable brand list and an approximate registrable-domain calculation (no full public-suffix list).
- Under the queue's bounded capacity, an extreme burst drops the *oldest* waiting notifications (counted and shown in Settings → diagnostics).
- UI strings are English only.
- Phone-security checks (Security tab): installed apps are rated from their declared permissions, install source and special accesses, and the protection state is read from settings Android exposes. Bootloader and developer options: the app reports whether the bootloader is **currently unlocked** (and whether the phone runs an unofficial build) and alerts right after developer options are switched on, explaining the link to USB debugging, "OEM unlocking" and scammer instructions. Unlocking the bootloader itself happens with the phone powered off and **erases the phone, so no app can see or warn about the act**; the state is read from a system property that some phones block, in which case it is unknown. Limits: Android gives apps **no way to block or pause an install, or to see a settings change before it happens**, so alerts arrive immediately *after* (a new app is checked before it has run, and you can uninstall it). The Play Protect switch lives in Google's own storage and **cannot be read reliably on every phone**: it may show as "could not be checked". Detecting someone *trying* to turn it off would need an Accessibility service reading system settings screens; the optional screen monitoring deliberately never reads system screens, so this is not done. Ratings are heuristic: many genuine apps ask for powerful permissions.

## Verification status

- **Verified in CI (GitHub Actions, on every push and PR):** `:engine` unit tests (73 passing); full Android build of `:app` and `:testsender` (Kotlin/Compose compile, Room KSP, resource and manifest merge); app unit tests; lint; debug APKs produced and uploaded as workflow artifacts.
- **Not yet verified:** the instrumented tests (`instrumented.yml`, manual) and the whole on-device checklist in [docs/TEST_PLAN.md](docs/TEST_PLAN.md): the real listener receiving notifications, warning display, lock-screen privacy, reconnect after reboot, OEM battery behaviour. No physical-device test has been performed.
- The `release.yml` workflow has not yet been exercised (no tag has been pushed), and no signing secrets are configured yet.
