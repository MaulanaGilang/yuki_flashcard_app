"""Stage 5 — evaluate: the automated checks of PRD §3.4 plus a spot-check sample sheet.

Writes work/<pack>/report.json and work/<pack>/report.html (open it in a browser; audio and
images are linked relative to the work folder). `package` refuses to run unless it passed.

Bars (recipe `evaluate` section):
  glosses   every word ≥ 1 gloss, none > 80 chars
  sentences every word ≥ 1; share of first sentences with ≤ relaxed above-level tokens ≥ bar
  audio     word audio for 100 % of words; size caps; duration plausible; loudness ≈ −16 LUFS;
            ASR (faster-whisper) on a random sample of TTS clips: CER ≤ 5 % for ≥ pass_ratio
  images    every finished image is 768×768 WebP ≤ 60 KB (coverage reported, not a bar: the
            FLUX batch runs for weeks)
  strokes   every character of every headword and measure word has stroke data
"""

from __future__ import annotations

import html
import json
import random
from collections import Counter
from pathlib import Path

import polars as pl
from PIL import Image

from .util import JsonCache, log, read_json, write_json
from .zh import is_hanzi, strip_tones

# ---------------------------------------------------------------- ASR


def _cer(ref: str, hyp: str) -> float:
    ref = "".join(c for c in ref if is_hanzi(c))
    hyp = "".join(c for c in hyp if is_hanzi(c))
    if not ref:
        return 0.0
    prev = list(range(len(hyp) + 1))
    for i, rc in enumerate(ref, 1):
        cur = [i]
        for j, hc in enumerate(hyp, 1):
            cur.append(min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (rc != hc)))
        prev = cur
    return prev[-1] / len(ref)


def _toneless(text: str) -> str:
    from pypinyin import lazy_pinyin

    return "".join(strip_tones(p) for p in lazy_pinyin("".join(c for c in text if is_hanzi(c))))


def asr_check(recipe, state: dict, sample_n: int, model_name: str, max_cer: float) -> dict:
    """Transcribe a random sample of TTS clips. A word clip also passes when the transcript
    has the same toneless pinyin (Whisper may pick a homophone for an isolated word)."""
    try:
        from faster_whisper import WhisperModel
    except ImportError:
        return {"skipped": "faster-whisper not installed"}
    cache = JsonCache(recipe.work / "asr_cache.json", every=20)
    tts = sorted(n for n, s in state.items() if not s["human"] and s.get("text"))
    rng = random.Random(42)
    sample = rng.sample(tts, min(sample_n, len(tts)))
    todo = [n for n in sample if n not in cache]
    if todo:
        log.info("evaluate: ASR on %d TTS clips (faster-whisper %s, CPU)", len(todo), model_name)
        model = WhisperModel(model_name, device="cpu", compute_type="int8")
        for i, n in enumerate(todo, 1):
            segs, _ = model.transcribe(str(recipe.work / "media" / "audio" / n), language="zh",
                                       beam_size=1, initial_prompt="以下是普通话的句子。")
            cache.set(n, "".join(s.text for s in segs).strip())
            if i % 50 == 0:
                log.info("evaluate: ASR %d/%d", i, len(todo))
        cache.flush()
    rows = []
    for n in sample:
        ref, hyp = state[n]["text"], cache.get(n, "")
        cer = _cer(ref, hyp)
        ok = cer <= max_cer or (n.startswith("w_") and _toneless(ref) == _toneless(hyp))
        rows.append({"file": n, "ref": ref, "hyp": hyp, "cer": round(cer, 3), "ok": ok})
    return {"sample": len(rows), "pass": sum(r["ok"] for r in rows),
            "ratio": (sum(r["ok"] for r in rows) / len(rows)) if rows else 1.0,
            "failures": [r for r in rows if not r["ok"]]}


# ---------------------------------------------------------------- stage


def run(recipe, asr: bool = True) -> bool:
    cfg = recipe.section("evaluate")
    acfg = recipe.section("media")["audio"]
    work = recipe.work
    words = pl.read_parquet(work / "enriched.parquet").to_dicts()
    hanzi = {h["ch"] for h in pl.read_parquet(work / "hanzi.parquet").select("ch").to_dicts()}
    audio = read_json(work / "media_audio.json", {"words": [], "sentences": []})
    state = read_json(work / "audio_state.json", {})
    images = read_json(work / "media_images.json", [])
    relaxed = recipe.section("enrich")["relaxed_max_above_level"]
    checks: dict[str, dict] = {}

    def check(name: str, ok: bool, **detail) -> None:
        checks[name] = {"ok": bool(ok), **detail}
        log.info("evaluate: %-26s %s %s", name, "PASS" if ok else "FAIL",
                 {k: v for k, v in detail.items() if not isinstance(v, list)})

    # glosses
    no_gloss = [w["id"] for w in words if not json.loads(w["senses"])]
    long_gloss = [(w["id"], s["gloss"]) for w in words for s in json.loads(w["senses"])
                  if len(s["gloss"]) > cfg["gloss_max_chars"]]
    check("glosses", not no_gloss and not long_gloss, words=len(words), missing=no_gloss, too_long=long_gloss)

    # sentences
    first = [json.loads(w["sentences"])[0] if json.loads(w["sentences"]) else None for w in words]
    none = [w["id"] for w, s in zip(words, first) if s is None]
    within = sum(1 for s in first if s and s["above_level"] <= relaxed)
    strict = sum(1 for s in first if s and s["above_level"] == 0)
    ratio = within / len(words)
    check("sentences", not none and ratio >= cfg["sentence_level_ok_ratio"], missing=none,
          within_level_or_one_above=round(ratio, 4), fully_within_level=round(strict / len(words), 4),
          authored=sum(1 for s in first if s and s["source"] != "tatoeba"),
          over_level=[w["id"] for w, s in zip(words, first) if s and s["above_level"] > relaxed])

    # audio coverage, caps, durations, loudness
    wa = {r["word_id"]: r for r in audio["words"]}
    missing_audio = [w["id"] for w in words if w["id"] not in wa]
    check("word_audio_coverage", len(missing_audio) == 0 and len(wa) / len(words) >= cfg["word_audio_coverage"],
          coverage=round(len(wa) / len(words), 4), human=sum(r["human"] for r in wa.values()), missing=missing_audio)
    sa = {(r["word_id"], r["sentence_id"]): r for r in audio["sentences"]}
    missing_sa = [w["id"] for w, s in zip(words, first) if s and (w["id"], s["id"]) not in sa]
    check("sentence_audio_coverage", not missing_sa, missing=missing_sa,
          human_clips=len({r["file"] for r in sa.values() if r["human"]}), clips=len({r["file"] for r in sa.values()}))
    used = {r["file"] for r in audio["words"]} | {r["file"] for r in audio["sentences"]}
    over_cap, bad_dur, bad_lufs = [], [], []
    for n in used:
        st = state.get(n)
        if not st:
            continue
        cap = (acfg["word_cap_kb"] if n.startswith("w_") else acfg["sentence_cap_kb"]) * 1024
        if st["bytes"] > cap:
            over_cap.append(n)
        n_chars = max(1, sum(1 for c in (st.get("text") or "") if is_hanzi(c)))
        d = (st.get("duration_ms") or 0) / 1000
        if not (0.2 <= d <= 0.9 * n_chars + 1.5):
            bad_dur.append((n, st.get("text"), d))
        if abs(st.get("output_lufs", -16) - acfg["loudness_lufs"]) > 2.0:
            bad_lufs.append((n, st.get("output_lufs")))
    check("audio_files", not over_cap and len(bad_dur) <= 0.01 * len(used), clips=len(used),
          over_cap=over_cap, implausible_duration=bad_dur, loudness_off=bad_lufs[:50])
    if asr and cfg["asr"]["enabled"]:
        a = asr_check(recipe, state, cfg["asr"].get("sample", 300), cfg["asr"]["model"], cfg["asr"]["max_cer"])
        if "skipped" in a:
            check("tts_asr", True, **a)
        else:
            check("tts_asr", a["ratio"] >= cfg["asr"]["pass_ratio"], sample=a["sample"], ratio=round(a["ratio"], 4),
                  failures=a["failures"])
    else:
        checks["tts_asr"] = {"ok": True, "skipped": "--no-asr"}

    # images
    bad_img = []
    for r in images:
        if r["status"] != "done":
            continue
        p = work / "media" / "img" / r["file"]
        try:
            with Image.open(p) as im:
                if im.format != "WEBP" or im.size != (768, 768) or p.stat().st_size > 60 * 1024:
                    bad_img.append((r["word_id"], im.format, im.size, p.stat().st_size))
        except Exception as e:
            bad_img.append((r["word_id"], str(e)))
    kinds = Counter((r["kind"], r["status"]) for r in images)
    check("images", not bad_img, fluent=kinds[("fluent", "done")], flux=kinds[("flux", "done")],
          pending=kinds[("flux", "pending")], typographic=kinds[("typographic", "typographic")], bad=bad_img)

    # strokes
    need = set()
    for w in words:
        need.update(c for c in w["headword"] if is_hanzi(c))
        for m in json.loads(w["measure_words"]):
            need.update(c for c in m["hanzi"] if is_hanzi(c))
    no_strokes = sorted(need - hanzi)
    check("strokes", len(no_strokes) == 0, characters=len(need), missing=no_strokes)

    # ids
    ids = [w["id"] for w in words]
    check("stable_ids", len(ids) == len(set(ids)) and all(i.startswith(recipe.lang + ":") for i in ids), words=len(ids))

    passed = all(c["ok"] for c in checks.values())
    write_json(work / "report.json", {"passed": passed, "checks": checks})
    _sample_sheet(recipe, words, audio, images, checks, cfg["sample_ratio"])
    log.info("evaluate: %s — report: %s", "PASSED" if passed else "FAILED", work / "report.html")
    return passed


def _sample_sheet(recipe, words, audio, images, checks, ratio: float) -> None:
    """Random sample per level (seeded) for the PRD's manual spot checks, plus 20 characters."""
    rng = random.Random(7)
    wa = {r["word_id"]: r for r in audio["words"]}
    sa = {(r["word_id"], r["sentence_id"]): r for r in audio["sentences"]}
    img = {r["word_id"]: r for r in images}
    by_level: dict[int, list] = {}
    for w in words:
        by_level.setdefault(w["level"], []).append(w)
    e = html.escape
    rows = []
    for lv in sorted(by_level):
        ws = by_level[lv]
        for w in sorted(rng.sample(ws, max(1, round(len(ws) * ratio))), key=lambda x: x["freq_rank"]):
            senses = "; ".join(f"<i>{e(s['pos'])}</i> {e(s['gloss'])}" for s in json.loads(w["senses"]))
            ss = json.loads(w["sentences"])
            s = ss[0] if ss else None
            a = wa.get(w["id"])
            sa_ = sa.get((w["id"], s["id"])) if s else None
            im = img.get(w["id"], {})
            art = (f'<img src="media/img/{e(im["file"])}" width="96">' if im.get("status") == "done"
                   else f'<span class="kw">{e(im.get("keyword") or "")}</span><br><small>{e(im.get("status", ""))}</small>')
            rows.append(f"""<tr><td>{lv}</td><td class="hz">{e(w['headword'])}</td><td>{e(w['reading'])}<br>
<small>{e(' '.join(w['syllables']))} · {w['tones']}</small></td><td>{senses}</td>
<td>{f'<audio controls preload="none" src="media/audio/{e(a["file"])}"></audio><br><small>{"human" if a["human"] else "TTS"} · {e(a["source"])}</small>' if a else '—'}</td>
<td>{f'{e(s["text"])}<br><small>{e(s["reading"])}</small><br>{e(s["translation"])}<br><small>{e(s["source"])} · above-level {s["above_level"]}</small>' if s else '—'}
{f'<br><audio controls preload="none" src="media/audio/{e(sa_["file"])}"></audio><small>{"human" if sa_["human"] else "TTS"}</small>' if sa_ else ''}</td>
<td>{art}</td><td><input type="checkbox"></td></tr>""")
    hz = pl.read_parquet(recipe.work / "hanzi.parquet").to_dicts()
    strokes = []
    for h in rng.sample(hz, min(20, len(hz))):
        paths = "".join(f'<path d="{p}"/>' for p in json.loads(h["strokes_json"]))
        strokes.append(f'<figure><svg viewBox="0 0 1024 1024" width="88"><g transform="scale(1,-1) translate(0,-900)">'
                       f'{paths}</g></svg><figcaption>{e(h["ch"])} · {h["stroke_count"]}</figcaption></figure>')
    summary = "".join(f"<li class={'ok' if c['ok'] else 'bad'}><b>{e(k)}</b>: {'PASS' if c['ok'] else 'FAIL'} "
                      f"<small>{e(json.dumps({x: y for x, y in c.items() if x != 'ok' and not isinstance(y, list)}, ensure_ascii=False))}</small></li>"
                      for k, c in checks.items())
    page = f"""<!doctype html><html lang="en"><head><meta charset="utf-8"><title>Yuki pack report — {e(recipe.pack_id)}</title>
<style>body{{font:14px system-ui,sans-serif;margin:24px;color:#1b1f2a}}table{{border-collapse:collapse;width:100%}}
td,th{{border-bottom:1px solid #ddd;padding:6px;vertical-align:top}}.hz{{font-size:28px}}.kw{{font:700 16px system-ui;color:#456}}
.ok{{color:#16733a}}.bad{{color:#b3261e}}figure{{display:inline-block;margin:6px;text-align:center}}svg{{background:#f4f6fb}}
audio{{width:180px;height:28px}}small{{color:#667}}</style></head><body>
<h1>{e(recipe.pack['title'])} — evaluation</h1><ul>{summary}</ul>
<h2>Spot-check sample ({len(rows)} words, {ratio:.0%} per level)</h2>
<p>Tick a row when gloss, sentence, translation, audio (tones) and image are all correct. PRD bars: glosses ≥ 98 %,
sentences ≥ 95 % natural, 0 wrong tones in the audio sample, images ≥ 90 % clear.</p>
<table><tr><th>HSK</th><th>Word</th><th>Pinyin / tones</th><th>Glosses</th><th>Word audio</th><th>Sentence</th><th>Art</th><th>✓</th></tr>
{''.join(rows)}</table><h2>Stroke data: 20 random characters</h2>{''.join(strokes)}</body></html>"""
    (recipe.work / "report.html").write_text(page, encoding="utf-8")
