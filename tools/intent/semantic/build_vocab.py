"""Builds the pruned vocabulary (semantic_vocab.txt) and the old-id -> new-id map from the XLM-R tokenizer.json.

    python3 tools/intent/semantic/build_vocab.py <tokenizer.json> <out_dir>

Kept: every piece of one or two characters, Latin-script and symbol pieces scoring above KEEP_SCORE, the most common
pieces of a few other scripts, and every piece the training corpora need. Ids are renumbered; the matching rows are
cut from the embedding matrix by train_semantic.py.
"""
import glob
import json
import os
import sys
import unicodedata

sys.path.insert(0, os.path.dirname(__file__))
from tokenizer import Vocab, encode  # noqa: E402

KEEP_SCORE = -13.0
OTHER_SCRIPTS = ("ARABIC", "DEVANAGARI", "CYRILLIC", "GREEK")
OTHER_TOP = 1500
HERE = os.path.dirname(os.path.abspath(__file__))


def script(piece):
    for ch in piece:
        if ch == "▁" or ch.isdigit() or unicodedata.category(ch)[0] in "PSZ":
            continue
        name = unicodedata.name(ch, "")
        for s in ("LATIN", "CYRILLIC", "ARABIC", "DEVANAGARI", "GREEK"):
            if name.startswith(s):
                return s
        return "OTHER"
    return "SYM"


def main(tok_path, out_dir):
    t = json.load(open(tok_path, encoding="utf-8"))
    full = t["model"]["vocab"]
    keep = set(range(4))
    per_other = {s: [] for s in OTHER_SCRIPTS}
    for i, (p, s) in enumerate(full):
        if i < 4 or i == 250001:
            continue
        sc = script(p)
        if sc in ("LATIN", "SYM") and (len(p.replace("▁", "")) <= 2 or s > KEEP_SCORE):
            keep.add(i)
        elif sc in per_other:
            per_other[sc].append((s, i))
    for sc, lst in per_other.items():
        for _, i in sorted(lst, reverse=True)[:OTHER_TOP]:
            keep.add(i)
    # pieces the corpora need, found with the full vocabulary
    full_vocab = Vocab([(p, s) for p, s in full[:250001]])
    texts = []
    for f in glob.glob(os.path.join(HERE, "..", "data", "*.jsonl")):
        if "rulehits" in f or "test_blind" in f:
            continue
        texts += [json.loads(l)["text"] for l in open(f, encoding="utf-8") if l.strip()]
    for txt in texts:
        keep.update(encode(txt, full_vocab, maxlen=10_000))
    order = sorted(keep)
    os.makedirs(out_dir, exist_ok=True)
    Vocab([tuple(full[i]) for i in order]).save(os.path.join(out_dir, "semantic_vocab.txt"))
    json.dump(order, open(os.path.join(out_dir, "keep_ids.json"), "w"))
    print(f"kept {len(order)} of {len(full)} pieces")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
