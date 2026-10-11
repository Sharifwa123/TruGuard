# Changelog

Versions follow `version.properties`; each entry corresponds to a `vX.Y.Z` git tag and GitHub Release.

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
