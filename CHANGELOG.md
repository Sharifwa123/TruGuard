# Changelog

Versions follow `version.properties`; each entry corresponds to a `vX.Y.Z` git tag and GitHub Release.

## 1.5.0 - 2026-10-12

- **Intent detection that does not depend on exact words.** A small learned model (hashed word and character n-grams, trained on 1,600 example messages) scores how a message is built and what it asks for, so reworded, misspelled, shorthand or code-mixed scams that no phrase list anticipated can still be caught. It adds to the rules. On a separate blind test of 319 adversarially worded messages, the rules alone flagged 21% of scams and rules plus the model flag 85% (67% at MEDIUM and above), with false alarms on ordinary messages at 7.5% (2.5% at MEDIUM and above) on a test deliberately full of look-alikes. Training data is model-written, not real victims' messages, so expect real-world results to differ. Details, method and limits: `docs/INTENT_MODEL.md`. New Settings switch "Smart intent detection".
- **Warnings you cannot miss.** Besides the notification, a warning at or above your chosen level (default MEDIUM) now shows a **pop-up card over whatever app is open** (needs the Accessibility service on, or "Display over other apps" allowed), with **strong vibration** (long repeated pulses for high risk) and an **optional alert tone** (short beep on the alarm volume). Settings: pop-up, strong vibration and tone switches, the level they start at, an "Allow pop-ups" shortcut and a **Test alert** button. The pop-up never appears on the lock screen (the lock-screen privacy setting applies to the notification), a high-risk card shakes and needs a button press, a medium one fades after 12 seconds. Vibration and tone use the alarm audio usage so they still work on silent.
- Adds the `VIBRATE` and `SYSTEM_ALERT_WINDOW` permissions.

## 1.4.0 - 2026-10-12

- **New interface: glass, clay and soft shadows.** A drifting aurora backdrop under frosted-glass cards, puffy clay buttons and switches that spring when pressed, soft-shadow stat tiles that press in, a floating glass tab bar whose selected tab grows into a glowing pill, expandable cards, haptic ticks and animated screen transitions. Light and dark themes. No new libraries (shadows are drawn, not blurred, so it looks the same on every Android version).
  - Dashboard rebuilt around a protection status orb, three tappable stat tiles (flagged, scanned, apps watched), a screen-monitor card with a live on/off switch, and a short recent list. The privacy paragraph and most explanatory text are gone.
  - Flagged, Log and Security show one glass card per item; details open in place with a tap instead of dialogs. Security opens with a health summary.
  - Settings is grouped into cards (access, warnings, screen monitor, rules, data). Each monitored app can be stopped from there with one tap.
  - Onboarding is three short points and one button.
- **Fixed (from device screenshots):**
  - The 1.3.0 bottom bar was clipped by its fixed height and its label was unreadable; it is replaced.
  - Setting descriptions were cut off mid-sentence; the copy is shorter and never truncated.
  - The chat shortcut offered "Only this chat: Yesterday". Day separators, times, unread counters and similar chrome are no longer taken for chat names.
  - The shield panel could open off-screen near the bottom; it is now placed inside the screen, closes when you tap elsewhere, and the shield glides to the nearest screen edge after a drag.
  - Two shields could appear: Android's own faded accessibility button uses the same shield icon as the app. TruGuard's accessibility service now has its own eye icon, Settings explains how to hide Android's button, and a service reconnect can no longer leave a second TruGuard shield behind.
  - Warnings are shorter (reason in one line) and quote the masked text that triggered an on-screen warning, so you can tell which message it was about.

## 1.3.0 - 2026-10-12

- **Shield is now one tap.** Tapping the floating shield over an app that is not monitored switches monitoring on for it and says so ("TruGuard is now monitoring WhatsApp"). The shield turns green while the app in front is monitored; tapping it then opens the panel (only this chat / website, resume, settings, the "Monitor <app>" switch to turn it off).
- **Much denser interface**, from a review of device screenshots where text and spacing were too large:
  - Compact typography (13sp body, 12sp captions) and font scaling capped at 110% so layouts stay compact on phones set to a large font.
  - Bottom bar is 60dp tall and shows a label only for the selected tab.
  - Dashboard: removed the title row, shortened status text, removed the privacy paragraph (it is in Settings).
  - Security: app rows are one line (details on tap), shorter intro, footer folded into it, finding consequences one line until opened.
  - Settings: shorter copy, single-line rule descriptions, 44dp rows.
  - Flagged and Log lists: tighter row padding.

## 1.2.3 - 2026-10-12

- **Fixed the floating shield only appearing to switch things on and off.** Opening the shield's panel made Android report a window change from TruGuard's own overlay, which was mistaken for "no app in front" and closed the panel and hid the shield straight away. Events from the overlay's own windows are now ignored, and an open panel stays open while the same app is in front.
- The panel now leads with a switch, "Monitor <app>", for the app in front (the chat / website options and Settings stay below it).

## 1.2.2 - 2026-10-11

- Fixed the bottom navigation label "Dashboard" wrapping onto two lines on narrow or large-font screens (labels are now one line, small style).

## 1.2.1 - 2026-10-11

- **Renamed the app to TruGuard** (launcher name, screens, notifications, Accessibility/notification-access labels, release asset names `TruGuard-<version>.apk`). The package id `com.shariftechnologies.scammonitor` is unchanged so existing installs update in place. Docs now suggest the key alias `truguard`.

## 1.2.0 - 2026-10-11

- **Optional, opt-in screen monitoring** for conversations that never produce a notification (Instagram, LinkedIn, Snapchat, Facebook, Meet chat, forums, web pages). Uses an Accessibility service that does nothing until the user agrees to a disclosure and switches it on. Nothing is monitored until the user picks it with the floating shield shortcut: the whole app, only one chat / group / broadcast list / number, or in a browser only one website. Fails closed when the open chat or site cannot be identified. Never reads password fields, typed text, keyboards, system screens, password managers or banking/money apps. Screen text is never stored; only flagged findings are kept (masked, labelled "(on screen)"). Persistent notification while active; pause and "stop all" in Settings.
- Settings gains a Screen monitoring section (set-up, pause, shortcut switch, stop all). No separate app-picker screen.

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
