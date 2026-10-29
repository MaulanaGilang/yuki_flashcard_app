"""Stage 2 — normalize: one Word record per HSK 3.0 entry (levels from the recipe).

Output: work/<pack>/words.parquet (+ words.jsonl for easy diffing).
Stable id: "zh:<headword>:<numbered pinyin>:<pos>", e.g. "zh:苹果:ping2guo3:n".
A second entry that would collide (称1/称2, 面1/面2) gets ":2" appended.
"""

from __future__ import annotations

import csv
import json
import re

import polars as pl

from .config import RAW_DIR, Recipe
from .util import log, write_json
from .zh import parse_cedict_pinyin, segment_marked, strip_tones, syllables_from

CHKV_POS = {
    "n": "n", "nr": "n", "ns": "n", "nt": "n", "nz": "n", "nx": "n", "t": "n", "s": "n", "f": "n", "vn": "n", "an": "n",
    "v": "v", "vd": "v", "a": "adj", "ad": "adj", "b": "adj", "d": "adv", "r": "pron", "m": "num", "q": "m",
    "p": "prep", "c": "conj", "u": "part", "y": "part", "e": "intj", "o": "onom", "i": "idiom", "l": "phr",
    "h": "prefix", "k": "suffix", "mq": "num",
}
_CEDICT_KEY = re.compile(r"^(?P<t>[^|]+)\|(?P<s>[^\[]+)\[(?P<p>[^\]]+)\]$")


def _cedict_keys(field: str) -> list[dict]:
    out = []
    for k in (field or "").split("/"):
        m = _CEDICT_KEY.match(k.strip())
        if m:
            out.append({"trad": m["t"], "simp": m["s"], "pinyin": m["p"]})
    return out


def _load_chkv() -> dict[str, dict]:
    with open(RAW_DIR / "chkv" / "complete.min.json", encoding="utf-8") as f:
        data = json.load(f)
    return {e["s"]: e for e in data}


def _pick_form(entry: dict | None, numeric: list[str]) -> dict | None:
    if not entry:
        return None
    want = "".join(numeric).lower()
    for form in entry.get("f", []):
        if form["i"]["n"].replace(" ", "").lower() == want:
            return form
    return None


def run(recipe: Recipe) -> pl.DataFrame:
    cfg = recipe.section("normalize")
    levels = {str(x) for x in recipe.pack["levels"]}
    pos_map = cfg["pos_map"]
    chkv = _load_chkv()

    with open(RAW_DIR / "hsk30" / "hsk30.csv", encoding="utf-8") as f:
        rows = [r for r in csv.DictReader(f) if r["Level"] in levels]
    log.info("normalize: %d HSK entries in levels %s", len(rows), sorted(levels))

    words, seen, problems = [], {}, []
    for r in rows:
        variants = json.loads(r["Variants"]) if r["Variants"] else []
        pattern = example = None
        others: list[dict] = []
        if variants:
            main = [v for v in variants if not v.get("Example")]
            examples = [v for v in variants if v.get("Example")]
            head = main[0]
            others = [{"headword": v["Simplified"], "reading": v["Pinyin"]} for v in main[1:]]
            if examples:
                example = examples[0]["Simplified"]
            headword, reading = head["Simplified"], head["Pinyin"]
            pos_field, cedict_field = head.get("POS") or r["POS"], head.get("CEDICT") or ""
            if "…" in r["Simplified"]:
                pattern = r["Simplified"]
        else:
            headword, reading, pos_field, cedict_field = r["Simplified"], r["Pinyin"], r["POS"], r["CEDICT"]
        headword = re.sub(r"\d+$", "", headword)

        keys = _cedict_keys(cedict_field) or _cedict_keys(r["CEDICT"])
        entry = chkv.get(headword)
        candidates = [parse_cedict_pinyin(k["pinyin"]) for k in keys if k["simp"] == headword]
        if entry:
            candidates += [f["i"]["n"].split() for f in entry.get("f", [])]
        sy = syllables_from(headword, candidates, reading) or segment_marked(headword, reading)
        if sy is None:
            problems.append(f"{r['ID']} {headword} {reading}: no per-character syllables")
            continue
        syllables, tones = sy
        numeric = [strip_tones(s) + str(t) for s, t in zip(syllables, tones)]
        form = _pick_form(entry, numeric)

        pos_all = [pos_map.get(p, p.lower()) for p in pos_field.split("/") if p] if pos_field else []
        if not pos_all and entry:
            pos_all = list(dict.fromkeys(CHKV_POS.get(p, "x") for p in entry.get("p", [])))
        if headword in cfg.get("pos_fix", {}):
            pos_all = [cfg["pos_fix"][headword]]
        if not pos_all:
            pos_all = ["phr"]
        pos = pos_all[0]

        wid = f"zh:{headword}:{''.join(numeric)}:{pos}"
        if wid in seen:
            n = 2
            while f"{wid}:{n}" in seen:
                n += 1
            wid = f"{wid}:{n}"
        seen[wid] = r["ID"]

        hsk2 = None
        if entry:
            old = [int(l[1:]) for l in entry.get("l", []) if l.startswith("o") and l[1:].isdigit()]
            hsk2 = min(old) if old else None

        words.append({
            "id": wid,
            "hsk_id": r["ID"],
            "headword": headword,
            "trad": (keys[0]["trad"] if keys else r["Traditional"].split("|")[0]),
            "reading": reading,
            "syllables": syllables,
            "tones": tones,
            "level": int(r["Level"]),
            "level_label": recipe.pack["level_label"].format(level=r["Level"]),
            "pos": pos,
            "pos_all": pos_all,
            "cedict_keys": json.dumps(keys, ensure_ascii=False),
            "variants": json.dumps(others, ensure_ascii=False),
            "example": example,
            "pattern": pattern,
            "hsk2_level": hsk2,
            "freq": (entry or {}).get("q"),
            "chkv_meanings": (form or {}).get("m", []) if form else ((entry or {}).get("f", [{}])[0].get("m", []) if entry else []),
            "chkv_classifiers": (form or {}).get("c", []) if form else [],
        })

    for p in problems:
        log.warning("normalize: %s", p)
    order = sorted(range(len(words)), key=lambda i: (words[i]["freq"] or 10**9, words[i]["level"], words[i]["hsk_id"]))
    for rank, i in enumerate(order, 1):
        words[i]["freq_rank"] = rank

    df = pl.DataFrame(words, infer_schema_length=None)
    df.write_parquet(recipe.work / "words.parquet")
    write_json(recipe.work / "normalize.report.json", {
        "words": len(words), "skipped": problems,
        "by_level": {str(k): v for k, v in sorted(df.group_by("level").len().iter_rows())},
    })
    log.info("normalize: wrote %d words (%d skipped)", len(words), len(problems))
    return df
