# Intent model: catching scams no phrase list anticipated

Scammers rephrase, misspell, translate and code-mix, so a list of known phrases (the rules in `scam_rules.json`) can
never be complete. TruGuard therefore also runs a small **learned** model that scores *how a message is built* instead
of which words it uses. It adds to the rules; it never replaces them.

## How it works
- **Features** (`IntentFeatures.kt`, mirrored by `tools/intent/featurize.py`): words, word pairs and character n-grams
  inside words (hashed into 2^18 buckets), plus a few shape features (length, `?`, `!`, shouting). Links and numbers are
  replaced by placeholders. Because the n-grams are *inside* words, "pls", "plz", "pleeease" and "please" share most
  features; so do "OTP", "o.t.p" and "0TP". There is no vocabulary.
- **Model** (`IntentClassifier.kt`): logistic regression over those features for "is this trying to get something
  harmful out of the reader?", plus a second head that names the kind of manipulation (asks for a secret, asks for
  money, threatens/pressures, offers a reward, builds trust or impersonates, asks for access/installation). The head's
  name picks the plain-language explanation shown to the user.
- **Scoring** (`IntentScoring.kt`): probability bands map to rule-engine scores (>=0.95: 45, >=0.90: 38, >=0.80: 26,
  >=0.75: 15). If rules already fired the model adds less (they say much the same thing), but it still adds.
- The shipped file is `app/src/main/assets/intent_model.json` (about 660 KB: pruned to the strongest 24,000 weights and
  quantised to 8 bits). It is analysed on the phone like everything else; nothing is uploaded.
- Settings has a "Smart intent detection" switch (default on).

## Where the training data came from, and what that means
The model is trained on **1,600 messages (700 scam, 900 ordinary) written by language models** from detailed briefs
(many scam types, registers, Ghana/Nigeria/Kenya/South Africa/UK/US/India/Philippines flavours, Pidgin, SMS shorthand,
code-mixing, and many *hard* ordinary messages: genuine one-time codes, bank alerts, fraud-awareness warnings, friends
borrowing money). They are not real victims' messages. Files: `tools/intent/data/train_*.jsonl`. Real scams will differ
in ways these do not capture, so expect real-world accuracy below the numbers here.

## Measured results
A separate **blind test set** of 319 messages (160 scam, 159 ordinary) was written by a different writer who was told to
avoid the usual stock phrases and to include alarming-sounding genuine messages. It was not used to train. Numbers for
the shipped (pruned, quantised) model:

| Detector | Scams flagged (LOW+) | Ordinary messages falsely flagged (LOW+) | Scams (MEDIUM+) | False alarms (MEDIUM+) |
|---|---|---|---|---|
| Rules alone | 21% | 3.8% | 17.5% | 1.9% |
| Rules + intent model | 85% | 7.5% | 67% | 2.5% |

Model alone at probability >= 0.90: 57% of scams caught, 0.6% false alarms; at >= 0.50: 90% caught, 8% false alarms.
5-fold cross-validation on the training data: about 88-90% accuracy. The prune size and score bands were chosen using
cross-validation and a look at this blind set, so its numbers are slightly optimistic. The "ordinary" half of the test is
deliberately full of look-alikes, so false alarms on everyday chat should be lower than shown. `IntentModelTest` prints
these numbers on every run and fails if the model stops beating the rules or the false-alarm rate climbs above 5%.

## Limits
- It is an estimate, not proof. Some scams will still be missed and some genuine messages flagged.
- English (with Pidgin and some code-mixing) is covered best; other languages are thin.
- A cleverly worded scam from someone who knows how the model works can still slip through; screens and notifications
  are only part of defence (never share codes or PINs, whoever asks).

## Retraining
```
python3 tools/intent/train.py --cv          # pick the regulariser with 5-fold cross-validation
python3 tools/intent/train.py               # writes app/src/main/assets/intent_model.json
python3 tools/intent/evaluate.py tools/intent/data/test_blind.jsonl
```
After retraining, regenerate `tools/intent/data/golden.json` (see the snippet in `tools/intent/README.md`) so the Kotlin
port is checked against the new weights. Needs only Python 3 and numpy.
