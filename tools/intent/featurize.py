"""Text -> hashed sparse features for the on-device intent classifier.

The SAME algorithm is implemented in Kotlin (engine/.../intent/IntentFeatures.kt); a golden test keeps them identical.
Words, word pairs and character n-grams inside words (so misspellings, shorthand and stretched words still share
most of their features with the proper spelling), plus a few shape features. No vocabulary list is involved.
"""
import math
import re
import unicodedata

BUCKETS = 1 << 18
MASK = BUCKETS - 1

_URL = re.compile(
    r"(?:https?://|www\.)\S+|\b[a-z0-9][a-z0-9-]*(?:\.[a-z0-9-]+)*\.(?:com|net|org|co|io|me|ly|link|info|biz|xyz|top|click|gh|ng|ke|za|ug|tk|ml|ga|cf|app|site|online)\b\S*"
)
_LONGNUM = re.compile(r"\d[\d\s().-]{5,}\d")
_DIGITS = re.compile(r"\d+")
_TOKEN = re.compile(r"[^\W_]+")


def normalize(text: str) -> str:
    t = unicodedata.normalize("NFKC", text).lower()
    t = _URL.sub(" urltok ", t)
    t = _LONGNUM.sub(" longnum ", t)
    t = _DIGITS.sub(" num ", t)
    return t


def tokens(text: str):
    return _TOKEN.findall(normalize(text))


def fnv(s: str) -> int:
    h = 2166136261
    for b in s.encode("utf-8"):
        h ^= b
        h = (h * 16777619) & 0xFFFFFFFF
    h ^= h >> 16
    h = (h * 0x45D9F3B) & 0xFFFFFFFF
    h ^= h >> 16
    return h


def features(text: str, tags=()):
    """Returns (indices, values): a sparse, L2-normalised vector. `tags` are rule ids that matched (extra features)."""
    toks = tokens(text)
    counts = {}

    def add(f):
        counts[f] = counts.get(f, 0) + 1

    for t in toks:
        add("w:" + t)
    for a, b in zip(toks, toks[1:]):
        add("b:" + a + " " + b)
    for t in toks:
        w = "<" + t + ">"
        for n in (3, 4, 5):
            for i in range(len(w) - n + 1):
                add("c:" + w[i : i + n])
    for t in tags:
        add("r:" + t)
    add("s:len" + str(min(9, (len(toks) + 1).bit_length() - 1)))
    if "?" in text:
        add("s:q")
    if "!" in text:
        add("s:ex")
    letters = [c for c in text if c.isalpha()]
    if len(letters) >= 8 and sum(1 for c in letters if c.isupper()) / len(letters) > 0.6:
        add("s:caps")

    acc = {}
    for f, c in counts.items():
        i = fnv(f) & MASK
        acc[i] = acc.get(i, 0.0) + 1.0 + math.log(c)
    norm = math.sqrt(sum(v * v for v in acc.values())) or 1.0
    idx = sorted(acc)
    return idx, [acc[i] / norm for i in idx]
