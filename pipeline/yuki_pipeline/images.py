"""Stage 4b — media/images.

Per word, in order:
  1. Fluent Emoji 3D (MIT) via the curated map config/zh_fluent_map.yaml
  2. FLUX.1 schnell on Cloudflare Workers AI (free daily quota), fixed style prompt from
     DESIGN-SYSTEM.md §5.4 (the API takes no seed; raw output is cached); resumable — every image is cached in
     work/<pack>/flux_raw/ and the queue state in work/<pack>/image_state.json
  3. typographic (abstract and grammar words; no image file, the app draws a keyword card)

Every image then gets: background removal (rembg, generated art only), crop, 6 dp white
die-cut outline + soft shadow, 768×768 RGBA WebP ≤ 60 KB.

`python -m yuki_pipeline images --suggest-fluent` writes candidate matches for curation.
"""

from __future__ import annotations

import base64
import io
import json
import re
import time
from datetime import date
from pathlib import Path

import polars as pl
import requests
import yaml
from PIL import Image, ImageChops, ImageFilter

from .config import RAW_DIR, Recipe, load_env
from .util import JsonCache, log, sha1_text, write_json

FLUENT = RAW_DIR / "fluentui-emoji" / "assets"
TYPO_POS = {"conj", "prep", "part", "adv", "pron", "num", "m", "intj", "prefix", "suffix", "onom", "phr"}
PROC_VERSION = "1"
COLOURS = {"white", "black", "red", "green", "blue", "yellow", "purple", "pink", "orange", "brown", "grey", "gray", "golden", "silver"}

# ---------------------------------------------------------------- word metadata


def first_gloss(w: dict) -> str:
    senses = json.loads(w["senses"])
    if not senses:
        return w["headword"]
    g = senses[0]["gloss"].split(";")[0]
    g = re.sub(r"\([^)]*\)", "", g).strip()
    return g


def keyword(w: dict) -> str:
    """Short uppercase keyword for typographic cards, e.g. 但是 -> BUT."""
    g = first_gloss(w)
    g = re.sub(r"^(to|a|an|the) ", "", g.strip(), flags=re.I)
    g = g.split(",")[0].strip()
    return g.upper()[:24]


def subject_for(w: dict) -> str:
    g = first_gloss(w)
    if w["pos"] == "v":
        return f"cute character performing the action: {re.sub(r'^to ', '', g)}"
    if g.lower() in COLOURS:
        # A plain coloured object; white needs a contrasting rim or background removal eats it.
        return f"glossy {g} paint splash blob" + (" with a soft grey outline" if g.lower() == "white" else "")
    if w["pos"] == "adj":
        return f"object that clearly looks {g}"
    return re.sub(r"^(a|an|the) ", "", g)


def is_typographic(w: dict, rules: dict) -> bool:
    if w["pos"] in TYPO_POS:
        return True
    g = first_gloss(w).lower()
    return any(re.search(p, g) for p in rules.get("typographic_gloss_patterns", []))


# ---------------------------------------------------------------- Fluent Emoji


def fluent_index() -> dict[str, dict]:
    idx = {}
    for d in FLUENT.iterdir():
        meta = d / "metadata.json"
        if not meta.exists():
            continue
        m = json.loads(meta.read_text(encoding="utf-8"))
        pngs = sorted((d / "3D").glob("*.png")) or sorted((d / "Default" / "3D").glob("*.png"))
        if pngs:
            idx[d.name] = {"cldr": m.get("cldr", d.name).lower(), "keywords": [k.lower() for k in m.get("keywords", [])],
                           "glyph": m.get("glyph"), "png": pngs[0]}
    return idx


def suggest_fluent(recipe: Recipe) -> Path:
    words = pl.read_parquet(recipe.work / "enriched.parquet").to_dicts()
    idx = fluent_index()
    by_cldr = {v["cldr"]: k for k, v in idx.items()}
    by_last: dict[str, list[str]] = {}
    by_kw: dict[str, list[str]] = {}
    for k, v in idx.items():
        by_last.setdefault(v["cldr"].split()[-1], []).append(k)
        for kw in v["keywords"]:
            by_kw.setdefault(kw, []).append(k)
    rows = []
    for w in words:
        if w["pos"] not in ("n", "v", "adj"):
            continue
        items = []
        for s in json.loads(w["senses"])[:1]:
            for it in s["gloss"].split(";")[:3]:
                it = re.sub(r"\([^)]*\)", "", it).strip().lower()
                it = re.sub(r"^(a|an|the|to) ", "", it)
                if it:
                    items.append(it)
        for it in items:
            hit = None
            if it in by_cldr:
                hit = (by_cldr[it], "cldr")
            elif it in by_last and len(by_last[it]) == 1:
                hit = (by_last[it][0], "cldr-tail")
            elif it in by_kw and len(by_kw[it]) <= 2:
                hit = (by_kw[it][0], "keyword")
            if hit:
                rows.append(f"{w['id']}\t{w['headword']}\t{it}\t{hit[0]}\t{hit[1]}")
                break
    out = recipe.work / "fluent_candidates.tsv"
    out.write_text("\n".join(rows), encoding="utf-8")
    log.info("images: %d Fluent Emoji candidates -> %s (curate into config/zh_fluent_map.yaml)", len(rows), out)
    return out


# ---------------------------------------------------------------- sticker processing


def _remove_bg(img: Image.Image, session_holder: dict) -> Image.Image:
    from rembg import new_session, remove

    if "s" not in session_holder:
        session_holder["s"] = new_session("isnet-general-use")
    return remove(img, session=session_holder["s"])


def sticker(img: Image.Image, size: int, outline_px: int, max_bytes: int, out: Path) -> dict:
    """Crop to content, fit, add white die-cut outline + soft shadow, save WebP ≤ max_bytes."""
    img = img.convert("RGBA")
    alpha = img.getchannel("A")
    bbox = alpha.point(lambda v: 255 if v > 16 else 0).getbbox()
    if bbox:
        img = img.crop(bbox)
    margin = outline_px * 2 + 24
    box = size - 2 * margin
    scale = box / max(img.size)
    img = img.resize((max(1, round(img.width * scale)), max(1, round(img.height * scale))), Image.LANCZOS)
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    ox, oy = (size - img.width) // 2, (size - img.height) // 2 - 6
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    layer.paste(img, (ox, oy))
    a = layer.getchannel("A")
    # Outline: blur the mask by the outline radius and threshold it -> rounded dilation.
    dil = a.filter(ImageFilter.GaussianBlur(outline_px / 2)).point(lambda v: 255 if v > 10 else 0)
    dil = dil.filter(ImageFilter.MaxFilter(5)).filter(ImageFilter.GaussianBlur(1))
    shadow = dil.filter(ImageFilter.GaussianBlur(14)).point(lambda v: int(v * 0.22))
    shadow_img = Image.new("RGBA", (size, size), (20, 24, 40, 0))
    shadow_img.putalpha(ImageChops.offset(shadow, 0, 10))
    white = Image.new("RGBA", (size, size), (255, 255, 255, 0))
    white.putalpha(dil)
    canvas = Image.alpha_composite(canvas, shadow_img)
    canvas = Image.alpha_composite(canvas, white)
    canvas = Image.alpha_composite(canvas, layer)
    out.parent.mkdir(parents=True, exist_ok=True)
    for q in (82, 75, 68, 60, 52, 45, 38):
        buf = io.BytesIO()
        canvas.save(buf, "WEBP", quality=q, method=6, alpha_quality=80)
        if buf.tell() <= max_bytes:
            break
    out.write_bytes(buf.getvalue())
    return {"bytes": out.stat().st_size, "quality": q, "size": size}


# ---------------------------------------------------------------- FLUX on Cloudflare


class QuotaExhausted(Exception):
    pass


class PromptRejected(Exception):
    """Workers AI's safety filter (code 8007) — it has false positives on harmless verbs."""


def flux_generate(env: dict, model: str, prompt: str, steps: int) -> bytes:
    """One FLUX.1 schnell image. The Workers AI schema has no seed parameter, so the
    fixed prompt is the only reproducibility control; the raw output is cached."""
    url = f"https://api.cloudflare.com/client/v4/accounts/{env['CF_ACCOUNT_ID']}/ai/run/{model}"
    r = requests.post(url, headers={"Authorization": f"Bearer {env['CF_API_TOKEN']}"},
                      json={"prompt": prompt, "steps": steps}, timeout=120)
    if r.status_code == 429:
        raise QuotaExhausted(r.text[:300])
    try:
        body = r.json()
    except ValueError:
        body = {}
    if r.status_code != 200 or not body.get("success", False):
        errors = body.get("errors") or []
        codes = {e.get("code") for e in errors if isinstance(e, dict)}
        msg = json.dumps(errors or r.text[:300])
        # 4006: daily free neuron allocation used up
        if 4006 in codes or "daily free allocation" in msg.lower():
            raise QuotaExhausted(msg)
        if 8007 in codes:
            raise PromptRejected(msg)
        raise RuntimeError(f"FLUX error {r.status_code}: {msg}")
    return base64.b64decode(body["result"]["image"])


# ---------------------------------------------------------------- stage


def read_rejected(recipe: Recipe) -> list[str]:
    p = recipe.work / "flux_rejected.json"
    return json.loads(p.read_text(encoding="utf-8")) if p.exists() else []



def run(recipe: Recipe, budget: int | None = None, limit: int | None = None, suggest: bool = False) -> None:
    if suggest:
        suggest_fluent(recipe)
        return
    cfg = recipe.section("media")["images"]
    size, max_bytes = cfg["size"], cfg["max_kb"] * 1024
    outline_px = cfg["outline_dp"] * cfg["px_per_dp"]
    flux = cfg["flux"]
    budget = flux["daily_image_budget"] if budget is None else budget
    words = pl.read_parquet(recipe.work / "enriched.parquet").to_dicts()
    if limit:
        words = words[:limit]
    fmap = yaml.safe_load(recipe.resolve(cfg["fluent_keyword_map"]).read_text(encoding="utf-8")) or {}
    rules = yaml.safe_load(recipe.resolve(cfg["overrides"]).read_text(encoding="utf-8")) or {}
    per_word = rules.get("words", {}) or {}
    idx = fluent_index()
    out_dir = recipe.work / "media" / "img"
    state = JsonCache(recipe.work / "image_state.json", every=10)
    session: dict = {}

    # A headword key applies to n/v/adj entries; if the headword also has a noun entry,
    # only to the noun (牛 "cow" must not illustrate 牛 "awesome"). Id keys always apply.
    nouns = {w["headword"] for w in words if w["pos"] == "n"}

    def fluent_key(w: dict) -> str | None:
        if w["id"] in fmap:
            return fmap[w["id"]]
        if w["pos"] not in ("n", "v", "adj") or (w["headword"] in nouns and w["pos"] != "n"):
            return None
        return fmap.get(w["headword"])

    rejected_ids = set(read_rejected(recipe))
    plan = []
    for w in words:
        ov = per_word.get(w["id"], {}) or {}
        key = fluent_key(w)
        if ov.get("kind") == "typographic":
            plan.append((w, "typographic", None))
        elif key and key in idx:
            plan.append((w, "fluent", key))
        elif w["id"] in rejected_ids and not ov.get("subject"):
            plan.append((w, "typographic", None))
        elif ov.get("subject") or (not is_typographic(w, rules) and ov.get("kind") != "typographic"):
            plan.append((w, "flux", ov.get("subject") or subject_for(w)))
        else:
            plan.append((w, "typographic", None))

    # Fluent Emoji: local, processed every run (cached by signature).
    n_fluent = 0
    for w, kind, ref in plan:
        if kind != "fluent":
            continue
        name = f"i_{sha1_text(w['id'])[:12]}.webp"
        sig = sha1_text(PROC_VERSION, "fluent", ref, str(size), str(outline_px))
        st = state.get(w["id"], {})
        if st.get("sig") != sig or not (out_dir / name).exists():
            info = sticker(Image.open(idx[ref]["png"]), size, outline_px, max_bytes, out_dir / name)
            state.set(w["id"], {"sig": sig, "status": "done", "kind": "fluent", "file": name, "ref": ref,
                                "source": "fluent-emoji-3d", "license": "MIT",
                                "attribution": f"Microsoft Fluent Emoji 3D — {ref}", **info})
        n_fluent += 1
    for w, kind, ref in plan:
        if kind == "typographic":
            state.set(w["id"], {"status": "typographic", "kind": "typographic", "keyword": keyword(w)})
    state.flush()

    # FLUX batch (resumable within the daily quota).
    pending = [(w, subj) for w, kind, subj in plan if kind == "flux"
               and not (state.get(w["id"], {}).get("status") == "done"
                        and state.get(w["id"], {}).get("subject") == subj)]
    env = load_env()
    made = 0
    if pending and budget > 0:
        if not env.get("CF_ACCOUNT_ID") or not env.get("CF_API_TOKEN"):
            log.warning("images: %d words wait for FLUX, but pipeline/.env has no CF_ACCOUNT_ID / CF_API_TOKEN. "
                        "Create a free Cloudflare account and add both (see README) — skipping the batch.",
                        len(pending))
        else:
            raw_dir = recipe.work / "flux_raw"
            rejected = set(read_rejected(recipe))
            log.info("images: FLUX batch — %d pending, budget %d this run", len(pending), budget)
            for w, subj in pending:
                if made >= budget:
                    break
                prompt = flux["style_prompt"].format(subject=subj)
                raw = raw_dir / f"{sha1_text(prompt)[:16]}.jpg"
                if not raw.exists():
                    try:
                        try:
                            data = flux_generate(env, flux["model"], prompt, flux["steps"])
                        except PromptRejected:
                            # Retry once with a plainer wording; if that is rejected too, typographic.
                            alt = f"friendly simple icon representing the idea of {first_gloss(w)}"
                            prompt = flux["style_prompt"].format(subject=alt)
                            raw = raw_dir / f"{sha1_text(prompt)[:16]}.jpg"
                            data = raw.read_bytes() if raw.exists() else flux_generate(env, flux["model"], prompt, flux["steps"])
                    except QuotaExhausted as e:
                        log.warning("images: daily quota reached after %d images (%s). Resume tomorrow.", made, e)
                        break
                    except PromptRejected:
                        log.warning("images: %s: prompt rejected twice by the safety filter -> typographic", w["id"])
                        rejected.add(w["id"])
                        continue
                    except Exception as e:
                        log.error("images: %s: %s", w["id"], e)
                        time.sleep(2)
                        continue
                    raw.parent.mkdir(parents=True, exist_ok=True)
                    raw.write_bytes(data)
                    made += 1
                name = f"i_{sha1_text(w['id'])[:12]}.webp"
                cut = _remove_bg(Image.open(raw), session)
                info = sticker(cut, size, outline_px, max_bytes, out_dir / name)
                state.set(w["id"], {"status": "done", "kind": "flux", "file": name, "subject": subj,
                                    "prompt": prompt, "generated_on": date.today().isoformat(),
                                    "source": "flux-1-schnell (Cloudflare Workers AI)",
                                    "license": "generated (FLUX.1 schnell, Apache-2.0 model)", "attribution": None,
                                    **info})
            state.flush()
            write_json(recipe.work / "flux_rejected.json", sorted(rejected))
    for w, subj in pending:
        if state.get(w["id"], {}).get("status") != "done":
            state.set(w["id"], {"status": "pending", "kind": "flux", "subject": subj, "keyword": keyword(w)})
    state.flush()

    rows = [{"word_id": w["id"], **{k: state.get(w["id"])[k] for k in ("status", "kind")},
             "file": state.get(w["id"]).get("file"), "keyword": state.get(w["id"]).get("keyword"),
             "source": state.get(w["id"]).get("source"), "license": state.get(w["id"]).get("license"),
             "attribution": state.get(w["id"]).get("attribution")} for w, _, _ in plan]
    write_json(recipe.work / "media_images.json", rows)
    from collections import Counter
    c = Counter((r["kind"], r["status"]) for r in rows)
    log.info("images: fluent %d, flux done %d (new today %d), flux pending %d, typographic %d",
             c[("fluent", "done")], c[("flux", "done")], made, c[("flux", "pending")], c[("typographic", "typographic")])
