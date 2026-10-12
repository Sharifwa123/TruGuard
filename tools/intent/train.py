"""Trains the intent classifier from tools/intent/data/*.jsonl and writes app/src/main/assets/intent_model.json.

    python3 tools/intent/train.py            # train on everything, write the model
    python3 tools/intent/train.py --cv       # also print 5-fold cross-validation (used to pick the regulariser)

Evaluation on the blind test file is done by evaluate.py against the *shipped* (pruned, quantised) model.
"""
import argparse
import glob
import json
import math
import os
import random
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
from featurize import BUCKETS, features  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "..", "app", "src", "main", "assets", "intent_model.json")

GROUPS = ["secret_request", "payment_request", "threat_pressure", "reward_bait", "impersonation_trust", "access_install"]
CATEGORY_GROUP = {
    "otp_forwarding": "secret_request", "phishing_link": "secret_request",
    "tech_support_remote_access": "access_install",
    "mobile_money_reversal": "payment_request", "advance_fee_loan": "payment_request",
    "delivery_fee_parcel": "payment_request", "marketplace_rental_deposit": "payment_request",
    "job_scam": "reward_bait", "fake_prize": "reward_bait", "investment_crypto": "reward_bait",
    "family_emergency_impersonation": "impersonation_trust", "romance_sextortion": "impersonation_trust",
    "authority_impersonation": "threat_pressure", "blackmail_threat_urgency": "threat_pressure",
}


def load(names):
    rows = []
    for name in names:
        for path in sorted(glob.glob(os.path.join(HERE, "data", name))):
            for line in open(path, encoding="utf-8"):
                line = line.strip()
                if line:
                    rows.append(json.loads(line))
    return rows


_HITS = None


def rule_hits():
    """sha1(text) -> rule ids the Kotlin engine fires on it (written by DumpRuleHits)."""
    global _HITS
    if _HITS is None:
        _HITS = {}
        path = os.path.join(HERE, "data", "rulehits.jsonl")
        if os.path.exists(path):
            for line in open(path, encoding="utf-8"):
                if line.strip():
                    o = json.loads(line)
                    _HITS[o["sha"]] = o["rules"]
    return _HITS


def tags_of(text):
    import hashlib
    return rule_hits().get(hashlib.sha1(text.encode("utf-8")).hexdigest(), [])


def to_csr(rows):
    idx, val, ptr = [], [], [0]
    for r in rows:
        i, v = features(r["text"], tags_of(r["text"]))
        idx.extend(i)
        val.extend(v)
        ptr.append(len(idx))
    return np.array(idx, dtype=np.int64), np.array(val, dtype=np.float64), np.array(ptr, dtype=np.int64)


def row_ids(ptr):
    return np.repeat(np.arange(len(ptr) - 1), np.diff(ptr))


def logits(w, b, csr):
    idx, val, ptr = csr
    n = len(ptr) - 1
    out = np.zeros(n)
    nz = np.diff(ptr) > 0
    contrib = w[idx] * val
    sums = np.add.reduceat(contrib, ptr[:-1][nz]) if len(idx) else np.zeros(nz.sum())
    out[nz] = sums
    return out + b


def sigmoid(z):
    return 1.0 / (1.0 + np.exp(-np.clip(z, -30, 30)))


def fit_binary(csr, y, l2, sample_w, epochs=400, lr=0.15):
    idx, val, ptr = csr
    rid = row_ids(ptr)
    w = np.zeros(BUCKETS)
    b = 0.0
    m = np.zeros(BUCKETS); v = np.zeros(BUCKETS); mb = 0.0; vb = 0.0
    n = len(y)
    for t in range(1, epochs + 1):
        p = sigmoid(logits(w, b, csr))
        err = (p - y) * sample_w
        g = np.zeros(BUCKETS)
        np.add.at(g, idx, val * err[rid])
        g = g / n + l2 * w
        gb = err.mean()
        m = 0.9 * m + 0.1 * g; v = 0.999 * v + 0.001 * g * g
        mb = 0.9 * mb + 0.1 * gb; vb = 0.999 * vb + 0.001 * gb * gb
        mh = m / (1 - 0.9 ** t); vh = v / (1 - 0.999 ** t)
        w -= lr * mh / (np.sqrt(vh) + 1e-8)
        b -= lr * (mb / (1 - 0.9 ** t)) / (math.sqrt(vb / (1 - 0.999 ** t)) + 1e-8)
    return w, b


def fit_softmax(csr, y, k, l2, epochs=300, lr=0.15):
    idx, val, ptr = csr
    rid = row_ids(ptr)
    n = len(y)
    W = np.zeros((k, BUCKETS)); B = np.zeros(k)
    M = np.zeros_like(W); V = np.zeros_like(W); Mb = np.zeros(k); Vb = np.zeros(k)
    onehot = np.eye(k)[y]
    for t in range(1, epochs + 1):
        Z = np.stack([logits(W[c], B[c], csr) for c in range(k)], axis=1)
        Z -= Z.max(axis=1, keepdims=True)
        P = np.exp(Z); P /= P.sum(axis=1, keepdims=True)
        E = P - onehot
        for c in range(k):
            g = np.zeros(BUCKETS)
            np.add.at(g, idx, val * E[rid, c])
            g = g / n + l2 * W[c]
            M[c] = 0.9 * M[c] + 0.1 * g; V[c] = 0.999 * V[c] + 0.001 * g * g
            W[c] -= lr * (M[c] / (1 - 0.9 ** t)) / (np.sqrt(V[c] / (1 - 0.999 ** t)) + 1e-8)
            gb = E[:, c].mean()
            Mb[c] = 0.9 * Mb[c] + 0.1 * gb; Vb[c] = 0.999 * Vb[c] + 0.001 * gb * gb
            B[c] -= lr * (Mb[c] / (1 - 0.9 ** t)) / (math.sqrt(Vb[c] / (1 - 0.999 ** t)) + 1e-8)
    return W, B


def prune_quantise(w, keep):
    order = np.argsort(-np.abs(w))[:keep]
    order = order[np.abs(w[order]) > 1e-9]
    order = np.sort(order)
    scale = float(np.abs(w[order]).max() / 127.0) if len(order) else 1.0
    q = np.clip(np.round(w[order] / scale), -127, 127).astype(int)
    keepmask = q != 0
    return [int(i) for i in order[keepmask]], [int(x) for x in q[keepmask]], scale


def oof_probs(rows, l2, folds=5, epochs=400):
    """Out-of-fold probabilities: every example is scored by a model that never saw it."""
    y = np.array([1 if r["label"] == "scam" else 0 for r in rows])
    feats = [to_csr([r]) for r in rows]

    def sub(ix):
        idx = np.concatenate([feats[i][0] for i in ix]); val = np.concatenate([feats[i][1] for i in ix])
        ptr = np.concatenate([[0], np.cumsum([len(feats[i][0]) for i in ix])])
        return idx, val, ptr

    p = np.zeros(len(rows))
    for f in range(folds):
        te = [i for i in range(len(rows)) if i % folds == f]
        tr = [i for i in range(len(rows)) if i % folds != f]
        w, b = fit_binary(sub(tr), y[tr], l2, np.ones(len(tr)), epochs=epochs)
        p[te] = sigmoid(logits(w, b, sub(te)))
    return p, y


# Benign-side false-alarm budgets for the three severities. Real traffic is overwhelmingly ordinary, so they are tight.
FPR_TARGETS = {"low": 0.04, "medium": 0.015, "high": 0.005}


def calibrate(p, y):
    """Thresholds such that at most FPR_TARGETS of ordinary messages reach each severity (out-of-fold)."""
    benign = np.sort(p[y == 0])[::-1]
    th = {}
    for name, target in FPR_TARGETS.items():
        k = int(len(benign) * target)
        th[name] = float(min(0.999, max(0.5, benign[k] + 1e-6 if k < len(benign) else 0.5)))
    # keep strictly increasing
    th["medium"] = max(th["medium"], th["low"] + 0.02)
    th["high"] = max(th["high"], th["medium"] + 0.02)
    return th


def report(p, y, th):
    for name in ("low", "medium", "high"):
        flagged = p >= th[name]
        print(f"  {name:6s} threshold {th[name]:.3f}: scams caught {(flagged & (y == 1)).sum()/max(1,(y==1).sum()):.3f}, "
              f"ordinary falsely flagged {(flagged & (y == 0)).sum()/max(1,(y==0).sum()):.3f}", flush=True)


def cv(rows, l2s, folds=5):
    random.Random(1).shuffle(rows)
    for l2 in l2s:
        p, y = oof_probs(rows, l2, folds)
        th = calibrate(p, y)
        print(f"l2={l2:g} accuracy@0.5={((p >= 0.5) == (y == 1)).mean():.4f}", flush=True)
        report(p, y, th)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--cv", action="store_true")
    ap.add_argument("--l2", type=float, default=3e-6)
    ap.add_argument("--keep", type=int, default=24000)
    args = ap.parse_args()
    rows = load(["train_*.jsonl"])
    print("training rows:", len(rows), "scam:", sum(r["label"] == "scam" for r in rows), flush=True)
    if args.cv:
        cv(list(rows), [1e-5, 3e-5, 1e-4])
        return
    y = np.array([1 if r["label"] == "scam" else 0 for r in rows])
    print("rows with rule-hit data:", sum(1 for r in rows if tags_of(r["text"])), "of", len(rows), flush=True)
    random.Random(1).shuffle(rows)
    y = np.array([1 if r["label"] == "scam" else 0 for r in rows])
    p_oof, y_oof = oof_probs(rows, args.l2)
    thresholds = calibrate(p_oof, y_oof)
    print("out-of-fold results with calibrated thresholds:")
    report(p_oof, y_oof, thresholds)
    csr = to_csr(rows)
    w, b = fit_binary(csr, y, args.l2, np.ones(len(y)))
    scam_rows = [r for r in rows if r["label"] == "scam" and r.get("category") in CATEGORY_GROUP]
    yg = np.array([GROUPS.index(CATEGORY_GROUP[r["category"]]) for r in scam_rows])
    W, B = fit_softmax(to_csr(scam_rows), yg, len(GROUPS), args.l2)

    sidx, sw, sscale = prune_quantise(w, args.keep)
    gidx, gw, gscale = [], [], 0.0
    gscale = float(max(np.abs(W[c][np.argsort(-np.abs(W[c]))[:args.keep // 3]]).max() for c in range(len(GROUPS))) / 127.0)
    for c in range(len(GROUPS)):
        order = np.sort(np.argsort(-np.abs(W[c]))[: args.keep // 3])
        q = np.clip(np.round(W[c][order] / gscale), -127, 127).astype(int)
        m = q != 0
        gidx.append([int(i) for i in order[m]]); gw.append([int(x) for x in q[m]])
    model = {
        "version": "2026.10.2", "buckets": BUCKETS,
        "scam": {"bias": float(b), "scale": sscale, "idx": sidx, "w": sw},
        "groups": GROUPS,
        "group": {"bias": [float(x) for x in B], "scale": gscale, "idx": gidx, "w": gw},
        "thresholds": {k: round(v, 4) for k, v in thresholds.items()},
    }
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w") as fh:
        json.dump(model, fh, separators=(",", ":"))
    print("wrote", OUT, os.path.getsize(OUT) // 1024, "KB; scam weights kept:", len(sidx))


if __name__ == "__main__":
    main()
