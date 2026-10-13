"""Reference tokenizer for the on-device semantic model (XLM-R style SentencePiece-unigram, pruned vocabulary).

The Kotlin `SemanticTokenizer` implements exactly this algorithm; a golden test keeps the two identical.

    normalise: NFKC, whitespace-like characters -> space, other control characters dropped
    split on whitespace; each word gets a leading U+2581 and is segmented by Viterbi over the unigram scores
    characters not in the vocabulary become <unk> (neighbouring unknowns fuse into one)
    ids are wrapped as <s> ... </s> and truncated to MAXLEN
"""
import unicodedata

PAD, BOS, EOS, UNK = 1, 0, 2, 3
MAXLEN = 128
UNK_PENALTY = 10.0
ZERO_WIDTH = {0x200B, 0x200C, 0x200D, 0xFEFF, 0x2060}


class Vocab:
    def __init__(self, pieces):
        """pieces: list of (piece, score) in id order; ids 0..3 are the special tokens."""
        self.pieces = pieces
        self.index = {p: (i, s) for i, (p, s) in enumerate(pieces) if i >= 4}
        self.max_len = max(len(p) for p, _ in pieces[4:])
        self.min_score = min(s for _, s in pieces[4:])

    @staticmethod
    def load(path):
        pieces = []
        for line in open(path, encoding="utf-8"):
            line = line.rstrip("\n")
            if not line:
                continue
            p, s = line.rsplit("\t", 1)
            pieces.append((p, float(s)))
        return Vocab(pieces)

    def save(self, path):
        with open(path, "w", encoding="utf-8") as f:
            for p, s in self.pieces:
                f.write(f"{p}\t{s!r}\n")


def normalize(text):
    text = unicodedata.normalize("NFKC", text)
    out = []
    for ch in text:
        if ch.isspace() or ord(ch) in ZERO_WIDTH:
            out.append(" ")
        elif unicodedata.category(ch) == "Cc":
            continue
        else:
            out.append(ch)
    return "".join(out)


def encode(text, vocab, maxlen=MAXLEN):
    ids = [BOS]
    for word in normalize(text).split():
        chars = list("▁" + word)
        n = len(chars)
        best = [float("-inf")] * (n + 1)
        back = [None] * (n + 1)
        best[0] = 0.0
        for i in range(n):
            if best[i] == float("-inf"):
                continue
            known_single = False
            for j in range(i + 1, min(n, i + vocab.max_len) + 1):
                hit = vocab.index.get("".join(chars[i:j]))
                if hit is None:
                    continue
                score = best[i] + hit[1]
                if score > best[j]:
                    best[j] = score
                    back[j] = (i, hit[0])
                if j == i + 1:
                    known_single = True
            if not known_single:
                score = best[i] + vocab.min_score - UNK_PENALTY
                if score > best[i + 1]:
                    best[i + 1] = score
                    back[i + 1] = (i, UNK)
        seq = []
        k = n
        while k > 0:
            i, t = back[k]
            seq.append(t)
            k = i
        seq.reverse()
        for t in seq:
            if t == UNK and ids[-1] == UNK:
                continue
            ids.append(t)
        if len(ids) >= maxlen - 1:
            break
    ids = ids[: maxlen - 1]
    ids.append(EOS)
    return ids
