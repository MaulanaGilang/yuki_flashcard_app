"""Mandarin helpers: pinyin conversion, per-character syllables, search keys."""

from __future__ import annotations

import re
import unicodedata

TONE_MARKS = {
    "a": "āáǎà", "e": "ēéěè", "i": "īíǐì", "o": "ōóǒò", "u": "ūúǔù", "ü": "ǖǘǚǜ",
}
_MARK_TO_BASE = {m: (b, i + 1) for b, ms in TONE_MARKS.items() for i, m in enumerate(ms)}
for _b, _ms in list(TONE_MARKS.items()):
    for _i, _m in enumerate(_ms):
        _MARK_TO_BASE[_m.upper()] = (_b.upper(), _i + 1)

HANZI_RE = re.compile(r"[㐀-䶿一-鿿豈-﫿\U00020000-\U0002ffff〇]")


def is_hanzi(ch: str) -> bool:
    return bool(HANZI_RE.fullmatch(ch))


def numeric_to_marked(syl: str) -> str:
    """'guo3' -> 'guǒ', 'lv4' / 'lu:4' -> 'lǜ', 'ma5' / 'ma' -> 'ma', 'r5' -> 'r'."""
    s = syl.strip()
    m = re.fullmatch(r"([A-Za-zÜü:]+)([1-5])?", s)
    if not m:
        return s
    body, tone = m.group(1), int(m.group(2) or 5)
    body = body.replace("u:", "ü").replace("U:", "Ü").replace("v", "ü").replace("V", "Ü")
    if tone == 5:
        return body
    low = body.lower()
    # Placement rule: a/e first; 'ou' -> o; otherwise the last vowel.
    if "a" in low:
        idx = low.index("a")
    elif "e" in low:
        idx = low.index("e")
    elif "ou" in low:
        idx = low.index("o")
    else:
        idx = max((i for i, c in enumerate(low) if c in "aeiouü"), default=-1)
        if idx < 0:  # 'r', 'm', 'ng', 'hm'
            return body
    base = body[idx]
    mark = TONE_MARKS[base.lower()][tone - 1]
    if base.isupper():
        mark = mark.upper()
    return body[:idx] + mark + body[idx + 1:]


def marked_to_numeric(syl: str) -> tuple[str, int]:
    """'guǒ' -> ('guo', 3); 'ma' -> ('ma', 5). ü is kept as 'ü'."""
    tone = 5
    out = []
    for ch in unicodedata.normalize("NFC", syl):
        if ch in _MARK_TO_BASE:
            base, tone = _MARK_TO_BASE[ch]
            out.append(base)
        else:
            out.append(ch)
    return "".join(out), tone


def strip_tones(text: str) -> str:
    """'Píngguǒ lǜ' -> 'pingguo lu' (ü folded to u; lowercase)."""
    out = []
    for ch in unicodedata.normalize("NFC", text):
        if ch in _MARK_TO_BASE:
            ch = _MARK_TO_BASE[ch][0]
        out.append(ch)
    return "".join(out).lower().replace("ü", "u")


def compact(pinyin: str) -> str:
    """Comparison form of a pinyin string: no spaces, apostrophes, separators, case."""
    return re.sub(r"[\s'’\-∥·/]", "", unicodedata.normalize("NFC", pinyin)).lower()


def parse_cedict_pinyin(p: str) -> list[str]:
    """'ping2 guo3' -> ['ping2', 'guo3'] (keeps 'r5', 'xx5'; drops ',' and '·')."""
    return [s for s in p.split() if s not in {",", "·", "-"}]


def syllables_from(word: str, candidates: list[list[str]], hsk_pinyin: str) -> tuple[list[str], list[int]] | None:
    """Pick the first candidate (numeric syllables) that matches the HSK pinyin and has one
    syllable per character. Returns (marked syllables, tones)."""
    target = compact(hsk_pinyin)
    n = len(word)
    for cand in candidates:
        if len(cand) != n:
            continue
        marked = [numeric_to_marked(s) for s in cand]
        if compact("".join(marked)) == target or not target:
            tones = [int(s[-1]) if s[-1].isdigit() else 5 for s in cand]
            return [m.lower() if not word[i].isupper() else m for i, m in enumerate(marked)], tones
    return None


# Toneless pinyin syllables, longest first, for segmenting an HSK pinyin string.
_INITIALS = ["zh", "ch", "sh", "b", "p", "m", "f", "d", "t", "n", "l", "g", "k", "h", "j", "q", "x", "r", "z", "c", "s", "y", "w", ""]
_FINALS = ["iang", "iong", "uang", "ueng", "ang", "eng", "ing", "ong", "uai", "ian", "iao", "uan", "üan", "van",
           "ai", "ei", "ao", "ou", "an", "en", "er", "ia", "ie", "in", "iu", "ua", "uo", "ui", "un", "ue", "üe", "ve", "ün",
           "a", "o", "e", "i", "u", "ü", "v", "ng", "m", "n", "r"]


def segment_marked(word: str, hsk_pinyin: str) -> tuple[list[str], list[int]] | None:
    """Split a marked pinyin string ('píngguǒ') into exactly len(word) syllables."""
    # Positions right after a space/apostrophe: the only places (besides 0) where a
    # zero-initial syllable (an, er, ou…) may start, per pinyin orthography.
    raw = unicodedata.normalize("NFC", hsk_pinyin).lower()
    s, boundaries = "", {0}
    for ch in raw:
        if re.match(r"[\s'’\-∥·/]", ch):
            boundaries.add(len(s))
        else:
            s += ch
    plain, _ = marked_to_numeric(s)
    plain = plain.replace("ü", "v")
    n = len(word)
    sylls = {i + f for i in _INITIALS for f in _FINALS if i + f and (i or f[0] in "aoe" or f in ("r", "n", "ng", "m"))}
    memo: dict[tuple[int, int], list[int] | None] = {}

    def go(pos: int, left: int) -> list[int] | None:
        if left == 0:
            return [] if pos == len(plain) else None
        key = (pos, left)
        if key in memo:
            return memo[key]
        res = None
        for L in range(min(6, len(plain) - pos), 0, -1):
            piece = plain[pos:pos + L]
            if (piece[0] in "aoe" or piece in ("n", "ng", "m")) and pos not in boundaries:
                continue
            if sum(1 for c in s[pos:pos + L] if c in _MARK_TO_BASE) > 1:  # one tone mark per syllable
                continue
            if piece in sylls:
                rest = go(pos + L, left - 1)
                if rest is not None:
                    res = [L] + rest
                    break
        memo[key] = res
        return res

    cuts = go(0, n)
    if cuts is None:
        return None
    out, tones, p = [], [], 0
    for L in cuts:
        seg = s[p:p + L]
        p += L
        _, t = marked_to_numeric(seg)
        out.append(seg)
        tones.append(t)
    return out, tones


def normalize_search(text: str) -> str:
    """search_key contract (ARCHITECTURE §2.4), identical to PackReader.normalizeQuery and
    tools/build_dev_pack.normalize: lowercase; NFD; drop combining marks after a Latin
    letter (tones, accents, ü->u), the Russian stress mark everywhere, Arabic vowel marks; NFC."""
    out, base = [], " "
    for ch in unicodedata.normalize("NFD", text.lower()):
        if unicodedata.category(ch) == "Mn":
            if ch == "́" or "a" <= base <= "z" or "ً" <= ch <= "ْ" or ch == "ٰ":
                continue
        else:
            base = ch
        out.append(ch)
    return unicodedata.normalize("NFC", "".join(out))


def search_key(headword: str, syllables: list[str], tones: list[int], glosses: list[str]) -> str:
    """'苹果 pingguo ping guo ping2guo3 apple' — headword, toneless pinyin joined and spaced,
    numbered pinyin, English glosses; same layout as build_dev_pack."""
    numbered = "".join(normalize_search(s) + str(t) for s, t in zip(syllables, tones))
    return " ".join([headword, normalize_search("".join(syllables)), normalize_search(" ".join(syllables)),
                     numbered, normalize_search("; ".join(glosses))])
