# tools/intent

Training code and data for the on-device intent model. See `docs/INTENT_MODEL.md`.

- `featurize.py` - the feature extractor (mirrored in `IntentFeatures.kt`).
- `train.py` - trains and writes `app/src/main/assets/intent_model.json`.
- `evaluate.py` - scores the shipped model on a labelled JSONL file.
- `data/train_*.jsonl` - training examples (`{"text", "label": "scam"|"benign", "category"}`).
- `data/test_blind.jsonl` - held-out test written independently of the training data.
- `data/rulehits.jsonl` - which rules fire on each example (written by the `DumpRuleHits` tool test); the model uses them as features.
- `data/test_blind*.jsonl` - held-out sets written independently of the training data.
- `data/golden.json` - Python's probabilities (with rule tags) for ~100 texts; the Kotlin test must reproduce them to 1e-6.

Regenerate the golden file after retraining:

```python
import json, sys
sys.path.insert(0, "tools/intent")
from evaluate import Model
m = Model()
from train import tags_of
golden = [{"text": o["text"], "tags": o["tags"], **dict(zip(("p", "group"), m.predict(o["text"], o["tags"])))} for o in json.load(open("tools/intent/data/golden.json"))]
json.dump(golden, open("tools/intent/data/golden.json", "w"), ensure_ascii=False, indent=0)
```
