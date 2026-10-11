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

## Concepts (wording-tolerant matching)
Regexes need the wording to be guessed in advance. A rule can instead describe *ideas* that must appear close together, in any order:

```jsonc
"negators": ["not", "never", "dont", "dnt", "no"],
"concepts": {
  "disclose_verb": { "terms": ["send", "forward", "share", "give", "snd", "frwd"], "negatable": true },
  "delivered":     { "terms": ["sent", "received", "got", "comes"] },
  "code_noun":     { "terms": ["code", "cod", "cde", "digit"] },
  "to_me":         { "terms": ["me", "us", "back"] },
  "phone_number":  { "terms": ["re:^(?:0\\d{9}|233\\d{9})$"] }   // "re:" = regex tested on one raw word
},
"rules": [{ "id": "code_forwarding", ...,
  "conceptPatterns": [
    { "all": ["delivered", "code_noun", "disclose_verb", "to_me"], "window": 14, "ordered": false }
  ]}]
```
- A pattern matches when all listed concepts occur within `window` consecutive words (`ordered: true` also requires the listed order). A rule fires if any regex pattern **or** any concept pattern matches.
- Each word is matched to a concept even when misspelled or disguised: one typo for words of 5+ letters, two for 8+, only insertions/deletions/swaps for 4-letter words (so "sent" is not "send" and "mode" is not "code"), 3-letter words must be exact except for stretched letters ("paay"). Leetspeak (`c0de`, `sh@re`), separators (`o.t.p`), spaced letters (`p i n`, only when the result is a listed word), stretched letters (`pleeease`) and simple suffixes (`-ing`, `-ed`, `-s`) are handled automatically.
- `negatable: true` ignores an occurrence that has a negator within the 3 preceding words ("never share your PIN"). A concept built from the negators themselves (see `secrecy_pressure`) lets a rule require the negation instead.
- Add shorthand and local spellings (`snd`, `2me`, `pls`) as plain terms. Prefer specific concepts: a word listed in a broad concept makes every rule using it noisier. Always add benign sentences to `FuzzyDetectionTest` (the everyday-message corpus) when widening a concept.
- Limits: 80 concepts, 120 terms each, 6 concepts per pattern, window 1..30; unknown concepts or bad regex terms skip that pattern with a warning.
- A de-obfuscated copy of the text (accents stripped, look-alike letters mapped, `c0de`→`code`, stretched letters collapsed) is also tried against the regex patterns.

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

## App permission risk table (`permission_risks.json`)
`permissions[]` (`name` without the `android.permission.` prefix, `label`, `harm` in plain language, `weight` 1..100, `kind`: `runtime` counts when granted, `special` counts when requested), `signals[]` (flags the scanner raises: `flag.accessibility_enabled`, `flag.notification_listener_enabled`, `flag.device_admin`, `flag.sideloaded`, `flag.no_launcher_icon`), `combinations[]` (`requiresAll` / `requiresAny` over permission names and flags, `score`, `harm`), `trustedInstallers` (stores whose apps get `trustedInstallerDiscountPercent` off permission weight) and `thresholds`. An unreadable table disables app ratings but nothing else. Keep `harm` texts concrete and honest: they are shown to the user as what the permission *can* cause, not as proof of intent.

## Blocklist and heuristics files
- `domain_blocklist.json`: `entries[]` with `domain`, `matchSubdomains` (exact-only unless true), `type`, `source`, `reason`, `addedAt`. Matching is by whole labels, never substring. Shipped entries are synthetic `.example` domains. Update deliberately; stale lists miss new scams.
- `domain_heuristics.json`: URL `shorteners`, `multiPartSuffixes` (e.g. `com.gh`), and `brands` (`token` + `officialDomains`). A host containing a brand token but not under an official domain is flagged. Keep official domains complete, or genuine sites will be flagged.
