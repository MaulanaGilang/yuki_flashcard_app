"""Stage 4a — media/audio: pick a human recording per word (else TTS) and per sentence
(Tatoeba human audio, else TTS), then trim silence, normalise to −16 LUFS and encode
Opus ≈32 kbps.

Word sources in order:
  1. audio-cmn HSK words (cmn-<hanzi>.mp3, speaker Yue Tan, CC BY-SA)
     — except polyphonic single characters, which go to 2. so the reading is guaranteed
  2. audio-cmn syllables (cmn-<pinyin><tone>.mp3, speaker Chen Wang) for single characters
  3. Lingua Libre (Wikimedia Commons, CC BY-SA 4.0)
  4. edge-tts (recipe voice), flagged human = 0

Every output is cached in work/<pack>/audio_state.json keyed by a signature of
(source, text/voice, encoder settings); a rerun only processes what changed.
"""

from __future__ import annotations

import asyncio
import json
import re
import subprocess
import time
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

import polars as pl
import requests

from .config import RAW_DIR, Recipe
from .util import JsonCache, ffmpeg_exe, log, read_json, sha1_text, write_json
from .zh import strip_tones

UA = {"User-Agent": "yuki-pipeline/0.1 (personal offline flashcards; contact via GitHub)"}
ENCODER_VERSION = "3"

# ---------------------------------------------------------------- encoding


def _trim_chain(pad_s: float) -> str:
    sr = f"silenceremove=start_periods=1:start_silence={pad_s}:start_threshold=-50dB"
    return f"{sr},areverse,{sr},areverse"


def encode(src: Path, out: Path, lufs: float, bitrates: list[str], cap_bytes: int, pad_ms: int) -> dict:
    """Measure integrated loudness (EBU R128), apply one linear gain to reach `lufs`
    (a fast limiter keeps peaks ≤ −1 dBFS), encode Opus. Lowers the bitrate until the
    file fits the cap. Clips are padded with silence for the measurement only, because
    loudnorm/ebur128 cannot measure clips shorter than one gating block reliably;
    silence is gated out of the integrated value."""
    ff = ffmpeg_exe()
    trim = _trim_chain(pad_ms / 1000)
    r = subprocess.run([ff, "-hide_banner", "-nostats", "-i", str(src), "-af",
                        f"{trim},apad=whole_dur=3,ebur128=peak=sample", "-f", "null", "-"],
                       capture_output=True, text=True, encoding="utf-8", errors="replace")
    summary = r.stderr[r.stderr.rfind("Summary:"):]
    mi = re.search(r"I:\s+(-?[\d.]+|-inf) LUFS", summary)
    mp = re.search(r"Peak:\s+(-?[\d.]+|-inf) dBFS", summary)
    if not mi or mi.group(1) == "-inf" or float(mi.group(1)) < -60:
        raise RuntimeError(f"silent or unmeasurable audio: {src}")
    measured = float(mi.group(1))
    peak = float(mp.group(1)) if mp and mp.group(1) != "-inf" else -20.0
    gain = lufs - measured
    out.parent.mkdir(parents=True, exist_ok=True)
    tmp = out.with_suffix(".tmp.opus")
    for br in bitrates:
        r = subprocess.run([ff, "-hide_banner", "-nostats", "-y", "-i", str(src), "-af", f"{trim},volume={gain:.2f}dB,alimiter=limit=0.89:level=0:attack=2:release=40",
                            "-ac", "1", "-ar", "48000", "-map_metadata", "-1", "-c:a", "libopus", "-b:a", br,
                            "-application", "voip", str(tmp)],
                           capture_output=True, text=True, encoding="utf-8", errors="replace")
        if r.returncode != 0:
            raise RuntimeError(f"encode failed for {src}: {r.stderr[-400:]}")
        if tmp.stat().st_size <= cap_bytes:
            break
    else:
        tmp.unlink()
        raise RuntimeError(f"over the {cap_bytes // 1024} KB cap even at {br}: {src}")
    tmp.replace(out)
    d = re.findall(r"time=(\d+):(\d+):([\d.]+)", r.stderr)
    dur = (int(d[-1][0]) * 3600 + int(d[-1][1]) * 60 + float(d[-1][2])) if d else None
    return {"bytes": out.stat().st_size, "duration_ms": int(dur * 1000) if dur else None, "bitrate": br,
            "input_lufs": measured, "gain_db": round(gain, 2), "output_lufs": round(measured + gain, 2)}


# ---------------------------------------------------------------- sources


class RateLimited(Exception):
    pass


def _http_get(url: str, dest: Path, tries: int = 4) -> Path:
    if dest.exists() and dest.stat().st_size > 0:
        return dest
    dest.parent.mkdir(parents=True, exist_ok=True)
    for i in range(tries):
        try:
            r = requests.get(url, headers=UA, timeout=60)
            if r.status_code == 429:
                # Wikimedia answers 429 with Retry-After: 600 for the whole IP; give up for this run.
                raise RateLimited(f"{url} -> 429 (retry after {r.headers.get('retry-after')} s)")
            r.raise_for_status()
            dest.write_bytes(r.content)
            return dest
        except requests.RequestException as e:
            log.warning("GET %s failed (%s), retry", url, e)
            time.sleep(3 * (i + 1))
    raise RuntimeError(f"could not download {url}")


def lingua_libre_index(cfg: dict) -> dict[str, list[dict]]:
    """Map simplified headword -> Commons files from the Lingua Libre cmn category (cached)."""
    import opencc

    cache = RAW_DIR / "lingualibre" / "cmn_index.json"
    data = read_json(cache)
    if data is None:
        files, cont = [], {}
        while True:
            params = {"action": "query", "list": "categorymembers", "cmtitle": f"Category:{cfg['category']}",
                      "cmtype": "file", "cmlimit": "500", "format": "json", **cont}
            j = requests.get("https://commons.wikimedia.org/w/api.php", params=params, headers=UA, timeout=60).json()
            files += [m["title"] for m in j["query"]["categorymembers"]]
            if "continue" not in j:
                break
            cont = j["continue"]
        data = {"category": cfg["category"], "files": files}
        write_json(cache, data)
    t2s = opencc.OpenCC("t2s")
    idx: dict[str, list[dict]] = {}
    for title in data["files"]:
        m = re.match(r"^File:LL-Q\d+ \(cmn\)-(.+?)-(.+)\.(wav|ogg|flac|mp3)$", title)
        if not m:
            continue
        user, word = m.group(1), m.group(2).strip()
        idx.setdefault(t2s.convert(word), []).append({"title": title, "user": user, "trad": t2s.convert(word) != word})
    return idx


def _polyphonic(ch: str) -> bool:
    from pypinyin import Style, pinyin

    readings = {strip_tones(p) + "" for p in pinyin(ch, style=Style.TONE3, heteronym=True)[0]}
    return len(readings) > 1


def pick_word_source(w: dict, ll_idx: dict, cfg: dict) -> dict:
    head = w["headword"]
    acmn = RAW_DIR / "audio-cmn" / "64k"
    single = len(head) == 1
    syl_file = None
    if single and w["tones"][0] in (1, 2, 3, 4):  # neutral-tone syllable files are unreliable
        syl = strip_tones(w["syllables"][0]).replace("ü", "v") + str(w["tones"][0])
        f = acmn / "syllabs" / f"cmn-{syl}.mp3"
        syl_file = f if f.exists() else None
    hsk_file = acmn / "hsk" / f"cmn-{head}.mp3"
    acmn_lic, acmn_attr = "CC BY-SA", "audio-cmn (hugolpz) / Shtooka cmn-caen-tan, speaker Yue Tan"
    if hsk_file.exists() and not (single and syl_file and _polyphonic(head)):
        return {"kind": "file", "path": hsk_file, "human": 1, "source": "audio-cmn",
                "license": acmn_lic, "attribution": acmn_attr}
    if syl_file:
        return {"kind": "file", "path": syl_file, "human": 1, "source": "audio-cmn-syllable",
                "license": acmn_lic, "attribution": "audio-cmn (hugolpz) syllables, speaker Chen Wang"}
    if hsk_file.exists():
        return {"kind": "file", "path": hsk_file, "human": 1, "source": "audio-cmn",
                "license": acmn_lic, "attribution": acmn_attr}
    if head in ll_idx:
        cands = sorted(ll_idx[head], key=lambda c: (c["trad"], c["title"]))
        c = cands[0]
        return {"kind": "commons", "title": c["title"], "human": 1, "source": "lingua-libre",
                "license": cfg["lingua_libre"]["license"],
                "attribution": f"{c['user']}, Lingua Libre — https://commons.wikimedia.org/wiki/{c['title'].replace(' ', '_')}"}
    return {"kind": "tts", "text": head, "human": 0, "source": f"edge-tts:{cfg['tts']['voice']}",
            "license": "generated (Microsoft Edge neural TTS, personal use)", "attribution": None}


def pick_sentence_source(s: dict, cfg: dict) -> dict:
    a = s.get("audio")
    if a:
        lic = a.get("license") or "Tatoeba audio, licence not specified (personal use)"
        return {"kind": "tatoeba", "audio_id": a["audio_id"], "human": 1, "source": "tatoeba-audio",
                "license": lic, "attribution": f"{a['user']} via Tatoeba (sentence {s['id'].split(':')[1]})"}
    return {"kind": "tts", "text": s["text"], "human": 0, "source": f"edge-tts:{cfg['tts']['voice']}",
            "license": "generated (Microsoft Edge neural TTS, personal use)", "attribution": None}


# ---------------------------------------------------------------- TTS


async def _tts_batch(jobs: list[tuple[str, Path]], voice: str, rate: str, concurrency: int = 4) -> list[str]:
    import edge_tts

    sem = asyncio.Semaphore(concurrency)
    errors: list[str] = []
    done = 0

    async def one(text: str, dest: Path) -> None:
        nonlocal done
        async with sem:
            for attempt in range(5):
                try:
                    tmp = dest.with_suffix(".part")
                    await edge_tts.Communicate(text, voice, rate=rate).save(str(tmp))
                    if tmp.stat().st_size < 500:
                        raise RuntimeError("empty TTS output")
                    tmp.replace(dest)
                    break
                except Exception as e:  # network hiccups, throttling
                    log.debug("tts retry %d for %s: %s", attempt + 1, text, e)
                    if attempt == 4:
                        errors.append(f"{text}: {e}")
                    await asyncio.sleep(2 * (attempt + 1))
            done += 1
            if done % 200 == 0:
                log.info("tts: %d/%d", done, len(jobs))

    await asyncio.gather(*(one(t, d) for t, d in jobs))
    return errors


def tts_raw_path(recipe: Recipe, text: str, voice: str, rate: str) -> Path:
    return recipe.work / "tts_raw" / f"{sha1_text(voice, rate, text)[:16]}.mp3"


# ---------------------------------------------------------------- stage


def run(recipe: Recipe, limit: int | None = None) -> None:
    cfg = recipe.section("media")["audio"]
    tts = cfg["tts"]
    lufs, pad = cfg["loudness_lufs"], cfg["silence_pad_ms"]
    out_dir = recipe.work / "media" / "audio"
    words = pl.read_parquet(recipe.work / "enriched.parquet").to_dicts()
    if limit:
        words = words[:limit]

    log.info("audio: indexing Lingua Libre")
    ll_idx = lingua_libre_index(cfg["lingua_libre"])

    # ---- plan every clip
    plan: dict[str, dict] = {}  # out name -> job
    word_map: dict[str, str] = {}
    sent_map: dict[str, str] = {}
    for w in words:
        src = pick_word_source(w, ll_idx, cfg)
        name = f"w_{sha1_text(w['id'])[:12]}.opus"
        plan[name] = {"text": w["headword"], **src, "cap": cfg["word_cap_kb"] * 1024, "for": w["id"]}
        word_map[w["id"]] = name
        for s in json.loads(w["sentences"]):
            src = pick_sentence_source(s, cfg)
            name = f"s_{sha1_text(s['text'])[:12]}.opus"
            # A sentence shared by several words is encoded once; human audio wins.
            if name not in plan or (src["human"] and not plan[name]["human"]):
                plan[name] = {"text": s["text"], **src, "cap": cfg["sentence_cap_kb"] * 1024, "for": s["id"]}
            sent_map[f"{w['id']}|{s['id']}"] = name

    # ---- fetch remote sources (Commons, Tatoeba) and synthesise TTS
    for name, job in plan.items():
        if job["kind"] == "commons":
            fn = job["title"][5:]
            job["path"] = RAW_DIR / "lingualibre" / "files" / f"{sha1_text(fn)[:16]}{Path(fn).suffix}"
        elif job["kind"] == "tatoeba":
            job["path"] = RAW_DIR / "tatoeba_audio" / f"{job['audio_id']}.mp3"
        elif job["kind"] == "tts":
            job["path"] = tts_raw_path(recipe, job["text"], tts["voice"], tts["rate"])
        job["sig"] = sha1_text(ENCODER_VERSION, str(lufs), cfg["bitrate"], job["kind"],
                               str(job.get("title") or job.get("audio_id") or job.get("text") or job["path"].name))

    state = JsonCache(recipe.work / "audio_state.json", every=100)
    todo = {n: j for n, j in plan.items()
            if not (state.get(n, {}).get("sig") == j["sig"] and (out_dir / n).exists())}
    log.info("audio: %d clips planned, %d to (re)build", len(plan), len(todo))

    remote = [j for j in todo.values() if j["kind"] in ("commons", "tatoeba") and not j["path"].exists()]
    if remote:
        log.info("audio: downloading %d human recordings (Commons/Tatoeba)", len(remote))

        def dl(j: dict) -> None:
            if j["kind"] == "commons":
                url = "https://commons.wikimedia.org/wiki/Special:FilePath/" + requests.utils.quote(j["title"][5:])
            else:
                url = f"https://tatoeba.org/audio/download/{j['audio_id']}"
            _http_get(url, j["path"])

        # Wikimedia rate-limits parallel file fetches: Commons one at a time, Tatoeba in parallel.
        commons = [j for j in remote if j["kind"] == "commons"]
        with ThreadPoolExecutor(4) as ex:
            futs = [ex.submit(dl, j) for j in remote if j["kind"] == "tatoeba"]
            for k, j in enumerate(commons):
                try:
                    dl(j)
                except RateLimited as e:
                    log.warning("audio: Commons rate limit, skipping %d Lingua Libre files this run (%s)",
                                len(commons) - k, e)
                    break
                except Exception as e:
                    log.warning("audio: %s", e)
                time.sleep(1.0)
            for f in as_completed(futs):
                try:
                    f.result()
                except Exception as e:
                    log.warning("audio: %s", e)
    # A failed human download (or, below, a failed encode) falls back to TTS.
    def to_tts(j: dict) -> None:
        j.update({"kind": "tts", "human": 0, "source": f"edge-tts:{tts['voice']}",
                  "license": "generated (Microsoft Edge neural TTS, personal use)", "attribution": None,
                  "path": tts_raw_path(recipe, j["text"], tts["voice"], tts["rate"]),
                  # A different signature than the planned human source, so the next run tries it again.
                  "sig": sha1_text(ENCODER_VERSION, str(lufs), cfg["bitrate"], "tts-fallback", j["text"])})

    for j in todo.values():
        if j["kind"] in ("commons", "tatoeba") and not j["path"].exists():
            to_tts(j)

    bitrates = [cfg["bitrate"], "24k", "16k"]
    ok = 0
    for attempt in range(2):
        tts_jobs = list({str(j["path"]): (j["text"], j["path"]) for j in todo.values()
                         if j["kind"] == "tts" and not j["path"].exists()}.values())
        if tts_jobs:
            log.info("audio: synthesising %d TTS clips with %s", len(tts_jobs), tts["voice"])
            (recipe.work / "tts_raw").mkdir(parents=True, exist_ok=True)
            for e in asyncio.run(_tts_batch(tts_jobs, tts["voice"], tts["rate"])):
                log.error("tts: %s", e)

        failed: list[str] = []
        with ThreadPoolExecutor(6) as ex:
            futs = {ex.submit(encode, j["path"], out_dir / n, lufs, bitrates, j["cap"], pad): n
                    for n, j in todo.items() if j["path"].exists()}
            for f in as_completed(futs):
                n = futs[f]
                j = todo[n]
                try:
                    info = f.result()
                    state.set(n, {"sig": j["sig"], "human": j["human"], "source": j["source"],
                                  "license": j["license"], "attribution": j["attribution"], "text": j["text"],
                                  "for": j["for"], "src": str(j["path"].relative_to(recipe.work.parent)), **info})
                    ok += 1
                    if ok % 500 == 0:
                        log.info("audio: encoded %d/%d", ok, len(futs))
                except Exception as e:
                    log.warning("audio: %s: %s", n, e)
                    failed.append(n)
        state.flush()
        todo = {n: todo[n] for n in failed if todo[n]["kind"] != "tts"}
        for j in todo.values():
            to_tts(j)
        if not todo:
            break
    fail = len(todo)

    # ---- media table for packaging
    rows = []
    for wid, name in word_map.items():
        st = state.get(name)
        if st and (out_dir / name).exists():
            rows.append({"word_id": wid, "kind": "word_audio", "file": name, **{k: st[k] for k in ("human", "source", "license", "attribution")}})
    sent_rows = []
    for key, name in sent_map.items():
        wid, sid = key.split("|")
        st = state.get(name)
        if st and (out_dir / name).exists():
            sent_rows.append({"word_id": wid, "sentence_id": sid, "file": name, "human": st["human"],
                              "source": st["source"], "license": st["license"], "attribution": st["attribution"]})
    write_json(recipe.work / "media_audio.json", {"words": rows, "sentences": sent_rows})
    human_w = sum(r["human"] for r in rows)
    human_s = len({r["file"] for r in sent_rows if r["human"]})
    log.info("audio: done (%d encoded, %d failed). word audio %d/%d (human %d), sentence clips %d (human %d)",
             ok, fail, len(rows), len(words), human_w, len({r['file'] for r in sent_rows}), human_s)
