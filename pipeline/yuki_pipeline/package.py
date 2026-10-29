"""Stage 6 — package: write packs/<id>-v<n>/ in the exact schema of ARCHITECTURE.md §2.4.

  pack.db        pack_meta, word, sense, sentence, asset, hanzi (+ the two indexes)
  audio/*.opus   word and sentence clips referenced from pack.db
  img/*.webp     stickers referenced from pack.db (images still in the FLUX queue have none)
  credits.json   source/licence/attribution of every sentence and sentence clip
  manifest.json  sha256 of every file, sources with licences, counts

Next to the folder: <id>-v<n>.kpack (zip of the folder) and <id>-v<n>.chars.txt (every
character used, for tools/subset_fonts.py --extra-chars-file).

The run refuses to package unless work/<pack>/report.json from `evaluate` passed.
"""

from __future__ import annotations

import json
import shutil
import sqlite3
import zipfile
from datetime import datetime, timezone

import polars as pl

from .config import RAW_DIR, Recipe
from .util import log, read_json, sha256_file, write_json
from .zh import is_hanzi, search_key

SCHEMA = """
CREATE TABLE pack_meta(key TEXT PRIMARY KEY, value TEXT);
CREATE TABLE word(id TEXT PRIMARY KEY, lang TEXT, headword TEXT, reading TEXT, search_key TEXT,
  level INTEGER, level_label TEXT, freq_rank INTEGER, pos TEXT, extra_json TEXT);
CREATE TABLE sense(word_id TEXT, ord INTEGER, pos TEXT, gloss TEXT);
CREATE TABLE sentence(id TEXT PRIMARY KEY, word_id TEXT, ord INTEGER, text TEXT, reading TEXT, translation TEXT,
  audio TEXT, audio_human INTEGER);
CREATE TABLE asset(word_id TEXT, kind TEXT, path TEXT, human INTEGER, source TEXT, license TEXT, attribution TEXT);
CREATE TABLE hanzi(ch TEXT PRIMARY KEY, stroke_count INTEGER, radical TEXT, strokes_json TEXT, medians_json TEXT);
CREATE INDEX word_level ON word(level, freq_rank);
CREATE INDEX word_search ON word(search_key);
"""


def run(recipe: Recipe, force: bool = False) -> None:
    work = recipe.work
    report = read_json(work / "report.json")
    if not force and not (report and report.get("passed")):
        raise SystemExit("package: run `evaluate` first (work/<pack>/report.json missing or failed); --force to override")

    words = pl.read_parquet(work / "enriched.parquet").sort(["level", "freq_rank"]).to_dicts()
    hanzi = pl.read_parquet(work / "hanzi.parquet").to_dicts()
    audio = read_json(work / "media_audio.json", {"words": [], "sentences": []})
    images = {r["word_id"]: r for r in read_json(work / "media_images.json", [])}
    word_audio = {r["word_id"]: r for r in audio["words"]}
    sent_audio = {(r["word_id"], r["sentence_id"]): r for r in audio["sentences"]}

    out = recipe.out_dir
    if out.exists():
        shutil.rmtree(out)
    (out / "audio").mkdir(parents=True)
    (out / "img").mkdir(parents=True)
    con = sqlite3.connect(out / "pack.db")
    con.executescript(SCHEMA)
    built_at = datetime.now(timezone.utc).isoformat(timespec="seconds")
    meta = {
        "id": recipe.pack_id, "lang": recipe.lang, "title": recipe.pack["title"],
        "levels": ",".join(str(x) for x in recipe.pack["levels"]), "version": str(recipe.pack["version"]),
        "schema_version": str(recipe.pack["schema_version"]), "built_at": built_at, "word_count": str(len(words)),
    }
    con.executemany("INSERT INTO pack_meta VALUES (?, ?)", list(meta.items()))

    audio_dir, img_dir = work / "media" / "audio", work / "media" / "img"
    copied: set[str] = set()

    def copy(src_dir, name, dest_dir) -> str:
        rel = f"{dest_dir}/{name}"
        if rel not in copied:
            shutil.copy2(src_dir / name, out / rel)
            copied.add(rel)
        return rel

    credits = {"sentences": [], "word_audio_note": "see asset table"}
    chars: set[str] = set()
    counts = {"words": 0, "word_audio": 0, "word_audio_human": 0, "sentences": 0, "sentence_audio_human": 0,
              "images": 0, "images_fluent": 0, "images_flux": 0, "typographic": 0, "image_pending": 0}
    for w in words:
        senses = json.loads(w["senses"])
        mws = json.loads(w["measure_words"])
        img = images.get(w["id"], {})
        extra = {"syllables": list(w["syllables"]), "tones": list(w["tones"])}
        if mws:
            extra["measure_word"] = mws[0]["hanzi"]
            extra["measure_words"] = mws
        if w["hsk2_level"] is not None:
            extra["hsk2_level"] = w["hsk2_level"]
        extra["hsk_id"] = w["hsk_id"]
        if w["trad"] and w["trad"] != w["headword"]:
            extra["traditional"] = w["trad"]
        if len(w["pos_all"]) > 1:
            extra["pos_all"] = list(w["pos_all"])
        variants = json.loads(w["variants"])
        if variants:
            extra["variants"] = variants
        if w["pattern"]:
            extra["pattern"] = w["pattern"]
        if w["example"]:
            extra["example"] = w["example"]
        if img.get("status") != "done":
            # No art yet (typographic, or still in the FLUX queue): the art window shows this keyword.
            extra["image"] = "typographic" if img.get("kind") != "flux" else "pending"
            extra["keyword"] = img.get("keyword")
        con.execute("INSERT INTO word VALUES (?,?,?,?,?,?,?,?,?,?)", (
            w["id"], recipe.lang, w["headword"], w["reading"],
            search_key(w["headword"], list(w["syllables"]), list(w["tones"]), [s["gloss"] for s in senses]),
            w["level"], w["level_label"], w["freq_rank"], w["pos"], json.dumps(extra, ensure_ascii=False)))
        con.executemany("INSERT INTO sense VALUES (?,?,?,?)",
                        [(w["id"], i, s["pos"], s["gloss"]) for i, s in enumerate(senses)])
        for i, s in enumerate(json.loads(w["sentences"])):
            a = sent_audio.get((w["id"], s["id"]))
            path = copy(audio_dir, a["file"], "audio") if a else None
            sid = f"{w['id']}#s{i}"
            con.execute("INSERT INTO sentence VALUES (?,?,?,?,?,?,?,?)", (
                sid, w["id"], i, s["text"], s["reading"], s["translation"], path, int(a["human"]) if a else 0))
            credits["sentences"].append({"id": sid, "source": s["source"], "license": s["license"],
                                         "attribution": s.get("attribution"),
                                         "audio": None if not a else {k: a[k] for k in ("source", "license", "attribution")}})
            chars.update(s["text"])
            counts["sentences"] += 1
            counts["sentence_audio_human"] += bool(a and a["human"])
        wa = word_audio.get(w["id"])
        if wa:
            con.execute("INSERT INTO asset VALUES (?,?,?,?,?,?,?)", (
                w["id"], "word_audio", copy(audio_dir, wa["file"], "audio"), int(wa["human"]),
                wa["source"], wa["license"], wa["attribution"]))
            counts["word_audio"] += 1
            counts["word_audio_human"] += int(wa["human"])
        if img.get("status") == "done":
            con.execute("INSERT INTO asset VALUES (?,?,?,?,?,?,?)", (
                w["id"], "image", copy(img_dir, img["file"], "img"), 0,
                img["source"], img["license"], img.get("attribution")))
            counts["images"] += 1
            counts["images_fluent" if img["kind"] == "fluent" else "images_flux"] += 1
        elif img.get("kind") == "flux":
            counts["image_pending"] += 1
        else:
            counts["typographic"] += 1
        chars.update(w["headword"])
        counts["words"] += 1

    con.executemany("INSERT INTO hanzi VALUES (?,?,?,?,?)", [
        (h["ch"], h["stroke_count"], h["radical"], h["strokes_json"], h["medians_json"]) for h in hanzi])
    con.commit()
    con.execute("VACUUM")
    con.close()

    write_json(out / "credits.json", credits, indent=None)
    lock = read_json(RAW_DIR / "fetch.lock.json", {})
    sources = sorted({(v.get("source"), v.get("url"), v.get("license")) for v in lock.values()})
    files = sorted(p for p in out.rglob("*") if p.is_file())
    manifest = {
        **{k: meta[k] for k in ("id", "lang", "title", "built_at")},
        "version": recipe.pack["version"], "schema_version": recipe.pack["schema_version"],
        "levels": recipe.pack["levels"], "counts": counts,
        "sources": [{"name": n, "url": u, "license": l} for n, u, l in sources],
        "files": {str(p.relative_to(out)).replace("\\", "/"): sha256_file(p) for p in files},
    }
    write_json(out / "manifest.json", manifest)

    (out.parent / f"{recipe.versioned_id}.chars.txt").write_text(
        "".join(sorted(c for c in chars if is_hanzi(c))), encoding="utf-8")
    kpack = out.parent / f"{recipe.versioned_id}.kpack"
    with zipfile.ZipFile(kpack, "w", zipfile.ZIP_STORED) as z:  # opus/webp are already compressed
        for p in sorted(out.rglob("*")):
            if p.is_file():
                z.write(p, str(p.relative_to(out)).replace("\\", "/"))
    size_mb = sum(p.stat().st_size for p in files) / 1e6
    log.info("package: %s — %s, %.1f MB", out, counts, size_mb)
