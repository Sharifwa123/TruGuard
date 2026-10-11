# Architecture

```
Android notification shade
        │  (system binds, permission BIND_NOTIFICATION_LISTENER_SERVICE)
        ▼
ScamNotificationListenerService          app/monitoring   – thin; try/catch; skips own package
        │ NotificationContentExtractor      reads only title/text/bigText/lines/messages/summary
        ▼ NotificationEvent (key, eventId, fingerprint, ScanInput)
NotificationEventDispatcher              engine/…/pipeline – submit() never blocks
   bounded Channel(64, DROP_OLDEST) → 2 fixed workers, per-event timeout
   dedupe by (key, content fingerprint) for 30 min; forget(key) on removal
        ▼
NotificationScanProcessor
   ScamDetectionEngine.analyze()         engine/…/domain/engine – pure, offline, time-limited regex
        ├─ RuleParser → CompiledRuleSet  (scam_rules.json, validated, fallback if invalid)
        ├─ UrlExtractor + DomainReputationChecker (ReputationProvider list; local blocklist in v1)
        ▼ DetectionResult (score, severity, matches, explanations, domains, evidence)
   ScanSink  = ScanRepository → Room (scans + flagged_items, one transaction, unique eventId)
   WarningSink = WarningNotificationManager (severity ≥ user minimum, repeat suppression, rate cap)
        ▼
Compose UI (Dashboard · Flagged · Log · Settings · Onboarding) ← ViewModels ← repositories/Flows
```

## Key decisions

- **`:engine` is a plain JVM module.** All logic that does not need Android (scoring, URL analysis, dispatcher, processor policy) is testable in milliseconds and in CI without an SDK. The app module only adapts Android types.
- **Application-scoped pipeline.** `AppContainer` owns the dispatcher and scope, so queued work survives the listener service being unbound/rebound. The service only submits events and reports connection state.
- **Backpressure:** bounded queue, drop-oldest, counted. No coroutine per event.
- **Duplicate protection in three layers:** dispatcher fingerprint dedupe → `eventId` unique index in Room (`INSERT IGNORE`) → processor warns only when the insert was new.
- **Single source of truth for rules:** bundled `scam_rules.json` defines rules; DataStore stores only the ids the user disabled.
- **Warnings are plain notifications.** No overlays, no background activity launches, no actions that modify data.
- **Dependency wiring by hand** (`AppContainer`), no DI framework.
- WorkManager is intentionally absent: retention pruning runs at app start/listener connect, and nothing else needs scheduling yet. The future permission audit may add it.

## Scoring

`score = min(100, Σ matched rule scores + Σ matched combination scores + link signals)`.
Thresholds (in `scam_rules.json`): INFO < 15 ≤ LOW < 35 ≤ MEDIUM < 60 ≤ HIGH. Results at/above `flagMinSeverity` (LOW) are *flagged* and stored as flagged items; warnings default to MEDIUM and above (user-configurable LOW/MEDIUM/HIGH).

Link signals: `signal.blocked_domain` (60, local blocklist hit → HIGH on its own) and `signal.suspicious_link` (20; IP host, shortener, punycode, `user@host` trick, brand name in an unrelated domain, brand look-alike, long subdomain chain). Combinations add score when signals co-occur (e.g. urgency + suspicious link).

## Package map (`com.shariftechnologies.scammonitor`)

`app` (application, container, activity) · `core` (text sanitising, hashing; in `:engine`) · `data` (Room, DataStore, assets, repositories) · `domain` (models, rules, engine, reputation; in `:engine`) · `monitoring` (service, extractor, access manager; pipeline in `:engine`) · `notifications` · `ui` · `audit` (future boundary, interface only).
