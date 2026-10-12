# Intent model: catching scams no phrase list anticipated

Scammers rephrase, misspell, translate and code-mix, so a list of known phrases (the rules in `scam_rules.json`) can
never be complete, and it also misfires on innocent text that happens to contain the same words. TruGuard therefore
judges messages with a small **learned** model that scores *how a message is built*, and it treats the hand-written
rules only as evidence that the model weighs.

## How it works
- **Features** (`IntentFeatures.kt`, mirrored by `tools/intent/featurize.py`): words, word pairs and character n-grams
  inside words (hashed into 2^18 buckets), a few shape features (length, `?`, `!`, shouting), links and long numbers
  replaced by placeholders, and one extra feature per **rule that matched** (`r:<rule id>`). Because the n-grams are inside
  words, "pls", "plz" and "please" share most features. There is no vocabulary list.
- **Model** (`IntentClassifier.kt`): logistic regression for "is this trying to get money, a secret, access or personal
  data out of the reader?", plus a second head that names the kind of manipulation (secret request, payment request,
  threat/pressure, reward bait, trust/impersonation, access/install). The name selects the plain-language reason.
- **Rules as evidence, not as verdicts.** The trainer sees which rules fired on each of the 3,180 training examples and
  learns how far to trust each one. A sloppy rule (for example the old "account + update + link" pattern that fired on
  "Join our WhatsApp group for updates [LINK]") ends up with little weight; a precise one keeps a lot. The only rule
  output that overrides the model is a domain on the local blocklist, which is a fact rather than a guess.
- **Calibrated severities.** Probability cut-offs for LOW / MEDIUM / HIGH are chosen at training time from out-of-fold
  predictions so that ordinary messages reach them at most 4% / 1.5% / 0.5% of the time. They are stored in the model
  file. The 0-100 risk score is mapped onto the same bands (15 / 35 / 60).
- **Judging neighbours, not the whole screen.** Text read off a screen arrives as separate pieces (bubbles, paragraphs,
  buttons). The engine judges each piece together with the next two, and the most worrying window wins. A scam split over
  a few bubbles is still read as one message, but words from bubbles or buttons far apart can no longer combine into a
  false match (this is what flagged a chat with an AI assistant as HIGH in 1.5.0).
- The shipped file is `app/src/main/assets/intent_model.json` (about 680 KB, pruned to the strongest weights and
  quantised to 8 bits). Everything runs on the phone; nothing is uploaded. Settings has a "Smart intent detection" switch;
  with it off, or if the file cannot be read, the older additive rule scoring is used.

## Where the training data came from, and what that means
The model is trained on **3,180 messages (1,150 scam, 2,030 ordinary) written by language models** from detailed briefs:
many scam types and registers, Ghana/Nigeria/Kenya/South Africa/UK/US/India/Philippines flavours, Pidgin, SMS shorthand,
code-mixing, multi-bubble lures, and a lot of *hard ordinary* text: genuine one-time codes and bank alerts, fraud-awareness
posts, legitimate programme and business promotions, group invitations, enquiries, developer and AI-assistant chat, long
prose and screen dumps. They are not real victims' messages, and some files (notably `train_benign_D`) are partly
template-composed, so wording repeats more than real data would. Real scams will differ in ways these do not capture, so
expect real-world accuracy below the numbers here. Files: `tools/intent/data/train_*.jsonl`.

## Measured results
Two **blind test sets** were written by writers who never saw the training data (319 and 400 messages, half scam, half
ordinary, deliberately full of look-alikes and stock-phrase-free scams). They were not used for fitting or for choosing
thresholds. Numbers for the shipped (pruned, quantised) model, run through the real engine:

| Set | Detector | Scams flagged at LOW+ | False alarms LOW+ | Scams at MEDIUM+ | False alarms MEDIUM+ | Scams at HIGH | False alarms HIGH |
|---|---|---|---|---|---|---|---|
| blind 1 (319) | rules alone | 21% | 3.8% | 17.5% | 1.9% | 8% | 1.9% |
| blind 1 (319) | learned model | 87.5% | 7.5% | 73% | 3.1% | 56% | 1.3% |
| blind 2 (400) | rules alone | 31% | 9.5% | 22.5% | 5.5% | 12.5% | 4.5% |
| blind 2 (400) | learned model | 91% | 3.0% | 75.5% | 1.5% | 64% | 0% |

Five-fold cross-validation on the training data (every example scored by a model that never saw it): 91% accuracy; 82% of
scams caught at 4% false alarms, 67% at 1.5%, 51% at 0.5%. `IntentModelTest` prints the blind-set numbers on every run and
fails if the model stops beating the rules or the false-alarm rates climb. It also pins real cases found on a phone: the
legitimate FORGE30 programme announcement, an enquiry about it, and a chat about this app are *not* flagged, and the
split-bubble "send us the message you just received / there's a code in it we need to add you to the group" lure *is*.

## Limits (please read)
- It is an estimate, not proof. About a quarter of the scams in the blind sets still pass MEDIUM, and a few ordinary
  messages are flagged. LOW warnings are notification-only by default; the pop-up and vibration start at MEDIUM.
- It is a statistical model over words and word pieces. It generalises over wording far better than a phrase list, but it
  does not *understand* text the way a person does. Truly novel scam types, other languages and cleverly worded messages
  can slip through.
- The monitor reads *all* text in a monitored app, including messages you send yourself, so a message-yourself chat used
  for testing is scanned like any other. Single threatening bubbles with no ask ("We have blocked your account") are
  rated low on their own and are caught mainly when they sit next to the lure.
- An on-device language model could understand intent much more deeply, but it needs a model file of several hundred
  megabytes, a phone with enough memory, seconds of processing per message and battery, and cannot be tested without a
  device. It is not part of this release.

## Retraining
```
DUMP_RULEHITS=1 ./gradlew -PengineOnly=true :engine:test --rerun --tests '*DumpRuleHits*'   # which rules fire on each example
python3 tools/intent/train.py --cv           # pick the regulariser with 5-fold cross-validation
python3 tools/intent/train.py                # writes app/src/main/assets/intent_model.json and its thresholds
python3 tools/intent/evaluate.py tools/intent/data/test_blind2.jsonl
```
Then regenerate `tools/intent/data/golden.json` (see `tools/intent/README.md`) so the Kotlin port is checked against the
new weights. Retrain whenever rules are added, changed or removed (the model was fitted to what they fire on). Needs only
Python 3, numpy and the Gradle build.
