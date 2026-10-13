"""Scores the exported ONNX language model, the fast model, and blends of the two on the blind test files.

    python3 tools/intent/semantic/evaluate_semantic.py <dir with semantic_model.onnx + semantic_vocab.txt> [--write-meta app/src/main/assets]

Thresholds for the blend are cut from the benign scores of the pooled blind sets at the same false-alarm budgets as the
fast model (4% / 1.5% / 0.5%); the script also reports each blind set with thresholds cut from the OTHER two sets.
"""
import json
import os
import sys

import numpy as np
import onnxruntime as ort

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, ".."))
from evaluate import Model  # noqa: E402
from tokenizer import PAD, Vocab, encode  # noqa: E402
from train import tags_of  # noqa: E402

DATA = os.path.join(HERE, "..", "data")
SETS = ["test_blind", "test_blind2", "test_blind3"]
BUDGETS = (0.04, 0.015, 0.005)


def rows_of(name):
    return [json.loads(l) for l in open(os.path.join(DATA, name + ".jsonl"), encoding="utf-8") if l.strip()]


def logit(p):
    p = np.clip(p, 1e-6, 1 - 1e-6)
    return np.log(p / (1 - p))


def semantic_scores(sess, vocab, texts):
    out, groups = [], []
    for t in texts:
        ids = np.array([encode(t, vocab)], dtype=np.int64)
        s, g = sess.run(None, {"input_ids": ids, "attention_mask": np.ones_like(ids)})
        out.append(float(s[0]))
        groups.append(g[0])
    return np.array(out), np.array(groups)


def cut(scores, y, budget):
    ben = np.sort(scores[~y])[::-1]
    return float(ben[min(len(ben) - 1, int(budget * len(ben)))])


def recall(scores, y, t):
    return float((scores[y] > t).mean())


def main():
    model_dir = sys.argv[1]
    sess = ort.InferenceSession(os.path.join(model_dir, "semantic_model.onnx"), providers=["CPUExecutionProvider"])
    vocab = Vocab.load(os.path.join(model_dir, "semantic_vocab.txt"))
    fast = Model()
    data = {}
    for n in SETS:
        rows = rows_of(n)
        y = np.array([r["label"] == "scam" for r in rows])
        texts = [r["text"] for r in rows]
        lf = logit(np.array([fast.predict(t, tags_of(t))[0] for t in texts]))
        ls, _ = semantic_scores(sess, vocab, texts)
        data[n] = (y, lf, ls)
    np.savez(os.path.join(model_dir, "blind_scores.npz"), **{f"{n}_{k}": v for n, d in data.items() for k, v in zip(("y", "fast", "sem"), d)})
    weights = [(1.0, 0.0), (0.0, 1.0), (0.5, 0.5), (0.4, 0.6), (0.3, 0.7), (0.6, 0.4)]
    for wf, ws in weights:
        label = "fast only" if ws == 0 else "semantic only" if wf == 0 else f"blend {wf}/{ws}"
        line = []
        for n in SETS:
            y, lf, ls = data[n]
            s = wf * lf + ws * ls
            others = [data[m] for m in SETS if m != n]
            ys = np.concatenate([o[0] for o in others])
            so = np.concatenate([wf * o[1] + ws * o[2] for o in others])
            res = []
            for b in BUDGETS[:2]:
                t = cut(so, ys, b)  # threshold fitted on the other two sets only
                fa = float((s[~y] > t).mean())
                res.append(f"R={recall(s, y, t):.2f}/FA={fa:.3f}")
            line.append(f"{n[5:] or 'blind1'}: " + " ".join(res))
        print(f"{label:16s} | " + " | ".join(line))


if __name__ == "__main__":
    main()
