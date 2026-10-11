# Rule schema and safe editing

Rules live in `app/src/main/assets/scam_rules.json` (schema version 1). They are loaded by `RuleParser`, validated, and compiled once.

```jsonc
{
  "schemaVersion": 1,
  "rulesVersion": "2026.10.1",
  "thresholds": { "low": 15, "medium": 35, "high": 60, "flagMinSeverity": "LOW" },
  "linkSignals": { "blocklistedDomain": 60, "suspiciousLink": 20 },
  "rules": [{
    "id": "pin_otp_request",          // [a-z][a-z0-9_.]{1,63}, unique, stable
    "description": "…",
    "category": "credential_request",
    "enabled": true,
    "score": 60,                       // 1..100, added once per scan when the rule matches
    "patterns": ["regex", "…"],        // 1..20 regexes, each ≤ 400 chars
    "matchMode": "ANY",                // ANY (default) or ALL patterns must match
    "explanation": "Shown to the user."
  }],
  "combinations": [{
    "id": "combo.urgency_suspicious_link",
    "requiresAll": ["urgency", "signal.suspicious_link"],   // rule ids or signal.* ids
    "requiresAny": [],                                      // optional: at least one more
    "score": 20,
    "explanation": "Shown to the user."
  }]
}
```

## How matching works
- Text = notification title + body, cleaned of control/zero-width characters, NFKC-normalised, lower-cased, whitespace collapsed. **Write patterns in lower case.**
- Patterns are Java regular expressions, run under a 250 ms per-scan budget (a runaway regex is aborted and only that rule skipped).
- Evidence excerpts mask runs of 4+ digits.

## Validation (invalid items are skipped with a warning; an unusable file falls back to a minimal built-in PIN rule)
Unknown schema version, bad thresholds (must satisfy `0 < low < medium < high ≤ 100`), duplicate/ill-formed ids, score outside 1..100, missing explanation, pattern that does not compile, over-long pattern, nested unbounded quantifiers such as `(a+)+`.

## Editing checklist
1. Prefer bounded gaps like `.{0,40}` over `.*`; anchor with `\b`.
2. Add a negative look-behind when a phrase appears in legitimate warnings (see `(?<!not )(?<!never )` in `pin_otp_request`).
3. Use only synthetic examples in tests. Never paste real messages, PINs, numbers or names.
4. Add/adjust cases in `engine/src/test/.../ScamDetectionEngineTest.kt`, including a benign look-alike that must *not* flag.
5. Run `./gradlew -PengineOnly=true :engine:test` (the tests load this exact file and assert it has no warnings).
6. Bump `rulesVersion`.

Users can switch individual rules off in Settings; that preference is stored separately and never edits the JSON.

## Blocklist and heuristics files
- `domain_blocklist.json`: `entries[]` with `domain`, `matchSubdomains` (exact-only unless true), `type`, `source`, `reason`, `addedAt`. Matching is by whole labels, never substring. Shipped entries are synthetic `.example` domains. Update deliberately; stale lists miss new scams.
- `domain_heuristics.json`: URL `shorteners`, `multiPartSuffixes` (e.g. `com.gh`), and `brands` (`token` + `officialDomains`). A host containing a brand token but not under an official domain is flagged. Keep official domains complete, or genuine sites will be flagged.
