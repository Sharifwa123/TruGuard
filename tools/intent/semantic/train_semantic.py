"""Fine-tunes the multilingual MiniLM encoder into the on-device scam-intent model and exports it to ONNX (int8).

    pip install torch transformers safetensors onnx onnxruntime numpy tokenizers
    python3 tools/intent/semantic/train_semantic.py --base <dir with the HF model> --vocab <dir from build_vocab.py> --out <dir>

Writes into --out: semantic_model.onnx, semantic_vocab.txt, semantic_scores.json (held-out scores for calibration).
The training texts are tools/intent/data/train_*.jsonl; test_blind*.jsonl are never trained on.
"""
import argparse
import glob
import json
import os
import random
import shutil
import sys
import time

import numpy as np
import torch
import torch.nn as nn

sys.path.insert(0, os.path.dirname(__file__))
from tokenizer import MAXLEN, PAD, Vocab, encode  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(HERE, "..", "data")
GROUPS = ["secret_request", "payment_request", "threat_pressure", "reward_bait", "impersonation_trust", "access_install"]
CATEGORY_GROUP = {
    "otp_forwarding": "secret_request", "phishing_link": "secret_request", "split_bubble_otp_lure": "secret_request",
    "tech_support_remote_access": "access_install",
    "mobile_money_reversal": "payment_request", "advance_fee_loan": "payment_request", "delivery_fee_parcel": "payment_request",
    "marketplace_rental_deposit": "payment_request", "split_bubble_money_lure": "payment_request", "fake_program_or_course_with_fee": "payment_request",
    "job_scam": "reward_bait", "fake_prize": "reward_bait", "investment_crypto": "reward_bait", "fake_job_task_post": "reward_bait", "investment_or_giveaway_post": "reward_bait",
    "family_emergency_impersonation": "impersonation_trust", "romance_sextortion": "impersonation_trust", "romance_trust_opener": "impersonation_trust", "impersonation_chat": "impersonation_trust",
    "authority_impersonation": "threat_pressure", "blackmail_threat_urgency": "threat_pressure", "split_bubble_threat_pressure": "threat_pressure",
}


def load(pattern):
    rows = []
    for path in sorted(glob.glob(os.path.join(DATA, pattern))):
        rows += [json.loads(l) for l in open(path, encoding="utf-8") if l.strip()]
    return rows


class Net(nn.Module):
    def __init__(self, enc):
        super().__init__()
        self.enc = enc
        self.scam = nn.Linear(enc.config.hidden_size, 1)
        self.group = nn.Linear(enc.config.hidden_size, len(GROUPS))

    def forward(self, ids, mask):
        h = self.enc(input_ids=ids, attention_mask=mask).last_hidden_state
        m = mask.unsqueeze(-1).to(h.dtype)
        e = (h * m).sum(1) / m.sum(1)
        return self.scam(e).squeeze(-1), self.group(e)


class Int8Embedding(nn.Module):
    """Embedding rows stored as int8 with a per-row scale (a quarter of the size); dequantised at lookup."""

    def __init__(self, weight):
        super().__init__()
        scale = weight.abs().amax(1, keepdim=True).clamp(min=1e-8) / 127.0
        self.register_buffer("q", torch.round(weight / scale).to(torch.int8))
        self.register_buffer("scale", scale)

    def forward(self, ids):
        return self.q[ids].to(torch.float32) * self.scale[ids]


def batch(rows, vocab):
    seqs = [encode(r["text"], vocab) for r in rows]
    n = max(len(s) for s in seqs)
    ids = torch.full((len(seqs), n), PAD, dtype=torch.long)
    mask = torch.zeros((len(seqs), n), dtype=torch.long)
    for i, s in enumerate(seqs):
        ids[i, : len(s)] = torch.tensor(s)
        mask[i, : len(s)] = 1
    y = torch.tensor([float(r["label"] == "scam") for r in rows])
    g = torch.tensor([GROUPS.index(CATEGORY_GROUP[r["category"]]) if r["label"] == "scam" and r.get("category") in CATEGORY_GROUP else -100 for r in rows])
    return ids, mask, y, g


def predict(net, rows, vocab):
    net.eval()
    sc, gr = [], []
    with torch.no_grad():
        for i in range(0, len(rows), 64):
            ids, mask, _, _ = batch(rows[i:i + 64], vocab)
            s, g = net(ids, mask)
            sc.append(s.numpy())
            gr.append(g.numpy())
    net.train()
    return np.concatenate(sc), np.concatenate(gr)


def report(name, rows, logits):
    y = np.array([r["label"] == "scam" for r in rows])
    p = 1 / (1 + np.exp(-logits))
    ben = np.sort(p[~y])[::-1]
    parts = []
    for f in (0.04, 0.015, 0.005):
        t = ben[int(f * len(ben))]
        parts.append(f"R@FPR{f}={(p[y] > t).mean():.3f}")
    auc = (p[y][:, None] > p[~y][None, :]).mean()
    print(f"  {name}: AUC={auc:.3f} " + " ".join(parts), flush=True)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", required=True)
    ap.add_argument("--vocab", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--epochs", type=int, default=4)
    ap.add_argument("--seed", type=int, default=0)
    ap.add_argument("--export-only", action="store_true", help="skip training; export semantic_fp32.pt found in --out")
    args = ap.parse_args()
    from transformers import AutoModel
    torch.set_num_threads(os.cpu_count() or 4)
    torch.manual_seed(args.seed)
    random.seed(args.seed)

    vocab = Vocab.load(os.path.join(args.vocab, "semantic_vocab.txt"))
    keep = json.load(open(os.path.join(args.vocab, "keep_ids.json")))
    enc = AutoModel.from_pretrained(args.base)
    full_w = enc.embeddings.word_embeddings.weight.data
    small = nn.Embedding(len(keep), full_w.shape[1], padding_idx=PAD)
    small.weight.data = full_w[torch.tensor(keep)].clone()
    enc.embeddings.word_embeddings = small
    enc.config.vocab_size = len(keep)
    net = Net(enc)

    if args.export_only:
        net.load_state_dict(torch.load(os.path.join(args.out, "semantic_fp32.pt")))
        export(net, vocab, args.out)
        shutil.copy(os.path.join(args.vocab, "semantic_vocab.txt"), os.path.join(args.out, "semantic_vocab.txt"))
        return
    train = load("train_*.jsonl")
    tests = {n: load(f"{n}.jsonl") for n in ("test_blind", "test_blind2", "test_blind3") if glob.glob(os.path.join(DATA, f"{n}.jsonl"))}
    print(f"train {len(train)} rows; vocab {len(vocab.pieces)}; tests {[(k, len(v)) for k, v in tests.items()]}", flush=True)

    bs = 16
    steps = args.epochs * ((len(train) + bs - 1) // bs)
    opt = torch.optim.AdamW([{"params": net.enc.parameters(), "lr": 3e-5}, {"params": list(net.scam.parameters()) + list(net.group.parameters()), "lr": 1e-3}], weight_decay=0.01)
    sched = torch.optim.lr_scheduler.OneCycleLR(opt, max_lr=[3e-5, 1e-3], total_steps=steps, pct_start=0.1)
    t0 = time.time()
    for ep in range(args.epochs):
        random.shuffle(train)
        total = 0.0
        for i in range(0, len(train), bs):
            ids, mask, y, g = batch(train[i:i + bs], vocab)
            s, gl = net(ids, mask)
            loss = nn.functional.binary_cross_entropy_with_logits(s, y)
            if (g != -100).any():
                loss = loss + 0.5 * nn.functional.cross_entropy(gl, g, ignore_index=-100)
            loss.backward()
            nn.utils.clip_grad_norm_(net.parameters(), 1.0)
            opt.step(); sched.step(); opt.zero_grad()
            total += loss.item()
        print(f"epoch {ep + 1} loss {total / (len(train) / bs):.4f} t={time.time() - t0:.0f}s", flush=True)
        for name, rows in tests.items():
            report(name, rows, predict(net, rows, vocab)[0])

    os.makedirs(args.out, exist_ok=True)
    torch.save(net.state_dict(), os.path.join(args.out, "semantic_fp32.pt"))
    export(net, vocab, args.out)
    shutil.copy(os.path.join(args.vocab, "semantic_vocab.txt"), os.path.join(args.out, "semantic_vocab.txt"))


def export(net, vocab, out):
    import onnx
    from onnxruntime.quantization import QuantType, quantize_dynamic
    net.eval()
    net.enc.embeddings.word_embeddings = Int8Embedding(net.enc.embeddings.word_embeddings.weight.data)
    ids = torch.tensor([encode("Send me the code you received so I can add you to the group", vocab)])
    mask = torch.ones_like(ids)
    fp32 = os.path.join(out, "semantic_model_fp32.onnx")
    torch.onnx.export(net, (ids, mask), fp32, input_names=["input_ids", "attention_mask"], output_names=["scam", "group"],
                      dynamic_axes={"input_ids": {0: "b", 1: "t"}, "attention_mask": {0: "b", 1: "t"}, "scam": {0: "b"}, "group": {0: "b"}},
                      opset_version=17, dynamo=False)
    quantize_dynamic(fp32, os.path.join(out, "semantic_model.onnx"), weight_type=QuantType.QInt8)
    os.remove(fp32)
    for extra in (fp32 + ".data",):
        if os.path.exists(extra):
            os.remove(extra)
    print("model bytes:", os.path.getsize(os.path.join(out, "semantic_model.onnx")), flush=True)


if __name__ == "__main__":
    main()
