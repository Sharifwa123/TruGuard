"""Writes the app assets for the language model and the golden file used by the Kotlin parity test.

    python3 tools/intent/semantic/make_assets.py <model dir from train_semantic.py> [--version 1.0]

- app/src/main/assets/semantic_model.onnx, semantic_vocab.txt   (copied)
- app/src/main/assets/semantic_meta.json                        (groups, blend weights, thresholds for the blend)
- tools/intent/data/semantic_golden.json                        (ids and logits from onnxruntime for ~80 texts)

Blend thresholds: the benign scores of the pooled blind files are cut at the same false-alarm budgets as the fast
model (4% LOW, 1.5% MEDIUM, 0.5% HIGH). That is a few hundred benign examples, so the HIGH cut in particular is noisy.
"""
import argparse
import glob
import json
import os
import shutil
import sys

import numpy as np
import onnxruntime as ort

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from tokenizer import Vocab, encode  # noqa: E402

ASSETS = os.path.join(HERE, "..", "..", "..", "app", "src", "main", "assets")
DATA = os.path.join(HERE, "..", "data")
GROUPS = ["secret_request", "payment_request", "threat_pressure", "reward_bait", "impersonation_trust", "access_install"]
W_FAST, W_SEM = 0.6, 0.4
BUDGETS = {"low": 0.04, "medium": 0.015, "high": 0.005}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("model_dir")
    ap.add_argument("--version", default="1.0")
    a = ap.parse_args()
    d = np.load(os.path.join(a.model_dir, "blind_scores.npz"))
    sets = [k[:-2] for k in d.files if k.endswith("_y")]
    y = np.concatenate([d[s + "_y"] for s in sets])
    blend = np.concatenate([W_FAST * d[s + "_fast"] + W_SEM * d[s + "_sem"] for s in sets])
    ben = np.sort(blend[~y])[::-1]
    th = {}
    for name, b in BUDGETS.items():
        cut = ben[min(len(ben) - 1, int(b * len(ben)))]
        th[name] = round(float(1 / (1 + np.exp(-cut))), 4)
    meta = {"version": a.version, "groups": GROUPS, "thresholds": th, "wFast": W_FAST, "wSemantic": W_SEM}
    shutil.copy(os.path.join(a.model_dir, "semantic_model.onnx"), os.path.join(ASSETS, "semantic_model.onnx"))
    shutil.copy(os.path.join(a.model_dir, "semantic_vocab.txt"), os.path.join(ASSETS, "semantic_vocab.txt"))
    json.dump(meta, open(os.path.join(ASSETS, "semantic_meta.json"), "w"), indent=1)
    print("meta", meta)
    print(f"benign examples used for the cuts: {int((~y).sum())}; scams: {int(y.sum())}")
    for name, t in th.items():
        c = np.log(t / (1 - t))
        print(f"  {name}: recall {float((blend[y] > c).mean()):.3f}, false alarms {float((blend[~y] > c).mean()):.3f} (on the same sets the cuts came from)")

    # golden file
    sess = ort.InferenceSession(os.path.join(ASSETS, "semantic_model.onnx"), providers=["CPUExecutionProvider"])
    vocab = Vocab.load(os.path.join(ASSETS, "semantic_vocab.txt"))
    texts = []
    for f in sorted(glob.glob(os.path.join(DATA, "test_blind*.jsonl"))):
        rows = [json.loads(l) for l in open(f, encoding="utf-8") if l.strip()]
        texts += [r["text"] for r in rows[:: max(1, len(rows) // 25)]]
    texts += ["Hello \U0001F49B world \U0001F339", "ａｂｃ ＡＢＣ １２３", "tab\there\nnew  line", "مرحبا كيف حالك",
              "你好世界 hello", "नमस्ते दोस्त", "naaaa \U0001F612\U0001F612 ya pic??", "​zero​width", "x" * 40 + " yyyyy", "a", "   ",
              "Can I get a view once of ya pic", "Send me the code you received so I can add you to the group"]
    gold = []
    for t in texts:
        ids = encode(t, vocab)
        s, g = sess.run(None, {"input_ids": np.array([ids], dtype=np.int64), "attention_mask": np.ones((1, len(ids)), dtype=np.int64)})
        gold.append({"text": t, "ids": ids, "logit": float(s[0]), "group": [float(x) for x in g[0]]})
    json.dump(gold, open(os.path.join(DATA, "semantic_golden.json"), "w"), ensure_ascii=False, indent=0)
    print("golden rows", len(gold))


if __name__ == "__main__":
    main()
