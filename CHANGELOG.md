# Changelog

Versions follow `version.properties`; each entry corresponds to a `vX.Y.Z` git tag and GitHub Release.

## 1.1.1 - 2026-10-11

- **Screen space and layout pass (no behavior changes).** Tighter, consistent spacing from shared tokens (`Dimens`); every tappable row at least 48dp tall; edge-to-edge with correct system-bar and keyboard insets.
  - Dashboard: removed the oversized headline, the status card's extra title and the big flagged-count card (now one compact row); the duplicate "Open security log" button became a small "Open log" link next to Recent scans; privacy note shortened.
  - Onboarding: four stacked full-width buttons became one primary action, a row of two secondary actions, and Continue.
  - Settings: related controls grouped; the 11 detection rules collapse behind a header showing how many are on; the single-button "Data" section merged with Privacy; diagnostics condensed; the two notification-access buttons share one row.
  - Security: each finding shows its title and consequence, with the explanation and the fix one tap away; the Check again button moved into the header.
  - Flagged and Log lists: denser, easier-to-scan rows with the score folded into the time line; Flagged detail disclaimer and rule list condensed.

## Unreleased

- Groundwork for opt-in screen monitoring (inert; no Accessibility service exists yet and nothing reads the screen): per-app, per-chat and per-website scope rules, a screen analyzer, a never-monitor list (system screens, keyboards, password managers, finance apps) and storage for the user's choices. Findings from on-screen text will be labelled "(on screen)" and are the only thing stored.

## 1.1.0 - 2026-10-11

- **Phone security checks (new Security tab).** Reads the protection state Android exposes (Play Protect when readable, screen lock, encryption, USB debugging, developer options, security-patch age, apps with Accessibility / notification access / device-admin rights) and rates every installed app from its permissions, install source and special accesses, explaining in plain language what each can cause and what to do.
- **Alerts for security changes and risky installs:** Play Protect turned off, screen lock removed, USB debugging or developer options turned on, a new Accessibility / notification-reading / device-admin app; and right after a new app is installed (before it has run) if it asks for dangerous permissions, especially outside an app store. Android cannot block an install or a settings change in advance, so alerts come immediately afterwards. A daily background check (WorkManager) covers times when the app is not running.
- New permission `QUERY_ALL_PACKAGES` (app metadata only) and editable `permission_risks.json`; 73 engine tests.
- Bootloader state (unlocked / locked / unreadable) and unofficial-build detection; developer options turned on is now a medium alert that explains USB debugging, OEM unlocking and scammer instructions.
- Settings switch for these alerts.

## 1.0.1 - 2026-10-11

- New rule `code_forwarding`: flags messages asking you to forward a code you were just sent (the WhatsApp/SMS account-takeover scam, e.g. "we sent you a code, send it to me so we can add you to the group"). Found on a real device, where such a message was previously scored 0.
- Warnings now tell you what you need without opening the app: the app name, the sender or chat name from the notification, why it was flagged, and a one-line safety reminder. Medium-risk warnings pop up on screen (new pop-up channel); the lock screen shows the warning text by default (Settings → "Show warning text on the lock screen" turns that off).
- Warnings now start at LOW risk by default (previously MEDIUM) so messages that merely might be harmful also get a notice; this is adjustable in Settings. Repeated identical findings and bursts remain rate-limited.
- **Detection no longer depends on exact wording.** A second, concept-based layer reads messages the way people actually write them: misspellings ("pleas", "procesing"), SMS shorthand ("snd it 2 me", "ur", "pls"), leetspeak ("c0de", "sh@re"), separators and spaced letters ("o.t.p", "p i n"), stretched words ("pleeease", "paay"), reordered sentences, and look-alike Cyrillic/Greek letters. Rules can say "a send-word, a code-word and a to-me word close together, in any order" instead of listing phrases, and negations ("never share your PIN") are respected. The lexicon is editable JSON (`concepts`, `conceptPatterns`).
- Rule set version 2026.10.3 (11 rules, 24 concepts); 58 engine tests, including a corpus of everyday messages that must not be flagged.

## 1.0.0 - 2026-10-11

- Notification listener that scores accessible notifications locally for scam indicators.
- 10 editable starter rules (Ghanaian mobile-money focus), rule combinations, URL/domain reputation with a local synthetic blocklist.
- Warning notifications with deduplication and rate limiting; Room history with retention; Compose UI (dashboard, flagged items, security log, settings, onboarding).
- Test Sender helper app for reproducible real-listener testing.
- GitHub Actions: CI, manual instrumented tests, tag-driven releases.
- Known limitations: see README. Not yet verified on a physical device.
