# Architecture — Yuki

| | |
|---|---|
| **Status** | v0.4 — 7 languages, reading guide, widget, level progression |
| **Date** | 2026-10-29 |
| **Companion docs** | `docs/PRD.md` (v0.2), `docs/DESIGN-SYSTEM.md` (v0.1) |

The system has two halves that never talk at runtime:

1. **The content pipeline** (Python, runs on the laptop) turns open data, recordings and generated art into versioned **language packs**.
2. **The Android app** (Kotlin, Jetpack Compose) reads packs read-only, keeps your progress in its own database and never touches the network.

```
 ┌───────────────── laptop (build time) ─────────────────┐        ┌──────────────── phone (runtime, offline) ────────────────┐
 │                                                       │        │                                                          │
 │  word lists ┐                                         │        │   ┌───────────────┐   read-only   ┌───────────────────┐  │
 │  dictionaries├─► pipeline/ ─► pack: pack.db           │  APK   │   │ Content pack  │◄──────────────│ Repositories      │  │
 │  sentences  │   (Python)      + audio/*.opus         ├───────►│   │ pack.db+media │               │ (core:data)       │  │
 │  recordings │                 + img/*.webp           │  or    │   └───────────────┘               │                   │  │
 │  TTS / art  ┘                 + manifest.json        │  .kpack│   ┌───────────────┐   read/write  │  ▲             │  │  │
 │                                                       │  file  │   │ progress.db   │◄─────────────►│  │ use cases    │  │  │
 └───────────────────────────────────────────────────────┘        │   │ (Room)        │               └──┼─────────────┼──┘  │
                                                                  │   └──────┬────────┘                  │             ▼     │
                                                                  │          │ export/import    ┌────────┴───┐  ┌──────────┐ │
                                                                  │          ▼                  │ FSRS-6     │  │ Compose  │ │
                                                                  │   .yuki-backup  +  Auto    │ scheduler  │  │ UI       │ │
                                                                  │   (user's file)     Backup  └────────────┘  └──────────┘ │
                                                                  └──────────────────────────────────────────────────────────┘
```

**Guiding rules**
1. **Content and progress are separate files.** Updating or re-importing a pack can never damage progress. Backups contain progress only.
2. **Progress is append-first.** Every rating writes an immutable review-log row, so card state can always be rebuilt from history.
3. **No network, enforced.** The app has no `INTERNET` permission, and a CI check fails the build if any library adds one.
4. **Pure Kotlin where it matters.** The scheduler and session engine have no Android dependencies, so they are fast to unit-test.
5. **Rp 0.** Every tool, library and service in this document is free.

---

## 1. Repository layout

```
Flashcard-App/
├── docs/                    PRD, design system, architecture, ADRs
├── pipeline/                Python content pipeline (§3)
│   ├── yuki_pipeline/      stages: fetch, normalize, enrich, media, evaluate, package
│   ├── config/              per-language recipes (sources, levels, voices, style prompt)
│   ├── tests/
│   └── work/                caches and intermediate files (git-ignored)
├── packs/                   built packs (git-ignored; published as GitHub Release assets)
└── android/                 Gradle project (§2)
    ├── app/
    ├── core/   model · scheduler · data · media · designsystem · testing
    ├── feature/ today · study · library · stats · settings · onboarding · hanzi
    ├── build-logic/         Gradle convention plugins
    └── gradle/libs.versions.toml
```

**Version control.** This folder is not a git repository yet, so `git init` is the first scaffold step. Generated media is never committed. Packs are rebuilt reproducibly from the pipeline cache and stored as **GitHub Release assets**, which are free and allow up to 2 GB per file.

---

## 2. Android app

### 2.1 Stack

All versions are pinned in `libs.versions.toml` at scaffold time, using the latest stable release then.

| Concern | Choice | Notes |
|---|---|---|
| Language / UI | Kotlin, Jetpack Compose, Material 3 | Brand theming per `DESIGN-SYSTEM.md` |
| Min / target SDK | minSdk **26** (Android 8.0), target = latest stable | API 26 covers nearly all active devices and simplifies notifications and fonts |
| Navigation | **Navigation 3** (stable 1.0 since Nov 2025) | Type-safe back stack you own as state; fits Compose |
| DI | **Hilt** (KSP) | Standard, works with Nav3 and ViewModel |
| Async | Coroutines + Flow | `StateFlow` UI state |
| Progress DB | **Room** on the **bundled SQLite driver** (`androidx.sqlite:sqlite-bundled`) | Same SQLite version on every device, plus `VACUUM INTO` for backups |
| Pack DB | Read-only `androidx.sqlite` connection (same bundled driver), small hand-written DAO | Packs are produced by the pipeline, not by Room (§2.4) |
| Scheduler | **FSRS-6, ported in-repo** (`core:scheduler`) | Ported from `py-fsrs` (MIT). Third-party Kotlin ports were rejected (§9, ADR-3). |
| Audio | **SoundPool** for word and sentence clips; Media3 ExoPlayer as fallback | Clips are pre-decoded, so playback is near-instant (≤ 150 ms target) |
| Images | **Coil 3** | Memory/disk cache, prefetch of the next 2 cards |
| Stroke order | Compose `Canvas` + `PathParser` + `PathMeasure` | Make Me a Hanzi paths and medians (§2.7) |
| Practice pad (P1) | **Jetpack Ink** | Optional writing pad only |
| Background work | WorkManager; AlarmManager (inexact) for reminders | No exact-alarm permission needed |
| Widget (P1) | Jetpack **Glance** | Word-of-the-day |
| Settings | Stored **in progress.db** (not DataStore) | One file holds everything to back up |
| Testing | JUnit5, Turbine, Robolectric, Compose UI test, **Roborazzi** screenshots, Room `MigrationTestHelper`, Macrobenchmark + Baseline Profiles | All free |

### 2.2 Modules

Modules stay coarse enough for one developer, but split where testability or build speed pays.

```
app ─────────────┬──► feature:today ─┐
                 ├──► feature:study ─┤
                 ├──► feature:library┤
                 ├──► feature:stats ─┼──► core:data ──► core:scheduler ──► core:model
                 ├──► feature:settings┤        │
                 ├──► feature:onboarding┤      └──► core:media
                 └──► feature:hanzi ─┘
     every feature ──► core:designsystem ──► core:model
```

| Module | Type | Responsibility |
|---|---|---|
| `core:model` | Kotlin/JVM | Pure data types: `Word`, `Sense`, `Sentence`, `Card`, `CardState`, `Rating`, `ReviewLog`, `DeckConfig`, `Line`, `Level`, `FoilTier` |
| `core:scheduler` | Kotlin/JVM | FSRS-6 algorithm, `SessionEngine` (queue building, interleaving, learning steps, day boundary), foil-tier rules. **No Android imports.** |
| `core:data` | Android library | Room `ProgressDatabase` and DAOs, `PackReader`, `PackRegistry`, repositories, use cases, backup/restore, pack import |
| `core:media` | Android library | `AudioPlayer` (SoundPool + prefetch), image loader setup, stroke-path parsing and cache |
| `core:designsystem` | Android library | `YukiTheme`, tokens, fonts, components (line bullet, route strip, collectible card, rating bar, stroke panel, arrival board) |
| `core:testing` | Android library | Fakes, a test pack fixture (≈ 50 words in 2 languages), clock control |
| `feature:*` | Android library | One screen family each: Compose UI + ViewModel. Features depend on `core:*` only, never on each other. |
| `app` | Application | `MainActivity`, Nav3 host, Hilt graph, manifest, Auto Backup rules, bundled MVP pack in assets |

### 2.3 Layering inside a feature (UDF)

```
Composable ──events──► ViewModel ──calls──► UseCase / Repository ──► DB / pack / scheduler
    ▲                      │
    └──── StateFlow<UiState> (immutable data class; one per screen)
```

- ViewModels hold no Android `Context`.
- One-off effects (snackbar "Rated Good · Undo", navigation) go through a `Channel` consumed once.
- Heavy work runs on `Dispatchers.IO`/`Default`, injected through a `DispatcherProvider` so tests can control it.

### 2.4 Data: two databases

#### Content pack (read-only, produced by the pipeline)

Each pack is a folder with `pack.db`, `audio/`, `img/` and `manifest.json`.

```sql
pack_meta(key TEXT PRIMARY KEY, value TEXT)            -- id, lang, levels, version, schema_version, built_at
word(
  id TEXT PRIMARY KEY,          -- stable across pack versions, e.g. "zh:苹果:ping2guo3:n"
  lang TEXT, headword TEXT, reading TEXT,               -- reading: "píngguǒ" / IPA / stressed form
  search_key TEXT,              -- normalised: "pingguo ping2guo3 苹果 apple" (lowercase, no tones)
  level INTEGER, level_label TEXT,                      -- 1 / "HSK 1", 3 / "B1"
  freq_rank INTEGER, pos TEXT, extra_json TEXT          -- gender, aspect pair, measure word, hsk2_level …; zh: "syllables": ["píng","guǒ"], "tones": [2,3] (one per character)
)
sense(word_id TEXT, ord INTEGER, pos TEXT, gloss TEXT)
sentence(id TEXT PRIMARY KEY, word_id TEXT, ord INTEGER, text TEXT, reading TEXT, translation TEXT,
         audio TEXT, audio_human INTEGER)
asset(word_id TEXT, kind TEXT,  -- 'word_audio' | 'image'
      path TEXT, human INTEGER, source TEXT, license TEXT, attribution TEXT)
hanzi(ch TEXT PRIMARY KEY, stroke_count INTEGER, radical TEXT, strokes_json TEXT, medians_json TEXT)
CREATE INDEX word_level ON word(level, freq_rank);
CREATE INDEX word_search ON word(search_key);
-- Child-table lookups by word (added 2026-10-29). On an 8,000-word pack, card open drops
-- from 14 ms to 1.7 ms (core/data PackPerformanceTest).
CREATE INDEX sense_word ON sense(word_id, ord);
CREATE INDEX sentence_word ON sentence(word_id, ord);
CREATE INDEX asset_word ON asset(word_id);
```

- **Stable word IDs** are the key to upgrades. A new pack version keeps IDs, so your progress carries over. A word removed in a newer pack keeps its card, hidden; cards are never deleted.
- **`search_key` normalisation contract** (pipeline and app must match; implemented in `PackReader.normalizeQuery` and `build_dev_pack.normalize`): lowercase; Unicode NFD; drop combining marks that follow a Latin letter (pinyin tones, Spanish accents, ü→u, ñ→n); drop the Russian stress mark U+0301 everywhere but keep й and ё; drop Arabic vowel marks (U+064B–U+0652, U+0670); NFC. For zh the key holds the headword, toneless pinyin (joined and spaced), numbered pinyin (`ping2guo3`) and the English glosses.
- **Search:** about 11,000 rows at most per language. A `LIKE` prefix/substring match over `search_key` takes ≤ 20 ms on device, inside the 100 ms budget. FTS is unnecessary; revisit only if measurements say so.
- **Opening a pack:**
  - The MVP pack ships in `assets/packs/zh-hsk-1-6/`. On first launch its `pack.db` (a few MB) is copied to `filesDir/packs/…`. Audio and images are read straight from assets through `AssetFileDescriptor`, with `noCompress` set for `opus` and `webp`.
  - Imported packs (P1) are unzipped into `filesDir/packs/<id>/`.
  - A `PackSource` interface hides the difference between the two.

#### Progress database (Room, read/write)

```kotlin
@Entity card(
  id: String /* wordId#type */, wordId: String, packId: String, lang: String,
  type: CardType /* RECOGNITION | LISTENING | PRODUCTION | WRITING */,
  state: FsrsState /* NEW | LEARNING | REVIEW | RELEARNING */, step: Int?,
  stability: Double?, difficulty: Double?, due: Instant?, lastReview: Instant?,
  reps: Int, lapses: Int, suspended: Boolean, markedKnown: Boolean, orphaned: Boolean,
  newRank: Int  // order for introducing new cards: level, then freq_rank
)  // index (lang, suspended, due), (lang, state, newRank)

@Entity review_log(
  id: Long (auto), cardId: String, rating: Rating, reviewedAt: Instant, durationMs: Long,
  before: CardSnapshot /* full prior state, JSON */, after: CardSnapshot,
  sessionId: String, kind: ReviewKind /* SCHEDULED | EXTRA_ROUND | MANUAL_RESET */
)  // index (cardId, reviewedAt), (reviewedAt)

@Entity deck_config(lang: String PK, newPerDay: Int = 10, maxReviews: Int = 200,
  desiredRetention: Double = 0.90, learningSteps: String = "1m,10m", relearningSteps: String = "10m",
  cardTypes: Set<CardType>, showReadingOnFront: Boolean = false, autoplayFront: Boolean = false,
  autoplayBack: Boolean = false, fsrsParams: String? /* null = defaults */)

@Entity app_settings(key: String PK, value: String)  // theme, tone numbers, reminder time, day-start hour (default 4)
@Entity installed_pack(id: String PK, version: Int, lang: String, path: String, installedAt: Instant)
```

- **Card rows are created on pack install:** one per word per enabled card type, about 5,500 rows for HSK 1–6, created in one transaction.
- **Every rating is one transaction:** insert the `review_log` row, then update `card`. An interrupted write leaves nothing half-done.
- **Undo** reads the last `review_log` row of the session, restores `card` from its `before` snapshot and deletes the row, all in one transaction.
- **Schema changes** use Room auto-migrations where possible, plus explicit `Migration`s. Every version is covered by `MigrationTestHelper` tests (release gate).

### 2.5 Scheduling & sessions (`core:scheduler`)

**FSRS-6 port**
- Ported line by line from `py-fsrs` (MIT): default parameters, retrievability, stability and difficulty updates, short-term learning steps, interval fuzz, max interval.
- **Test vectors:** the pipeline uses `py-fsrs` to generate 1,000+ random review histories as JSON. A JUnit test replays them through the Kotlin port and requires identical results (within 1e-6 for floats, same due day). This is the gate for any scheduler change.
- **Personal parameters (later, free):** export the review log as CSV, run the official FSRS optimizer on the laptop, and paste the 21 parameters into `deck_config.fsrsParams`. No on-device training.

**Day boundary.** The study day starts at 04:00 local time (configurable), like Anki, so a late-night session counts toward the previous day. Changing timezone uses the device's current zone; due times are stored as UTC instants.

**SessionEngine** is a pure state machine:
```
build(lang, now) →
  reviews  = due cards (state REVIEW/RELEARNING, due ≤ end of study day), sorted by due, capped at maxReviews
  learning = LEARNING cards due within the session
  new      = next (newPerDay − introducedToday) cards by newRank, excluding suspended / markedKnown
  queue    = interleave(reviews, new, ratio 4:1); learning cards re-inserted when their step is due
next() → Card | Done
answer(rating, now) → (CardUpdate, ReviewLog)   // the caller persists both in one transaction
undo() → restores the previous queue position
```
- Sibling cards (same word, different type) are buried until the next study day after one of them is shown.
- The **"review missed words"** round after the summary uses `ReviewKind.EXTRA_ROUND`. It doesn't touch FSRS state.

**Foil tier** = f(state, stability): New / Learning / Review (< 21 d) / Mastered (≥ 21 d) / Gold (≥ 180 d), as in `DESIGN-SYSTEM.md` §2.5.

### 2.6 Audio (`core:media`)
- One `SoundPool` (max 4 streams, audio attributes `USAGE_MEDIA` / `CONTENT_TYPE_SPEECH`).
- When a card is shown, the engine **preloads** its word clip, its sentence clip and the next card's clips, and unloads clips more than 3 cards back.
- Audio focus is requested transiently and ducks other audio, so music pauses briefly and resumes.
- If a clip fails to decode (rare codec issues), the player falls back to Media3 ExoPlayer for that clip and logs it locally.

### 2.7 Stroke order (`feature:hanzi`, rendered by `core:designsystem`)
- Data comes from the pack's `hanzi` table: Make Me a Hanzi stroke outlines (SVG paths in a 1024×1024 box, y-flipped) and **medians** (centre lines).
- **Animation** uses the same technique as Hanzi Writer:
  - Each stroke outline is a **clip path**.
  - A thick line is drawn along the median, with its dash offset animated by `PathMeasure`.
  - The ink therefore "flows" along the stroke direction.
- Paths are parsed once and cached in an LRU keyed by character.
- **Numbered strip:** cumulative frames rendered as small `Canvas` thumbnails, one per stroke.
- **Practice pad (P1):**
  - Jetpack Ink captures touch strokes.
  - Each stroke is matched against the expected median: start and end point distance, direction and length ratio, using thresholds ported from Hanzi Writer's matcher (MIT).
  - Results go to a `PracticeLog`. They feed FSRS only if the user has turned on the writing card type.

### 2.8 Navigation (Nav3)

The back stack is a `SnapshotStateList<Route>` owned by the app:

```kotlin
sealed interface Route {
  data object Today : Route; data object Library : Route; data object Stats : Route; data object Settings : Route
  data class Onboarding(val step: Int) : Route
  data class Study(val lang: String) : Route
  data class Summary(val sessionId: String) : Route
  data class Level(val lang: String, val level: Int) : Route
  data class WordDetail(val wordId: String) : Route
  data class Character(val ch: Char) : Route
  data class Practice(val ch: Char) : Route   // P1
}
```

- Bottom navigation holds four top-level routes, each with its own saved stack.
- Predictive back is supported everywhere.
- Study → Summary replaces the Study entry, so pressing back from Summary returns to Today.

### 2.9 Backup & restore (`core:data/backup`)

**Export:**
1. Run `VACUUM INTO 'cacheDir/snapshot.db'` (a consistent copy even while the app runs).
2. Write `manifest.json` with `{format: 1, schemaVersion, appVersion, createdAt, cards, reviews, lastReviewAt, sha256}`.
3. Zip both as `yuki-YYYYMMDD-HHmm.yuki-backup`.
4. Save through the Storage Access Framework (`CreateDocument`) to any location: Downloads, Google Drive and so on.

**Import:**
1. Pick the file with `OpenDocument`.
2. Unzip to cache and verify the sha256.
3. Open the snapshot read-only and run `PRAGMA integrity_check`.
4. Check that `schemaVersion ≤ current`; older versions are migrated on open.
5. Show a preview (cards, reviews, last review date) and wait for confirmation.
6. Close Room, atomically replace `progress.db` (write to a temp file, then rename), reopen and rebuild in-memory caches.

**Android Auto Backup:**
- `data_extraction_rules.xml` / `full_backup_content.xml` include **only** `databases/progress.db*` and exclude `files/packs/` (re-installable).
- The database stays far below the 25 MB Auto Backup limit (≈ 2–5 MB after years of reviews).

**Scheduled weekly export (P1):** a WorkManager job writes to a folder the user granted earlier with persistable URI permission.

**CI gate:** an instrumented test seeds the database, exports, wipes, imports, and asserts that card and review-log tables are identical.

### 2.10 Reminders & widget (P1)
- **Reminder:** `AlarmManager.setAndAllowWhileIdle` at the chosen time (inexact, within minutes). The receiver checks due counts and today's goal, and posts a notification only if needed. A `BOOT_COMPLETED` receiver reschedules the alarm. `POST_NOTIFICATIONS` is requested only when reminders are switched on.
- **Widget:** a Glance widget refreshed daily by WorkManager. It picks a word from the active deck (not yet mastered, already introduced) and opens `WordDetail` on tap.

### 2.11 Manifest & permissions

```xml
<uses-permission android:name="android.permission.INTERNET" tools:node="remove" />   <!-- strip if any library adds it -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />              <!-- P1, requested at runtime -->
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />          <!-- P1, reminders -->
<application android:allowBackup="true" android:dataExtractionRules="@xml/data_extraction_rules" … />
```

CI runs a manifest-merger check that fails the build if `INTERNET` appears in the merged manifest.

### 2.12 Performance plan

| Budget (PRD §4.4) | How it's met | How it's measured |
|---|---|---|
| Cold start ≤ 1.5 s | Baseline Profile; lazy Hilt init; Today reads only small count queries; no pack media touched at start | Macrobenchmark `StartupBenchmark` on my phone |
| Flip/transition 60 fps, 0 jank | Tilt and foil in `graphicsLayer`/`drawWithCache` with no per-frame recomposition; stable UI state classes; R8 full mode | Macrobenchmark `FrameTimingMetric` over a scripted 30-card session |
| Audio ≤ 150 ms | SoundPool preloading | Instrumented timing log in debug builds |
| Search ≤ 100 ms | Indexed `search_key`, query debounced 150 ms | Unit benchmark on the full HSK pack |
| Rating persisted ≤ 50 ms | Single small transaction off the main thread; WAL mode | Benchmark test |

### 2.13 Reading guide, level progression, widget (PRD S17, S18, S14)
- **Reading guide** (`core:model/script`, pure Kotlin, unit-tested):
  - `ScriptGuide.of(lang)` returns `CyrillicGuide`, `ArabicGuide` or `LatinRules.German/French/Spanish`.
  - Each guide provides `transliterate`, `breakdown` (letter tiles) and `hints`.
  - Arabic transliteration works from fully vowelled text: the article and sun letters, long vowels, shadda, tanwīn, ة pausal/-at-, and initial hamza.
  - Nothing extra is stored in packs.
- **Progression** (`core:scheduler/Progression.kt`):
  - `Progression.state(LevelStats)` gives the station states.
  - `PaceGuard.adjust(...)` gives today's new-card allowance from 7-day recall on mature cards (`review_log` rows with `beforeState = REVIEW`).
  - `StudyRepository.startSession` and Today both use it.
- **Widget** (`app/widget/WordWidget.kt`): Jetpack Glance, `SizeMode.Exact`, 2×1 target cells.
  - Word of the day comes from `StudyRepository.wordOfTheDay()`: a learning, not-yet-mastered card picked by study-day index, falling back to the next new card.
  - Refreshed by `updatePeriodMillis` (6 h) and from `MainActivity.onStop`.
  - Tapping it starts `MainActivity` with the word id; the activity is `singleTop` and pushes `WordKey`.
  - Settings can pin it via `requestPinAppWidget`.
- **Packs are awaited, not assumed:** `PackRegistry.awaitReady()` runs before any content read, because the widget can cold-start the app straight into a word page.

---

## 3. Content pipeline (`pipeline/`)

### 3.1 Stack (all free)

| Concern | Tool |
|---|---|
| Runtime | Python 3.12 (already installed), `venv` + `pip`, `requirements.lock` |
| Data handling | `polars`, `pydantic` models for every record |
| Tokenising / lemmas | `jieba` (zh), `simplemma` (es/ru/en), `pymorphy3` (ru stress and lemma checks) |
| Pinyin | `pypinyin` (tone marks ↔ numbers), with CC-CEDICT readings taking priority |
| Audio processing | `ffmpeg` (loudness normalisation to −16 LUFS, silence trim, Opus 32 kbps) |
| TTS fallback | `edge-tts` (Microsoft neural voices), behind a `TtsProvider` interface so the winner of the listening test can be swapped in |
| TTS verification | `faster-whisper` (CPU, `small` model) for the ASR check (CER ≤ 5 %) |
| Image generation | Cloudflare Workers AI REST (FLUX.1 schnell), free account; resumable daily batches |
| Background removal | `rembg` (CPU, ONNX) |
| Sticker outline, WebP | `Pillow` (alpha dilation for the 6 dp outline, WebP q≈80) |
| OCR (ТРКИ-2 PDF) | `tesseract` + `rus` language data |
| Font subsetting | `fonttools` `pyftsubset` (WenKai subset from pack characters) |
| FSRS test vectors | `py-fsrs` (MIT) |

The laptop has 8 GB RAM and no GPU, so every tool above runs on CPU. Image generation is the only remote step, and it uses a free tier.

### 3.2 Stages

Each stage reads the previous stage's output from `work/<pack>/` and writes its own. Every stage is **idempotent and cached by content hash**, so it can stop at the image quota and resume tomorrow.

```
fetch ──► normalize ──► enrich ──► media ──► evaluate ──► package
```

| Stage | Does | Output |
|---|---|---|
| **fetch** | Download sources at pinned versions/URLs: HSK lists, CC-CEDICT, kaikki.org Wiktionary extracts, Tatoeba exports (sentences, links, audio index), audio-cmn, Shtooka, Lingua Libre/Commons indexes, Make Me a Hanzi, ТРКИ lists (manual files for docx/PDF) | `work/raw/` |
| **normalize** | One `Word` record per entry: stable ID, headword, reading, level, frequency rank, POS | `words.parquet` |
| **enrich** | Join glosses; pick example sentences (level-filtered, **human audio preferred**, ≤ 15 tokens); add grammar fields (measure word, gender, aspect pair, stress); attach stroke data for every character | `enriched.parquet` |
| **media** | **Audio:** match human recordings in priority order; otherwise TTS; normalise and encode. **Images:** Fluent Emoji match (curated keyword map) → generated art (style prompt from `DESIGN-SYSTEM.md` §5.4) → free photo (manual pick list) → typographic flag. Then background removal, sticker outline, WebP. | `media/` + `media.parquet` (source, licence, human flag per file) |
| **evaluate** | Automated checks from PRD §3.4 (gloss length, sentence level, ASR CER, image size and coverage, stroke coverage). Writes a **sample sheet** (HTML) for manual 2–5 % spot checks. Fails the run if any bar fails. | `report.html` |
| **package** | Write `pack.db` (schema §2.4), copy media, write `manifest.json` (per-file sha256, sources, licences), subset the WenKai font for the pack's characters | `packs/<id>-v<n>/` and `.kpack` (zip) |

**Language recipes** (`config/zh.yaml`, `es.yaml`, …) hold the source list, level mapping, TTS voice, style prompt and quotas. Adding a language means adding a recipe, not new code.

### 3.3 Handoff to the app
- A Gradle task `copyBundledPack` copies `packs/zh-hsk-1-6-v<n>/` into `android/app/src/main/assets/packs/` before `assembleRelease`.
- Imported packs (P1) are `.kpack` files: a zip with the same layout, verified against the manifest checksums when imported.

---

## 4. Build, test & CI

| Check | Where | Gate |
|---|---|---|
| Unit tests (scheduler vectors, session engine, foil tiers, search normalisation) | `core:scheduler`, `core:data` | Every push |
| Room migration tests | `core:data` | Every push |
| Backup round-trip (instrumented) | `core:data` | Before release |
| Compose UI tests: flip, rating disabled until flip, undo | `feature:study` | Every push |
| Screenshot tests (Roborazzi): card front/back × 4 lines × light/dark × font scale 1.0/2.0 | `core:designsystem` | Every push (diff review) |
| Lint: no hex colours outside `Tokens.kt`; no `INTERNET` in merged manifest | `build-logic` | Every push |
| Glyph-coverage test (pinyin vowels, ́ stress mark, Spanish marks) | `core:designsystem` | Every push |
| Macrobenchmark: startup, session frame timing | `benchmark` module | Before release |
| Pipeline evaluate stage | `pipeline/` | Before packaging |

**CI** runs on GitHub Actions. A private repository gets free monthly minutes, which is enough for unit, UI and screenshot tests on an emulator. Benchmarks run locally on the real phone.

**Release:** a signed release APK with a locally generated keystore that is never committed. It's installed by sideloading. No Play Console fee.

---

## 5. Security & privacy
- No network permission, no analytics, no ads, no crash-reporting SDK. Crashes are logged to a local file the user can export from Settings.
- The backup file is a plain zip under the user's control. It contains study history only.
- The pack import verifies checksums and only unzips into its own folder, rejecting paths with `..` (zip-slip protection).
- The keystore and any pipeline API tokens (Cloudflare) live outside the repository, in a `.env` file that is git-ignored.

---

## 6. Requirements traceability

| PRD story | Where it lives |
|---|---|
| S1 daily session, S2 rating + undo | `core:scheduler` SessionEngine + FSRS; `feature:study`; `review_log.before` snapshot |
| S3 real audio | Pipeline media stage (human first); `core:media` SoundPool preload |
| S4 stroke order for paper; S5 optional practice | `feature:hanzi` + stroke panel; Jetpack Ink pad behind a button |
| S6 card types | `card.type`; `deck_config.cardTypes`; sibling burying |
| S7 summary + extra round | `feature:study` Summary; `ReviewKind.EXTRA_ROUND` |
| S8–S9 library, search, per-word control | `feature:library`; `search_key` index; card flags |
| S10–S11 onboarding, multiple languages | `feature:onboarding`; per-language `deck_config` |
| S12 stats | `feature:stats`, computed from `review_log` |
| S13–S14 reminders, widget | AlarmManager + Glance (P1) |
| S15 backup/restore | `core:data/backup`; Auto Backup rules; round-trip test |
| S16 fully offline | No `INTERNET`; packs bundled or imported from file |

---

## 7. Build order (MVP)

1. **Scaffold:** `git init`, Gradle project, modules, convention plugins, theme tokens, CI skeleton.
2. **Scheduler first:** port FSRS-6, add the test vectors and SessionEngine, with unit tests passing.
3. **Pipeline M0** (runs in parallel from day 1, because the image batch needs about 3–4 weeks): Mandarin HSK 1–6 words, glosses, sentences, strokes, then audio, then images.
4. **Data layer:** progress DB, `PackReader` on the test fixture pack, repositories, backup/restore with round-trip test.
5. **Study loop UI:** collectible card, flip, rating bar, undo, summary.
6. **Hanzi:** stroke panel and strip, character detail.
7. **Library, Today, Stats, Settings, Onboarding.**
8. **Polish:** motion, foil tilt, dark theme, accessibility pass, benchmarks, baseline profile.
9. **Swap in the real pack**, run 2 weeks of daily use, then fix and tag v1.0.

---

## 8. Risks specific to the architecture

| Risk | Mitigation |
|---|---|
| FSRS port subtly wrong | Byte-for-byte comparison against `py-fsrs` vectors; any scheduler change must pass them |
| SoundPool can't decode some Opus files on a given device | Automatic ExoPlayer fallback per clip; the pipeline can also emit AAC if needed (flag in recipe) |
| Restoring a backup from a newer app version | `schemaVersion > current` is rejected with a clear message ("Update the app to restore this backup") |
| Pack update changes a word's ID | Pipeline check: compare IDs with the previous pack version; renamed IDs must be listed in an `id_aliases` table the app applies on install |
| Bundled SQLite driver adds ~1–2 MB to the APK | Accepted (consistency, `VACUUM INTO`) |
| Unofficial TTS endpoint changes | Output is cached and bundled; `TtsProvider` interface allows a swap |

---

## 9. Architecture decision records (summary)

| # | Decision | Alternatives considered | Why |
|---|---|---|---|
| ADR-1 | Native Kotlin + Compose | Flutter, React Native | Best animation performance for the card and foil; full offline control; matches installed Android skills |
| ADR-2 | Separate read-only pack DB + Room progress DB | One combined DB; SQLite `ATTACH` | Content updates can't corrupt progress; backups stay tiny. Joins are done in repositories (data is small). |
| ADR-3 | FSRS-6 ported in-repo from `py-fsrs` | FsrsKt (GPL-3, 1★, inactive), `android-fsrs` (older FSRS-4) | Current algorithm, permissive source, verifiable with official vectors |
| ADR-4 | Settings in progress.db, not DataStore | DataStore | One file is backed up and restored as a single unit |
| ADR-5 | Search via indexed normalised column | FTS4/FTS5 | ≤ 11k rows per language is fast enough. FTS5 availability in the bundled driver is unconfirmed. |
| ADR-6 | SoundPool for clips | ExoPlayer only | Pre-decoded clips give the lowest tap-to-sound latency |
| ADR-7 | Study fully offline; `INTERNET` only for the optional online backup (amended 30 Oct 2026, was "no `INTERNET` permission") | Pack downloads; live sync | Studying never needs a network. After you sign in (Settings › Online backup, email + password), the existing `.yuki-backup` file uploads to Supabase Storage (free tier, project `yuki`, private bucket `backups/<user id>/latest` + `previous`, storage policies limit each user to their own folder) when you leave the app and are online; WorkManager waits for a network. Restore downloads it into the usual confirm-and-replace flow. One phone at a time: newest upload wins. The session lives in SharedPreferences, never in progress.db. Packs still arrive by file import. |
| ADR-8 | Packs as GitHub Release assets, not in git | Git LFS | Free, no LFS quota, reproducible from pipeline |
| ADR-9 | Manual DI (`AppGraph`) instead of Hilt | Hilt | The graph is ~8 objects; skipping annotation processing keeps builds fast on an 8 GB laptop. Revisit if the graph grows. |
| ADR-10 | First scaffold: `core:media` lives in `core:data/media`; no `build-logic`; features `today`, `study`, `library` only | Full module list | Fewer modules while the app is small. Split out `core:media`, add convention plugins and `stats`/`settings`/`onboarding`/`hanzi` modules when they arrive. |
| ADR-11 | Transliteration and letter breakdown computed in the app, not stored in packs | Pipeline-generated readings per word | Deterministic rules are small and testable; works for every word, sentence and future pack; Arabic packs only need full vowelling |
| ADR-12 | Pace guard lowers new cards instead of blocking levels | Hard gates or placement exams | Keeps daily flow simple; recall data decides the speed; an optional checkpoint quiz (P1) covers skipping ahead |

---

## Sources
- Navigation 3 stable: https://android-developers.googleblog.com/2025/11/jetpack-navigation-3-is-stable.html
- Bundled SQLite driver: https://developer.android.com/kotlin/multiplatform/sqlite · https://developer.android.com/reference/androidx/sqlite/driver/bundled/BundledSQLiteDriver
- FsrsKt (rejected, GPL-3): https://klibs.io/project/TheRockYT/FsrsKt · FSRS implementations: https://github.com/open-spaced-repetition/awesome-fsrs
- Stroke data and animation technique: https://github.com/chanind/hanzi-writer
