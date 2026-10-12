"""Scores the SHIPPED model (pruned + quantised, read from the asset JSON) on a labelled JSONL file."""
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from featurize import features  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
MODEL = os.path.join(HERE, "..", "..", "app", "src", "main", "assets", "intent_model.json")


class Model:
    def __init__(self, path=MODEL):
        m = json.load(open(path))
        s = m["scam"]
        self.sb, self.ss = s["bias"], s["scale"]
        self.sw = dict(zip(s["idx"], s["w"]))
        self.groups = m["groups"]
        g = m["group"]
        self.gs = g["scale"]
        self.gb = g["bias"]
        self.gw = [dict(zip(i, w)) for i, w in zip(g["idx"], g["w"])]

    def predict(self, text):
        idx, val = features(text)
        z = self.sb + self.ss * sum(self.sw.get(i, 0) * v for i, v in zip(idx, val))
        p = 1 / (1 + math.exp(-max(-30, min(30, z))))
        zs = [self.gb[c] + self.gs * sum(self.gw[c].get(i, 0) * v for i, v in zip(idx, val)) for c in range(len(self.groups))]
        top = max(range(len(zs)), key=lambda c: zs[c])
        return p, self.groups[top]


def main(path, thresholds=(0.5, 0.6, 0.7, 0.8, 0.9)):
    rows = [json.loads(l) for l in open(path, encoding="utf-8") if l.strip()]
    model = Model()
    scores = [(model.predict(r["text"])[0], r["label"] == "scam", r) for r in rows]
    n_s = sum(1 for _, s, _ in scores if s)
    n_b = len(scores) - n_s
    print(f"{path}: {len(rows)} rows ({n_s} scam, {n_b} benign)")
    for t in thresholds:
        tp = sum(1 for p, s, _ in scores if p >= t and s)
        fp = sum(1 for p, s, _ in scores if p >= t and not s)
        print(f"  threshold {t:.2f}: recall {tp/max(1,n_s):.3f}  false-alarm rate {fp/max(1,n_b):.3f}  precision {tp/max(1,tp+fp):.3f}  (caught {tp}/{n_s}, false alarms {fp}/{n_b})")
    return scores


if __name__ == "__main__":
    main(sys.argv[1])
