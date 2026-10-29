"""Stage 3 — enrich: glosses (CC-CEDICT), measure words, example sentences (Tatoeba,
level-filtered with jieba, human audio preferred) and stroke data (Make Me a Hanzi).

Output: work/<pack>/enriched.parquet, hanzi.parquet, enrich.report.json
"""

from __future__ import annotations

import csv
import json
import re
from collections import defaultdict
from pathlib import Path

import polars as pl
import yaml

from .config import RAW_DIR, Recipe
from .util import log, write_json
from .zh import is_hanzi, numeric_to_marked, parse_cedict_pinyin

# ---------------------------------------------------------------- CC-CEDICT

_LINE = re.compile(r"^(\S+) (\S+) \[([^\]]+)\] /(.*)/\s*$")


def load_cedict() -> dict[tuple[str, str, str], list[str]]:
    out: dict[tuple[str, str, str], list[str]] = {}
    with open(RAW_DIR / "cedict" / "cedict_ts.u8", encoding="utf-8") as f:
        for line in f:
            if line.startswith("#"):
                continue
            m = _LINE.match(line)
            if m:
                trad, simp, py, defs = m.groups()
                out.setdefault((trad, simp, py), []).extend(defs.split("/"))
    return out


_SKIP = re.compile(r"^(old |archaic )?(variant of|see |also written|also pr\.|Taiwan pr\.|surname |used in |\(Tw\)|\(old\)|\(archaic\)|\(dialect\)|\(Cantonese\)|abbr\. for)", re.I)
_REF = re.compile(r"(?:[^\s|\[\]]+\|)?([^\s|\[\]]+)\[[^\]]+\]")


def clean_def(d: str) -> str:
    d = _REF.sub(r"\1", d).strip()
    d = re.sub(r"\bsb\b", "someone", d)
    d = re.sub(r"\bsth\b", "something", d)
    d = re.sub(r"\bsb's\b", "someone's", d)
    d = re.sub(r"\s+", " ", d)
    return d


def shorten(g: str, limit: int) -> str:
    if len(g) <= limit:
        return g
    g2 = re.sub(r"\s*\([^)]*\)", "", g).strip()
    if 0 < len(g2) <= limit:
        return g2
    g = g2 or g
    cut = max(g.rfind(";", 0, limit - 1), g.rfind(",", 0, limit - 1))
    if cut > limit // 2:
        return g[:cut].strip()
    return g[: limit - 1].rsplit(" ", 1)[0].rstrip(",;") + "…"


def classify(d: str, pos_all: list[str]) -> str:
    if len(pos_all) == 1:
        return pos_all[0]
    low = d.lower()
    if low.startswith("to ") and "v" in pos_all:
        return "v"
    if low.startswith("classifier") and "m" in pos_all:
        return "m"
    for p in pos_all:
        if p != "v":
            return p
    return pos_all[0]


def build_senses(defs: list[str], pos_all: list[str], limit: int, max_senses: int) -> tuple[list[dict], list[str]]:
    """Group cleaned CEDICT definitions into ≤ max_senses senses of ≤ limit chars.
    Returns (senses, measure-word specs from 'CL:' entries)."""
    cls, kept = [], []
    for raw in defs:
        raw = raw.strip()
        if not raw:
            continue
        if raw.startswith("CL:"):
            cls.extend(x.strip() for x in raw[3:].split(","))
            continue
        kept.append(raw)
    usable = [clean_def(d) for d in kept if not _SKIP.match(d)]
    if not usable:  # e.g. a word that CEDICT only lists as "variant of X"
        usable = [clean_def(d) for d in kept]
    senses: list[dict] = []
    for d in usable:
        d = shorten(d, limit)
        p = classify(d, pos_all)
        if senses and senses[-1]["pos"] == p and len(senses[-1]["gloss"]) + 2 + len(d) <= limit:
            if d not in senses[-1]["gloss"].split("; "):
                senses[-1]["gloss"] += "; " + d
        elif len(senses) < max_senses:
            if not any(d == s["gloss"] for s in senses):
                senses.append({"pos": p, "gloss": d})
    return senses, cls


_REF_ONLY = re.compile(r"^(?:old |archaic )?(?:erhua (?:form|variant) of|variant of|see(?: also)?|same as|also written) "
                       r"(?:[^\s|\[\]]+\|)?([^\s|\[\]]+)(?:\[[^\]]+\])?", re.I)


def resolve_refs(defs: list[str], by_simp: dict[str, list[tuple]]) -> list[str]:
    """CEDICT sometimes only says 'erhua variant of 面条' or 'see 老头子'. Replace such
    entries with the referenced word's definitions (one level deep)."""
    content = [d for d in defs if d.strip() and not d.startswith("CL:")]
    if not content or not all(_REF_ONLY.match(d) for d in content):
        return defs
    out = [d for d in defs if d.startswith("CL:")]
    for d in content:
        target = _REF_ONLY.match(d).group(1)
        for _t, _p, tdefs in by_simp.get(target, []):
            out += [x for x in tdefs if not _REF_ONLY.match(x)]
    return out if len(out) > len([d for d in defs if d.startswith("CL:")]) else defs


def parse_cl(spec: str) -> dict | None:
    m = re.match(r"(?:[^|\[]+\|)?([^\[|]+)\[([^\]]+)\]", spec)
    if not m:
        return None
    return {"hanzi": m.group(1), "pinyin": " ".join(numeric_to_marked(s) for s in parse_cedict_pinyin(m.group(2)))}


# ---------------------------------------------------------------- sentences

PUNCT = set("，。！？、；：“”‘’（）《》…—·「」『』,.!?;:\"'()- ～~　")


class Leveler:
    """Assigns an HSK level to every jieba token of a sentence."""

    def __init__(self, recipe: Recipe):
        import jieba

        self.jieba = jieba
        jieba.setLogLevel(60)
        self.level: dict[str, int] = {}
        with open(RAW_DIR / "hsk30" / "hsk30.csv", encoding="utf-8") as f:
            for r in csv.DictReader(f):
                lv = 7 if r["Level"] == "7-9" else int(r["Level"])
                forms = [r["Simplified"]]
                if r["Variants"]:
                    forms += [v["Simplified"] for v in json.loads(r["Variants"])]
                for form in forms:
                    form = re.sub(r"[（(].*?[)）]|\d+$|…", "", form)
                    for part in form.split("|"):
                        if part and all(is_hanzi(c) for c in part):
                            if lv < self.level.get(part, 99):
                                self.level[part] = lv
        for w in self.level:
            if len(w) > 1:
                jieba.add_word(w, freq=50000)
        self.white = set(recipe.section("enrich").get("level_whitelist", []))

    def tokens(self, text: str) -> list[str]:
        return [t for t in self.jieba.lcut(text, HMM=False) if t.strip()]

    def token_level(self, tok: str) -> int:
        if all(c in PUNCT or c.isdigit() or c.isspace() for c in tok):
            return 0
        if tok in self.white:
            return 1
        if tok in self.level:
            return self.level[tok]
        # Greedy decomposition into list words; unknown characters count as 99.
        worst, i = 0, 0
        while i < len(tok):
            for L in range(min(4, len(tok) - i), 0, -1):
                piece = tok[i:i + L]
                if piece in self.level:
                    worst = max(worst, self.level[piece])
                    i += L
                    break
            else:
                if not tok[i].isdigit() and tok[i] not in PUNCT:
                    worst = 99
                i += 1
        return worst


def load_tatoeba(leveler: Leveler, max_tokens: int) -> dict[int, dict]:
    import opencc

    t2s = opencc.OpenCC("t2s")
    tdir = RAW_DIR / "tatoeba"
    links: dict[int, list[int]] = defaultdict(list)
    with open(tdir / "cmn-eng_links.tsv", encoding="utf-8") as f:
        for line in f:
            a, b = line.rstrip("\n").split("\t")[:2]
            links[int(a)].append(int(b))
    cmn: dict[int, dict] = {}
    seen_text: set[str] = set()
    with open(tdir / "cmn_sentences_detailed.tsv", encoding="utf-8") as f:
        for line in f:
            parts = line.rstrip("\n").split("\t")
            sid, text = int(parts[0]), parts[2].strip()
            if sid not in links or re.search(r"[A-Za-z]", text):
                continue
            # Traditional-script sentences are converted (OpenCC t2s); the audio is the same speech.
            simp = t2s.convert(text)
            converted = simp != text
            text = simp
            if text in seen_text and converted:
                continue
            seen_text.add(text)
            toks = leveler.tokens(text)
            words = [t for t in toks if leveler.token_level(t) > 0]
            if not 2 <= len(words) <= max_tokens:
                continue
            cmn[sid] = {"id": sid, "text": text, "tokens": toks, "user": parts[3], "converted": converted}
    need = {e for sid in cmn for e in links[sid]}
    eng: dict[int, str] = {}
    with open(tdir / "eng_sentences.tsv", encoding="utf-8") as f:
        for line in f:
            sid, _, text = line.rstrip("\n").split("\t", 2)
            if int(sid) in need:
                eng[int(sid)] = text
    for sid in list(cmn):
        trs = sorted((eng[e] for e in links[sid] if e in eng), key=len)
        if not trs:
            del cmn[sid]
        else:
            cmn[sid]["translation"] = trs[0]
    with open(tdir / "sentences_with_audio.csv", encoding="utf-8") as f:
        for line in f:
            p = line.rstrip("\n").split("\t")
            sid = int(p[0])
            if sid in cmn and "audio" not in cmn[sid]:
                cmn[sid]["audio"] = {"audio_id": int(p[1]), "user": p[2],
                                     "license": p[3] if len(p) > 3 and p[3] not in ("", "\\N") else None,
                                     "attribution": p[4] if len(p) > 4 and p[4] not in ("", "\\N") else None}
    log.info("enrich: %d usable Tatoeba zh-en sentences (%d with human audio)",
             len(cmn), sum(1 for s in cmn.values() if "audio" in s))
    return cmn


def sentence_reading(tokens: list[str], head: str, head_syllables: list[str]) -> str:
    from pypinyin import Style, pinyin

    out: list[str] = []
    for t in tokens:
        if all(c in PUNCT for c in t):
            p = t.translate(str.maketrans("，。！？、；：“”‘’（）《》", ",.!?,;:\"\"''()<>"))
            if out:
                out[-1] += p
            else:
                out.append(p)
            continue
        if t == head:
            out.append("".join(head_syllables))
        elif all(is_hanzi(c) for c in t):
            out.append("".join(s[0] for s in pinyin(t, style=Style.TONE, heteronym=False)))
        else:
            out.append(t)
    return " ".join(out)


def find_sentences(word: dict, cmn: dict[int, dict], by_char: dict[str, list[int]], leveler: Leveler,
                   cfg: dict, strict: bool = True) -> list[dict]:
    head = word["headword"]
    rare = min(head, key=lambda c: len(by_char.get(c, [])))
    level = word["level"]
    target = 4 + level
    scored = []
    for sid in by_char.get(rare, []):
        s = cmn[sid]
        text = s["text"]
        if head not in text:
            continue
        # The headword must sit on token boundaries (会 must not match inside 会议).
        starts, ends, pos = set(), set(), 0
        for t in s["tokens"]:
            i = text.find(t, pos)
            starts.add(i)
            ends.add(i + len(t))
            pos = i + len(t)
        hit = any(m.start() in starts and m.end() in ends for m in re.finditer(re.escape(head), text))
        if not hit and (strict or len(head) < 2):
            continue
        above = 0
        for t in s["tokens"]:
            if t == head:
                continue
            if leveler.token_level(t) > level:
                above += 1
        n = sum(1 for t in s["tokens"] if leveler.token_level(t) > 0)
        scored.append(((above, 0 if "audio" in s else 1, abs(n - target), len(s["translation"]), sid), s, above))
    scored.sort(key=lambda x: x[0])
    return [{"s": s, "above": above} for _, s, above in scored]


# ---------------------------------------------------------------- strokes


def load_mmah() -> tuple[dict[str, dict], dict[str, dict]]:
    g, d = {}, {}
    with open(RAW_DIR / "mmah" / "graphics.txt", encoding="utf-8") as f:
        for line in f:
            e = json.loads(line)
            g[e["character"]] = e
    with open(RAW_DIR / "mmah" / "dictionary.txt", encoding="utf-8") as f:
        for line in f:
            e = json.loads(line)
            d[e["character"]] = e
    return g, d


# ---------------------------------------------------------------- stage


def _extra_sentences(recipe: Recipe) -> dict[str, list[dict]]:
    out: dict[str, list[dict]] = defaultdict(list)
    for spec in recipe.section("enrich").get("extra_sentence_files", []):
        p = recipe.resolve(spec["path"])
        if not p.exists():
            continue
        for line in p.read_text(encoding="utf-8").splitlines():
            if not line.strip() or line.startswith("#"):
                continue
            key, zh, en = line.split("\t")[:3]
            out[key].append({"text": zh.strip(), "translation": en.strip(),
                             "source": spec["source"], "license": spec["license"]})
    return out


def run(recipe: Recipe) -> None:
    cfg = recipe.section("enrich")
    limit, max_senses = cfg["max_gloss_chars"], cfg["max_senses"]
    per_word, relaxed = cfg["sentences_per_word"], cfg["relaxed_max_above_level"]
    words = pl.read_parquet(recipe.work / "words.parquet").to_dicts()
    overrides = yaml.safe_load(recipe.resolve("config/zh_overrides.yaml").read_text(encoding="utf-8")) or {}
    unused_overrides = set(overrides) - {w["id"] for w in words}
    for k in sorted(unused_overrides):
        log.warning("enrich: override for unknown id %s", k)

    log.info("enrich: loading CC-CEDICT")
    cedict = load_cedict()
    by_simp: dict[str, list[tuple]] = defaultdict(list)
    for (t, s, p), defs in cedict.items():
        by_simp[s].append((t, p, defs))

    log.info("enrich: loading Tatoeba + jieba levels")
    leveler = Leveler(recipe)
    cmn = load_tatoeba(leveler, cfg["max_sentence_tokens"])
    by_char: dict[str, list[int]] = defaultdict(list)
    for sid, s in cmn.items():
        for c in set(s["text"]):
            by_char[c].append(sid)
    extra = _extra_sentences(recipe)

    from pypinyin import Style, pinyin

    stats = defaultdict(int)
    out = []
    for w in words:
        keys = json.loads(w["cedict_keys"])
        pos_all = list(w["pos_all"])
        # ---- glosses
        defs: list[str] = []
        for k in keys:
            defs += cedict.get((k["trad"], k["simp"], k["pinyin"]), [])
        if not defs:  # fall back to any CEDICT entry with the same simplified form and pinyin
            want = "".join(s.lower() for s in w["syllables"])
            for t, p, d in by_simp.get(w["headword"], []):
                if "".join(numeric_to_marked(x).lower() for x in parse_cedict_pinyin(p)) == want:
                    defs += d
        defs = resolve_refs(defs, by_simp)
        senses, cls = build_senses(defs, pos_all, limit, max_senses)
        gloss_source = "cc-cedict"
        if w["id"] in overrides:
            senses = overrides[w["id"]]["senses"]
            gloss_source = "yuki-override"
        elif not senses and w["chkv_meanings"]:
            senses, _ = build_senses(list(w["chkv_meanings"]), pos_all, limit, max_senses)
            gloss_source = "complete-hsk-vocabulary"
        stats[f"gloss_{gloss_source}"] += 1
        if not senses:
            stats["gloss_missing"] += 1

        # ---- measure words
        mws = [m for m in (parse_cl(c) for c in cls) if m]
        if not mws:
            for c in w["chkv_classifiers"] or []:
                mws.append({"hanzi": c, "pinyin": pinyin(c, style=Style.TONE)[0][0]})
        mws = list({m["hanzi"]: m for m in mws}.values())[:3]

        # ---- sentences
        chosen = []
        for cand in find_sentences(w, cmn, by_char, leveler, cfg):
            if cand["above"] > relaxed or len(chosen) >= per_word:
                break
            s = cand["s"]
            chosen.append({
                "id": f"tatoeba:{s['id']}", "text": s["text"],
                "reading": sentence_reading(s["tokens"], w["headword"], w["syllables"]),
                "translation": s["translation"], "above_level": cand["above"],
                "source": "tatoeba", "license": "CC BY 2.0 FR",
                "attribution": f"https://tatoeba.org/sentences/show/{s['id']} ({s['user']})",
                "audio": s.get("audio"),
            })
        if len(chosen) < per_word:
            for e in extra.get(w["id"], []) + extra.get(w["headword"], []):
                if len(chosen) >= per_word:
                    break
                toks = leveler.tokens(e["text"])
                above = sum(1 for t in toks if t != w["headword"] and leveler.token_level(t) > w["level"])
                chosen.append({
                    "id": f"yuki:{abs(hash(e['text'])) % 10**10}", "text": e["text"],
                    "reading": sentence_reading(toks, w["headword"], w["syllables"]),
                    "translation": e["translation"], "above_level": above,
                    "source": e["source"], "license": e["license"], "attribution": None, "audio": None,
                })
        if len(chosen) < per_word:
            # Last resort: the best Tatoeba sentence even if it uses harder words (flagged).
            # Multi-character words may also match across jieba token boundaries here.
            for cand in find_sentences(w, cmn, by_char, leveler, cfg, strict=False)[:per_word - len(chosen)]:
                s = cand["s"]
                if any(c["id"] == f"tatoeba:{s['id']}" for c in chosen):
                    continue
                chosen.append({
                    "id": f"tatoeba:{s['id']}", "text": s["text"],
                    "reading": sentence_reading(s["tokens"], w["headword"], w["syllables"]),
                    "translation": s["translation"], "above_level": cand["above"],
                    "source": "tatoeba", "license": "CC BY 2.0 FR",
                    "attribution": f"https://tatoeba.org/sentences/show/{s['id']} ({s['user']})",
                    "audio": s.get("audio"),
                })
        stats["sentences_none"] += not chosen
        stats["sentences_level_ok"] += bool(chosen) and chosen[0]["above_level"] == 0
        stats["sentences_human_audio"] += bool(chosen) and chosen[0]["audio"] is not None

        out.append({**w, "senses": json.dumps(senses, ensure_ascii=False), "gloss_source": gloss_source,
                    "measure_words": json.dumps(mws, ensure_ascii=False),
                    "sentences": json.dumps(chosen, ensure_ascii=False)})

    # ---- strokes
    log.info("enrich: loading Make Me a Hanzi")
    graphics, dictionary = load_mmah()
    chars: set[str] = set()
    for w in out:
        chars.update(c for c in w["headword"] if is_hanzi(c))
        for m in json.loads(w["measure_words"]):
            chars.update(c for c in m["hanzi"] if is_hanzi(c))
    hanzi, missing = [], []
    for ch in sorted(chars):
        g = graphics.get(ch)
        if not g:
            missing.append(ch)
            continue
        hanzi.append({"ch": ch, "stroke_count": len(g["strokes"]),
                      "radical": (dictionary.get(ch) or {}).get("radical"),
                      "strokes_json": json.dumps(g["strokes"], ensure_ascii=False, separators=(",", ":")),
                      "medians_json": json.dumps(g["medians"], separators=(",", ":"))})

    pl.DataFrame(out, infer_schema_length=None).write_parquet(recipe.work / "enriched.parquet")
    pl.DataFrame(hanzi).write_parquet(recipe.work / "hanzi.parquet")
    no_sentence = [w["id"] for w in out if not json.loads(w["sentences"])]
    report = {"words": len(out), **stats, "hanzi": len(hanzi), "hanzi_missing": missing,
              "words_without_sentence": no_sentence,
              "words_with_flagged_sentence": [w["id"] for w in out
                                              if json.loads(w["sentences"]) and json.loads(w["sentences"])[0]["above_level"] > relaxed]}
    write_json(recipe.work / "enrich.report.json", report)
    log.info("enrich: %s", {k: v for k, v in report.items() if not isinstance(v, list)})
    log.info("enrich: %d words without sentence, %d hanzi missing strokes", len(no_sentence), len(missing))
