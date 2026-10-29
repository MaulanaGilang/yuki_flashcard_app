# Yuki content pipeline

Turns free/open data into read-only language packs for the app (schema: `docs/ARCHITECTURE.md` §2.4).
First pack: **Mandarin HSK 3.0 levels 1–6** (5,456 words) → `packs/zh-hsk-1-6-v1/`.

```
fetch ─► normalize ─► enrich ─► media (audio + images) ─► evaluate ─► package
```

Every stage reads the previous one's output from `work/<pack>/` (git-ignored), is idempotent and caches
its work, so any stage can be stopped and re-run. Recipes live in `config/` (`zh.yaml` + its curated
data files); adding a language means adding a recipe.

## Setup (Windows, CPU only)

Run commands from `pipeline/` in PowerShell. Git Bash can't fork on this machine.

```powershell
python -m venv .venv                       # Python 3.12
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
```

ffmpeg comes from `imageio-ffmpeg` (static build with libopus); a system `ffmpeg` on PATH is used if present.
The rembg model (~180 MB) and the Whisper model download on first use.

For the image batch, create a **free Cloudflare account** and put two lines in `pipeline/.env` (git-ignored):

```
CF_ACCOUNT_ID=<Account ID from the Cloudflare dashboard>
CF_API_TOKEN=<API token with the "Workers AI" permission>
```

## Running

Use `-X utf8` so hanzi print correctly in the console:

```powershell
$py = ".\.venv\Scripts\python.exe"
& $py -X utf8 -m yuki_pipeline fetch        # pinned downloads -> work/raw (lock: work/raw/fetch.lock.json)
& $py -X utf8 -m yuki_pipeline normalize    # words.parquet: stable ids, levels, pinyin per character
& $py -X utf8 -m yuki_pipeline enrich       # glosses, measure words, sentences, stroke data
& $py -X utf8 -m yuki_pipeline media --only audio
& $py -X utf8 -m yuki_pipeline images       # Fluent Emoji + today's FLUX batch
& $py -X utf8 -m yuki_pipeline evaluate     # report.json + report.html (spot-check sheet)
& $py -X utf8 -m yuki_pipeline package      # packs/zh-hsk-1-6-v1/ (+ .kpack, .chars.txt)
```

`all` runs every stage in order. Useful flags:
- `--limit N` processes only the first N words, for quick trials.
- `evaluate --no-asr` skips the Whisper check.
- `package --force` packages without a passing report.
- `fetch --refresh` re-downloads the rolling exports (Tatoeba, CC-CEDICT).

Tests: `& $py -m pytest tests`. They also check that `search_key` normalisation matches the app's contract (`tools/build_dev_pack.normalize`).

### The daily image batch (resume every day)

FLUX.1 schnell on Workers AI's free tier allows about 170 images a day (10,000 neurons). 3,900 words need art, so the batch takes about 3–4 weeks. Once a day:

```powershell
& $py -X utf8 -m yuki_pipeline images                     # default budget: media.images.flux.daily_image_budget
& $py -X utf8 -m yuki_pipeline images --image-budget 50   # or a smaller run
& $py -X utf8 -m yuki_pipeline evaluate --no-asr
& $py -X utf8 -m yuki_pipeline package                    # refresh the pack with the new art
```

How resuming works:
- The batch stops by itself when Cloudflare reports the daily allocation used (error 4006 or HTTP 429), and picks up the remaining words the next day.
- Raw images are cached in `work/<pack>/flux_raw/` and progress in `work/<pack>/image_state.json`, so nothing is generated twice.
- Prompts the Workers AI safety filter rejects twice (false positives on harmless verbs) are listed in `work/<pack>/flux_rejected.json`. Those words become typographic cards.

To run it unattended, add a Windows Task Scheduler job that runs the first command daily.

To change one word's art, edit `config/zh_image_overrides.yaml` (`words:` → `{kind: typographic}` or `{subject: "…"}`).
To change a Fluent Emoji match, edit `config/zh_fluent_map.yaml`.

## What each stage does (zh)

| Stage | Details |
|---|---|
| fetch | HSK 3.0 list `ivankra/hsk30` (MIT) · `drkameleon/complete-hsk-vocabulary` (MIT; frequency, HSK 2.0 level, classifiers) · CC-CEDICT (CC BY-SA 4.0) · Tatoeba cmn/eng sentences + audio index (CC BY 2.0 FR) · Make Me a Hanzi (Arphic PL / LGPL) · audio-cmn (CC BY-SA) · Fluent Emoji (MIT). Git sources are pinned to commits. |
| normalize | One row per HSK entry, with id `zh:<hanzi>:<numbered pinyin>:<pos>` (e.g. `zh:苹果:ping2guo3:n`; a duplicate gets `:2`). Each row also has per-character `syllables` and `tones` (5 = neutral), and a `freq_rank` by corpus frequency. |
| enrich | **Glosses:** CC-CEDICT senses grouped by part of speech, each ≤ 80 chars. Hand fixes are in `config/zh_overrides.yaml`.<br>**Measure words:** from the CEDICT `CL:` entries.<br>**Sentences:** one per word, from Tatoeba. Traditional text is converted with OpenCC. Each sentence is level-filtered with jieba against the HSK list, ≤ 15 tokens, and sentences with human audio are preferred. Words without a usable sentence use `config/zh_extra_sentences.tsv` (authored, CC0, flagged `yuki-authored`).<br>**Strokes:** Make Me a Hanzi for every headword and measure-word character. |
| media / audio | **Word audio**, first available of:<br>1. audio-cmn HSK recordings<br>2. audio-cmn syllable recordings (single characters, polyphones first, so the reading is right)<br>3. Lingua Libre<br>4. edge-tts `zh-CN-XiaoxiaoNeural` (`human = 0`)<br>**Sentence audio:** Tatoeba human recordings, otherwise edge-tts.<br>**Processing:** silence trim (≤ 100 ms pad), one gain to −16 LUFS with a −1 dBFS limiter, Opus. The bitrate steps down from 32 kbps until each clip fits its cap (word 8 KB, sentence 20 KB). |
| media / images | 1. Fluent Emoji 3D via the curated map.<br>2. FLUX.1 schnell with the fixed style prompt (DESIGN-SYSTEM §5.4), then rembg background removal.<br>3. Typographic: function words, plus abstract glosses listed in `zh_image_overrides.yaml`.<br>Every image gets the same sticker treatment: crop, 6 dp white die-cut outline, soft shadow, 768×768 RGBA WebP ≤ 60 KB. |
| evaluate | PRD §3.4 bars, see `yuki_pipeline/evaluate.py`.<br>**Report:** `work/<pack>/report.html` is the spot-check sheet: 5 % of words per level with audio players and art, plus 20 rendered stroke sets.<br>**ASR check:** faster-whisper `small` on a seeded sample of 300 TTS clips. |
| package | `pack.db` (exact §2.4 schema), `audio/`, `img/`, `credits.json` (sentence and sentence-audio attribution), `manifest.json` (sha256 per file, sources and licences, counts).<br>Next to the folder: a `.kpack` zip, and `.chars.txt` for `tools/subset_fonts.py --extra-chars-file`. |

### `word.extra_json` (zh)

- **Always:**
  - `syllables` (toned, one per character)
  - `tones` (1–5)
  - `hsk_id`
- **When known:**
  - `measure_word` (e.g. `"个"`) and `measure_words` (`[{hanzi, pinyin}]`)
  - `hsk2_level`
  - `traditional`
  - `pos_all`
  - `variants` (e.g. 爸 for 爸爸)
  - `pattern` (`…极了`)
  - `example` (affixes: 第 → 第二)
- **No image yet:** `image` (`"typographic"` or `"pending"`) and `keyword` (e.g. `"BUT"`) for the art window.

`search_key` = headword, toneless pinyin joined and spaced, numbered pinyin, and glosses, normalised exactly as the §2.4 contract says.

## Licences in the pack

Each `asset` row carries `source`, `license` and `attribution`. Sentence sources are in `credits.json`.
Personal use only, as the PRD says. Going public needs the licensing pass in PRD §5.4. Tatoeba audio without a stated licence and the edge-tts voices are fine for personal use only.
