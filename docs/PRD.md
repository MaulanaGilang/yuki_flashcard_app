# PRD — Yuki

| | |
|---|---|
| **Status** | Draft v0.4 — German, French, Arabic; reading guide; 2×1 widget; level progression |
| **Date** | 2026-10-29 |
| **Owner** | Gilang (sole user & developer) |
| **Platform** | Android (native) |
| **Companion docs** | `docs/DESIGN-SYSTEM.md` (next), `docs/ARCHITECTURE.md` (next) |

> **Yuki** is the app name (decided 2026-10-29).

### Decisions already made

| Decision | Choice | Consequence |
|---|---|---|
| Audience | **Personal use only**, sideloaded APK | We can use content licensed for personal/non-commercial use. Going public later needs a licensing pass (see §5.4). |
| Progress storage | **On-device + backup file** | No account, no server, no sign-in. Backup/restore via a file and Android Auto Backup. |
| Meaning language | **English** | Back-of-card meanings and translations are in English. English cards use English definitions. |
| Languages | Mandarin (Simplified) first, then Spanish, Russian, English | Mandarin uses HSK levels. The other three use CEFR (A1–C2). |
| Scheduling | Spaced repetition "like Anki" | FSRS algorithm (the scheduler Anki uses today). |
| **Budget** | **Rp 0 target. Hard cap Rp 200,000, one-time payments only, no subscriptions.** | Only free tools, free tiers and free/open data. Any paid item must be one-time and listed in §3.5. |
| Images | A mix is fine: generated illustrations preferred, plus 3D, photos and others | Sources are mixed and unified visually by the design system's card frame (§3.2). |
| Audio | Real human voice wherever possible. Otherwise the best-rated free TTS that sounds closest to a real voice | Human recordings first; TTS voice chosen by a blind listening test (§3.2). |
| English level | Start at **B2** | The English deck covers B2–C2. |
| **Coverage** | Word lists must reach **at least C1** (Spanish, Russian, English) and **at least HSK 6** (Mandarin) | Mandarin ships HSK 1–6 (≈ 5,456 words) in v1.0. ES/RU go A1–C1, EN goes B2–C1, each at launch of that language. |
| **More languages** | Add **German, French and Arabic** (Modern Standard Arabic) | Seven lines in total. Each new language ships A1–C1 as its own pack (§3.1, §5.1). |
| **Target phone** | **OPPO Reno4** (Snapdragon 720G, 8 GB RAM, 1080×2400 AMOLED, ColorOS; shipped with Android 10) | Performance budgets are measured on this phone. ColorOS battery rules affect the widget and reminders (§4.7). |
| Writing | Stroke-order **viewing** is the core; I write on paper. On-phone writing is rare and occasional. | The stroke-order viewer is P0 and paper-friendly. Phone writing is an optional "Practice" button (S5). |

---

## 1. Executive Summary

**Problem statement.** Existing HSK/CEFR flashcard apps either look dated (Anki), lock content behind subscriptions and ads, or have weak review flows. The 1-star reviews of the reference app (*Chinese Vocab: HSK Flashcard*, `com.brilliantcorp.dailychinese`) say you can't go back to words you've studied, and there's no clear way to review them. None of these apps covers Mandarin, Spanish, Russian and English in one place, offline, with real audio.

**Proposed solution.** A beautiful, fully offline Android flashcard app with:
- Illustrated cards and native-quality audio.
- Stroke-order animation for every Chinese character.
- Example sentences.
- An FSRS spaced-repetition scheduler.
- A complete, browsable word library, so every studied word can be found again.

Content ships as pre-built language packs, so nothing needs the network at runtime.

**Success criteria (measurable)**
1. **Habit:** I study on ≥ 5 days/week for 8 consecutive weeks after MVP install. Measured from the in-app streak/heatmap.
2. **Retention:** true retention on mature-card reviews stays within **85–92 %** (FSRS target 90 %). Measured from the review log.
3. **Completeness:** **100 %** of MVP cards (HSK 1–6) have word audio, a sentence, a sentence translation and stroke data for every character. **≥ 95 %** also have an image.
4. **Speed:** cold start to the "Today" screen takes **≤ 1.5 s**, and the card flip runs at **60 fps with 0 janky frames** on my phone. Measured with Macrobenchmark.
5. **Durability:** **0** progress lost across uninstall → reinstall → restore. Verified by an automated backup round-trip test.
6. **Cost:** total spend for the MVP is **Rp 0**, and never more than Rp 200,000 (one-time) over the whole roadmap.

---

## 2. User Experience & Functionality

### 2.1 Persona

**Gilang, a self-directed multilingual learner.** Indonesian, fluent in English, studying several languages in short daily sessions on an Android phone, often with no or poor connectivity.
- Wants long-term retention, not cramming.
- Wants to *see* progress per level (e.g., "HSK 2: 74 % mature").
- Wants hearing the real pronunciation to be effortless.
- Is a visual learner and cares about the app feeling well crafted.
- Frustrations with existing apps: ads and paywalls, cards disappearing after rating, no way to look a word up again, robotic TTS.

### 2.2 Core user flow

```
First launch ─► Pick language ─► Pick level (or quick placement) ─► Set daily goal
                                                                       │
        ┌──────────────────────────────────────────────────────────────┘
        ▼
   TODAY screen ── due reviews · new cards · streak ──► Start session
        │                                                  │
        │                                       ┌──────────▼──────────┐
        │                                       │ FRONT: word + image │◄─┐
        │                                       │  (tap ▶ audio)      │  │
        │                                       └──────────┬──────────┘  │
        │                                          tap / swipe up        │
        │                                       ┌──────────▼──────────┐  │
        │                                       │ BACK: pinyin/IPA,   │  │
        │                                       │ meanings, sentence, │  │
        │                                       │ stroke order, audio │  │
        │                                       └──────────┬──────────┘  │
        │                                     Again · Hard · Good · Easy │
        │                                                  └── next card─┘
        │                                                  ▼
        │                                         Session summary
        ├──► LIBRARY (levels → words → word detail + history)
        ├──► STATS (heatmap, retention, forecast, level mastery)
        └──► SETTINGS (limits, audio, card types, backup/restore, reminders)
```

### 2.3 Card anatomy

**Front (prompt).** Shown for the default *recognition* card type:
- The word in large type: Hanzi for Mandarin; the headword for the others. Spanish nouns show their article (*el/la*). Russian words show stress marks (*молоко́*).
- An example image that illustrates the meaning.
- An audio button. Audio auto-plays on reveal if that setting is on.
- Optional pinyin on the front, toggled in settings and off by default. Hiding it lets you practise reading characters.

**Back (answer).** Appears after a 3D flip animation:

| Element | Mandarin | Spanish | Russian | English |
|---|---|---|---|---|
| Pronunciation | Pinyin with tone marks and tone colours | IPA (optional) | Stress-marked word + IPA (optional) | IPA (UK/US) |
| Meanings | English glosses grouped by part of speech | English glosses + gender | English glosses + gender / aspect pair | English learner definition |
| Grammar chip | Measure word (量词) | Gender, irregular verb flag | Gender, aspect (perfective/imperfective) | Part of speech, countable/uncountable |
| Example sentence | Hanzi + pinyin + English, with audio | Sentence + English, with audio | Sentence (stressed) + English, with audio | Sentence, with audio |
| How to write | **Stroke-order animation per character** (replay, step through, adjustable speed), plus a **numbered stroke strip** to copy onto paper. A small "Practice" button opens the optional writing pad. | — | — | — |
| Word audio | Replay button | Replay | Replay | Replay (UK/US) |

**Rating bar:** Again / Hard / Good / Easy. Each button shows the next interval (e.g., "Good · 3d"), as in Anki.

### 2.4 User stories & acceptance criteria

Priority: **P0** = MVP, **P1** = v1.1, **P2** = later.

#### Studying

**S1 (P0). As a learner, I want a daily session of due reviews plus a set number of new cards, so that I keep up without being overwhelmed.**
- The Today screen shows the due-review count, the new-card count (default 10/day, configurable 0–50) and the current streak.
- A session serves due reviews first, then interleaves new cards (1 new per 4 reviews when both exist).
- New cards are introduced in level order, then by frequency within the level.
- The daily review cap is configurable (default 200). Overflow rolls over to the next day.
- The session can be left at any time. Progress is saved after every single rating.

**S2 (P0). As a learner, I want to flip a card and rate how well I knew it, so that the scheduler adapts to me.**
- Tap the card, or swipe up, to flip. The flip animation lasts 300–400 ms and holds 60 fps.
- Rating buttons are disabled until the card has been flipped.
- Each rating writes an immutable review-log row: card, rating, timestamp, elapsed time, previous and new state.
- The next due date is computed by FSRS, with desired retention 0.90 by default (configurable 0.80–0.97).
- **Undo last rating** is available for the whole session. It restores the previous card state exactly.

**S3 (P0). As a learner, I want to hear real pronunciation, so that I learn the correct sounds and tones.**
- Every MVP word has a word-audio file. Every example sentence has a sentence-audio file.
- Audio starts in ≤ 150 ms after tapping and plays fully offline.
- Optional auto-play on card reveal (front) and on flip (sentence). Both are off by default.
- Audio comes from human recordings where licensed recordings exist. Otherwise it is high-quality neural TTS **pre-generated at build time** and never synthesized on-device (see §3).

**S4 (P0). As a Mandarin learner, I want to see clearly how each character is written, so that I can copy the stroke order onto paper.**
- The back of each Mandarin card shows one stroke-order panel per character in the word.
- Strokes animate in the correct order on a 米字格 practice grid (the guide grid on Chinese practice paper), like a printed workbook.
- Controls: play/replay, **loop**, step forward/back one stroke, speed 0.5×/1×/2×.
- A **numbered stroke strip** shows each step as a cumulative frame: stroke 1, then strokes 1–2, and so on. The current stroke is highlighted, so it can be followed on paper without replaying the animation.
- Each panel shows the stroke count.
- **Write-along mode:** tap "Hold" to keep the screen awake. The animation pauses after each stroke until you tap, so you can write each stroke on paper at your own pace.
- Stroke data exists for 100 % of characters in HSK 1–6 (≈ 1,800 characters under HSK 3.0).
- Tapping a character opens a **character detail** page: large stroke-order viewer, stroke count, radical, pinyin readings, and other words in the library that contain it.

**S5 (P1, low priority). As a Mandarin learner, I occasionally want to practise writing on my phone, e.g., when I'm outside without paper.**
- Writing is **never part of the main study flow**. It opens only from a small "✎ Practice" button on the stroke-order panel or the character detail page.
- Practice pad: draw on a 米字格 grid. Each stroke is checked for order and shape against the stroke medians. After 3 wrong attempts on a stroke, its outline is shown as a hint.
- By default, practice results **do not** affect scheduling. A setting (off by default) turns on a separate scheduled *writing* card type for those who want it later.

**S6 (P1). As a learner, I want different card directions, so that I train listening and production too.**
- Card types per word: **Recognition** (word → meaning, P0), **Listening** (audio only → meaning, P1), **Production** (English → word, P1).
- Each card type is scheduled independently, and can be switched on or off per language.
- Sibling cards of the same word are not shown on the same day.

**S7 (P0). As a learner, I want a summary at the end of a session, so that I feel progress.**
- Shows: cards reviewed, new cards learned, accuracy (% not "Again"), time spent, streak status.
- Lists the words rated "Again" with a one-tap "review these now" extra round. That round does not affect scheduling.

#### Finding words again (fixes the reference app's main complaint)

**S8 (P0). As a learner, I want to browse every word by level and see its state, so that I can always find words I've studied.**
- Library: Language → Level → word list, with status badges *New / Learning / Review / Mastered / Suspended*.
- Filters: status, level, "rated Again in last 7 days", "studied today".
- Search works across headword, pinyin with or without tones (`ni3hao3`, `nihao`, `nǐhǎo`), and English meaning. Results appear in ≤ 100 ms over 11,000 entries.
- The word detail page shows the full back of the card, plus review history (dates and ratings), next due date and a stability indicator.

**S9 (P0). As a learner, I want to control individual words, so that the deck fits me.**
- Per-word actions: *Suspend*, *Mark as known* (skip it as new), *Reset progress*, *Study now* (adds it to today's queue).
- Bulk-select from the library for the same actions.

#### Onboarding & goals

**S10 (P0). As a new user, I want to choose my language and starting level, so that I don't start from words I already know.**
- Language picker. Only installed language packs are selectable.
- Level picker: HSK 1–9 / CEFR A1–C2. Everything below the chosen level can be bulk-marked "known" in one tap.
- Optional 2-minute placement test (P1): sample 30 words across levels, then suggest a starting level.
- Daily goal options: 5 / 10 / 15 / 20 minutes, or a custom new-card count.

**S11 (P0). As a learner, I want to study more than one language, so that each keeps its own progress.**
- Each language has its own deck, settings, limits and stats.
- The Today screen lists every active language with its due count. "Study all" runs the languages one after another.

#### Progress & motivation

**S12 (P0). As a learner, I want stats, so that I can see long-term progress.**
- Year heatmap of daily reviews (GitHub-style), and current/longest streak.
- Per-level mastery bars, e.g., "HSK 2: 412/772 mastered" (mastered means FSRS stability ≥ 21 days).
- True retention (last 30 days), and the review forecast for the next 30 days.
- All stats are computed locally from the review log. A stats screen with a year of data renders in ≤ 500 ms.

**S13 (P1). As a learner, I want a daily reminder, so that I keep my streak.**
- A local notification at a user-chosen time, shown only when cards are due and today's goal isn't met.
- No network required. It survives device reboot.

**S14 (P0). As a learner, I want a 2×1 home-screen widget, so that I see a word of the day without opening the app.**
- The widget shows one word from the active deck (word, reading, meaning). It refreshes daily, and tapping it opens the word detail page.

#### Data safety

**S15 (P0). As a learner, I want to back up and restore my progress, so that I never lose it.**
- **Export** writes a single `.yuki-backup` file (zip containing a JSON/SQLite snapshot and a manifest with schema version and checksum). Any location can be chosen via the Storage Access Framework, including Google Drive and Downloads.
- **Import** validates the checksum and schema version, shows a preview (cards, reviews, last review date) and requires confirmation before replacing data.
- **Android Auto Backup** is enabled for the progress database (the user's own Google account backup, at no cost to us).
- Optional automatic weekly export to a folder the user chooses (P1).
- Backups contain **progress only** (states, review log, settings), not content packs, so they stay well under 5 MB.
- A round-trip test (export → wipe → import) is part of CI and must produce identical card states and review logs.

**S16 (P0). As a learner, I want the app to work with no internet at all, so that I can study anywhere.**
- Every P0 feature works in airplane mode from first launch.
- The MVP app declares **no `INTERNET` permission**. Content packs beyond the bundled one are imported from a file (P1). Downloading them over the network is a later, optional decision (see §5).

#### Reading unfamiliar scripts

**S17 (P0). As a learner, I want to see how to read a word written in an unfamiliar script, so that I can sound it out (e.g. Russian Д doesn't look like D).**
- **Russian and Arabic** cards show a Latin transliteration next to the word on the back (and on the front if "Show Latin letters on the front" is on), e.g. я́блоко → *yábloko*, تُفَّاحَة → *tuffāḥa*. Example sentences get one too.
- **"How to read this word"** opens letter-by-letter tiles: each letter with its Latin sound and a note where it matters ("unstressed: sounds like a", "at the end: sounds like t", "doubled", "makes the vowel long"). Arabic tiles run right to left.
- Word-specific tips appear under the tiles (Russian stress and devoicing, Arabic sun letters, shadda, tanwīn, ة).
- **German, French and Spanish** get the spelling rules that trip up English readers (ei = "eye", eau = "oh", j = throaty h …), shown for the word when they apply.
- **Alphabet guide** per language, reachable from the Library: all 33 Cyrillic letters and the Arabic letters and vowel marks with sound descriptions and **look-alike warnings** (В looks like B but sounds v; Р looks like P but sounds r; Д in italics looks like g), the spelling rules for Latin-script languages, and pinyin tones for Mandarin.
- Transliteration is computed in the app from the word itself (Arabic packs store full vowel marks), so it works for every word without extra pack data.

#### Moving up a level

**S18 (P0). As a learner, I want to know when I'm ready for the next level, so that I move on at the right time.**
- Yuki never blocks you: new words always continue in level order (A1 words, then A2).
- Each level is a **station** with a state:
  - *Not started.*
  - *Riding:* some words introduced.
  - *Arrived:* every word introduced, ≥ 80 % already in Review, and recall on that level's reviews ≥ 85 % over 30 days.
  - *Mastered:* ≥ 90 % of words remembered for 3+ weeks (stability ≥ 21 days).
- Today shows the current station's progress ("A1: 64 of 90 introduced, 40 in review") and announces arrival.
- **Pace guard:** if recall on mature cards over the last 7 days (≥ 30 reviews) drops below 80 %, today's new words are halved; below 70 % they pause; if more reviews are due than the daily cap, new words are halved. Today explains why.
- **Checkpoint test (P1):** an optional 20-word quiz from a level; ≥ 85 % correct marks the rest of that level as known, so you can skip ahead.

#### Yuki, the mascot

**S19 (P0). As a learner, I want Yuki to cheer me on and remind me, so that studying feels friendly and I keep my streak.**
- Yuki is an ink-line avatar (black lines, white fills) whose scarf and snowflake hair clip take the current language's line colour, or brand maroon on neutral screens, with four poses: waving, celebrating, worried, sleepy. (Changed 30 Oct 2026 from a 2D chibi girl made from the owner's AI-generated reference; see DESIGN-SYSTEM §5.13.)
- **Today:** Yuki with a speech bubble at the top. Her mood and line follow the day:
  - no languages yet → waves hello,
  - studied today → celebrates (or sleeps late at night),
  - evening with the streak at risk → worried ("Your 5-day streak ends at 4 am"),
  - otherwise → waves with today's card count.
- A 🔥 streak chip next to the title, lit once today's session is done.
- **Session summary:** Yuki celebrates. **All caught up:** Yuki sleeps.
- **Animation:** a gentle idle "breathing" bob, a pop on mood change, hops when celebrating and when tapped. All motion stops when "Remove animations" is on.
- **Streak widget** (2×1, resizable to 4×1 and 2×2): Yuki's bust, the flame and day count, and her short line. It refreshes hourly and whenever Yuki goes to the background. The word widget (S14) stays available too.
- **App icon:** a flat snowflake on the brand maroon field (DESIGN-SYSTEM §5.13).

### 2.5 Non-goals (explicitly not building)

- Accounts, sign-in, cloud sync, or any backend server.
- Ads, in-app purchases, or analytics or telemetry SDKs.
- An AI tutor or chat. The reference app has one; it needs a network and an API cost.
- Speech recognition or pronunciation scoring (possible v2).
- Grammar lessons, reading passages, stories or a dictionary beyond the curated word lists.
- Traditional Chinese characters (possible v2; stroke data supports it).
- User-created decks or Anki `.apkg` import (possible v2).
- iOS, tablet-optimised layouts, Wear OS (the app must not break on tablets, but there is no special layout).
- A public Play Store release (would need the licensing pass in §5.4).

---

## 3. Content & AI Pipeline Requirements

The app has no AI at runtime. AI is used **offline at build time** to produce content packs. A content pipeline, scripts in this repo, turns open data plus generated assets into versioned language packs.

### 3.1 Level standards

| Language | Standard | Levels | Proposed word source | Licence (verify before use) |
|---|---|---|---|---|
| Mandarin | **HSK 3.0** (2021 standard, 9 levels). HSK 2.0 level shown as a secondary tag. | 1–6, then 7–9 combined | `drkameleon/complete-hsk-vocabulary` and/or `ivankra/hsk30` (has pinyin and POS) | MIT (reported) |
| Spanish | CEFR, aligned to the **Plan Curricular del Instituto Cervantes** (the basis of the DELE exam) | **A1–C1** (C2 later) | PCIC *Nociones específicas* lists, cross-checked with a frequency list | Personal use |
| Russian | CEFR, using the official **TORFL / ТРКИ lexical minimums** (see the table below) | **A1–C1** (C2 later) | The ТРКИ minimum word lists (found online for A1, A2, B2) | Personal use |
| English | CEFR, via the **Oxford 5000** (B2 and C1 words) and the **English Vocabulary Profile** (C2) | **B2–C1** at launch, C2 later (starts at B2) | Oxford 5000 list + EVP C2 list | Personal use |
| German | CEFR, via the official **Goethe-Zertifikat word lists** (A1, A2, B1) plus a frequency list for B2–C1 | **A1–C1** | Goethe-Institut lists; frequency list (e.g. DeReWo) | Personal use |
| French | CEFR, via **FLELex** (a CEFR-graded French lexicon, A1–C2) | **A1–C1** | FLELex (UCLouvain CEFRLex) | Free for research/teaching (CC BY-NC) |
| Arabic | CEFR, via the **Kelly project** Arabic list (A1–C2); Modern Standard Arabic, every word and sentence fully vowelled | **A1–C1** | Kelly list (Språkbanken) + Wiktionary vowelling | CC BY-NC-SA (personal use) |

**Russian ТРКИ lexical minimums.** Levels and word counts are from the official minimum series (Златоуст / ЦМО МГУ):

| ТРКИ level | CEFR | Units | Where it's available | Cost |
|---|---|---|---|---|
| ТЭУ (элементарный) | A1 | 780 | University course file (docx), found online | Free |
| ТБУ (базовый) | A2 | 1,300 | PDF, found online | Free |
| ТРКИ-1 (первый сертификационный) | B1 | 2,300 | Published book only (Андрюшина et al.); no free copy found | **Decided (2026-10-29): a free CEFR-graded frequency list for B1.** The ebook stays an option if the list proves weak |
| ТРКИ-2 (второй сертификационный) | B2 | ≈ 5,100 | PDF (5th ed., 2014), found online; needs OCR/clean-up | Free |
| ТРКИ-3 (третий сертификационный) | C1 | size to verify | No free copy found yet | **Decided: a free frequency list for C1** (the next band after B2), unless a free ТРКИ-3 list turns up during v1.3 prep |
| ТРКИ-4 | C2 | — | Not needed until v2 | — |

There is no single HSK-equivalent exam for Spanish, Russian or English, but each has a CEFR-aligned national standard. The table above uses the closest equivalents.

Approximate **cumulative** HSK 3.0 word counts: L1 500 · L2 1,272 · L3 2,245 · L4 3,245 · L5 4,316 · L6 5,456 · L7–9 ≈ 11,000. Commercial study sites disagree on these figures, so the pipeline takes counts from the chosen source list, not from this table.

### 3.2 Asset sources per field

| Field | Primary source | Fallback |
|---|---|---|
| Mandarin readings and glosses | CC-CEDICT (CC BY-SA 4.0) | Wiktionary via kaikki.org extracts (CC BY-SA) |
| ES/RU/EN glosses, gender, stress, IPA | Wiktionary via kaikki.org (CC BY-SA) | Manual curation |
| Example sentences + English translations | Tatoeba (CC BY 2.0 FR), filtered so every word in the sentence is at or below the card's level. **Prefer sentences that already have a human recording.** | LLM-generated level-appropriate sentence, human spot-checked |
| Stroke order | Make Me a Hanzi `graphics.txt` (Arphic Public License); the same data powers Hanzi Writer | — |

#### Audio: real human voices first

Every source below is free. The pipeline tries them in order and keeps the first good match.

| Order | Source | Covers | Licence |
|---|---|---|---|
| 1 | `hugolpz/audio-cmn` | Mandarin words: ≈ 5,000+ HSK 2.0 words and all 1,707 syllables, recorded by native speakers | CC BY-SA |
| 2 | Shtooka Project (SWAC collections) | Word recordings for Russian, Mandarin and English (≈ 75,000 items total) | Mostly CC BY (per-collection README) |
| 3 | Lingua Libre / Wikimedia Commons / Wiktionary audio | Words in all four languages (Lingua Libre alone has 1.4 M+ recordings in 315+ languages) | CC BY-SA 4.0 (mostly) |
| 4 | Tatoeba sentence audio | **Human-recorded example sentences**, especially English and Spanish (one contributor alone has recorded 100,000+ Spanish sentences) | Per speaker: CC BY / BY-SA / BY-NC; all fine for personal use |
| 5 | **Free neural TTS** (fallback) | Whatever is left, mostly Mandarin sentences and rarer words | See below |

**Choosing the TTS voice (free only).** Before any batch, run a blind listening test: 20 words and 10 sentences per language, rated against real recordings. Candidates:

| Candidate | Why | Cost / limits |
|---|---|---|
| **Microsoft neural voices** (via the free Edge read-aloud service; e.g., `zh-CN-XiaoxiaoNeural`, `ru-RU-SvetlanaNeural`, `es-ES-ElviraNeural`) | Very natural, strong Mandarin tones, no account needed. **Default candidate.** | Free; unofficial endpoint (fine for personal use) |
| Google Cloud TTS (Chirp 3 HD voices) | High quality, official API, generous monthly free tier | Free tier, but requires a billing card on file (verify limits) |
| Gemini 3.1 Flash TTS | Among the top-ranked voices on the Artificial Analysis Speech Arena | Free tier is tiny (reports of ~3–10 requests/day): only for hand-picked fixes |
| ElevenLabs (free plan) | Near-top quality | ~10k characters/month: only for a few problem words |

The whole roadmap needs roughly 1 M characters of TTS, mostly sentences. With the default candidate that costs Rp 0.

**Audio quality rule.** Within one card, the word and the sentence may come from different sources. Within a language, TTS uses one fixed voice so the app sounds consistent. Every audio file is loudness-normalised (−16 LUFS), has silence trimmed (≤ 100 ms padding) and is encoded as Opus/OGG at ≈ 32 kbps. Each card records whether its audio is human or TTS, and a human recording replaces TTS whenever one becomes available.

#### Images: a free mix, unified by one frame

The design system defines a card **image frame** (background tile, corner radius, colour grade, soft shadow). Every image sits inside it, so mixed sources still look like one set.

| Order | Source | Use for | Cost |
|---|---|---|---|
| 1 | **Microsoft Fluent Emoji 3D** (≈ 1,500 glossy 3D objects, MIT licence) | Concrete nouns that have an emoji: fruit, animals, food, transport, weather, body parts, objects | Free |
| 2 | **Generated illustrations** via Cloudflare Workers AI (FLUX.1 schnell), free tier of 10,000 "neurons"/day, which is ≈ 170–230 images/day | Most other picturable words, with one fixed style prompt | Free (free Cloudflare account; HSK 1–3 takes ≈ 1–2 weeks of daily batches) |
| 3 | Photos from Openverse / Wikimedia Commons / Pexels / Unsplash | Places, culture and food where a photo beats a drawing (北京, 长城, 饺子) | Free |
| 4 | **Higgsfield** (your existing 76.75 credits) | Not bulk. Style-reference images, onboarding and empty-state art, the app icon | Already paid; no top-up |
| 5 | Typographic card | Abstract words (但是 "but", 因为 "because") and grammar words: a designed text card, not a forced picture | Free |

Your laptop has no dedicated GPU (Intel UHD, 8 GB RAM), so local image generation is not an option.

### 3.3 Tools the pipeline needs

- Data scripts (Python): fetch and merge word lists, join the dictionary, sentences and stroke data, and emit the SQLite pack.
- The free TTS voice that wins the listening test in §3.2. Build-time only.
- Cloudflare Workers AI (free account) for illustrations, run in daily batches with a fixed style prompt and seed policy. Resumable, so it can stop at the daily free limit and continue tomorrow.
- An LLM for gap-filling sentences and as a gloss sanity-check. Claude, in this workspace, at no extra cost.
- OCR for the scanned ТРКИ-2 PDF (Tesseract, free).

### 3.4 Evaluation strategy for generated content

| Asset | Automated check | Human check | Pass bar |
|---|---|---|---|
| Glosses | Every word has ≥ 1 gloss; no gloss > 80 chars | Review a random 5 % per level | ≥ 98 % of the sample correct |
| Sentences | Tokenise and check every word is ≤ the card's level (Mandarin: jieba; others: lemmatiser) | Review a random 5 % for naturalness and correct translation | ≥ 95 % natural and correct |
| TTS audio | Duration plausible relative to text length; ASR transcript matches the text (CER ≤ 5 %) | Listen to 2 % of files, all Mandarin tones in the sample | 0 wrong tones or wrong words in the sample |
| Images | One image per card at 768×768, WebP ≤ 60 KB | Review 5 % for "does it depict the meaning" and style consistency | ≥ 90 % judged clear |
| Stroke data | Every character in the pack has stroke data | Visually check 20 random characters | 100 % coverage |

Any batch failing its bar is regenerated before packing. Each card stores the source and licence of every asset, for attribution and future licensing.

### 3.5 Cost plan

| Item | Cost |
|---|---|
| Word lists, dictionaries, sentences, stroke data, human audio | Rp 0 (open data) |
| TTS fallback (default candidate) | Rp 0 |
| Illustrations (Cloudflare free tier + Fluent Emoji + free photos) | Rp 0 |
| Higgsfield art (existing credits) | Rp 0 extra |
| Fonts (Google Fonts / OFL) | Rp 0 |
| Android Studio, Kotlin, all libraries | Rp 0 (open source) |
| Distribution (sideloaded APK; no Play Console fee) | Rp 0 |
| *Optional:* ТРКИ-1 (B1) lexical minimum ebook | One-time, small; only if the free B1 fallback isn't good enough |
| **Total** | **Rp 0** (cap: Rp 200,000 one-time) |

---

## 4. Technical Specifications (high level — detail in `ARCHITECTURE.md`)

### 4.1 Proposed stack (to be confirmed in the architecture doc)
- **Kotlin + Jetpack Compose (Material 3, custom theme)**, single-activity, Navigation 3. Native gives the best animation performance and full offline control.
- **Room (SQLite)** for progress. Content packs are **separate, read-only SQLite databases** plus asset folders, so content updates never touch progress data.
- **FSRS-6** scheduler, ported in-repo from the official `py-fsrs` (MIT) and verified against its test vectors. Existing Kotlin libraries were rejected; see `ARCHITECTURE.md` ADR-3.
- **Media3 ExoPlayer** or SoundPool for low-latency audio.
- **Compose Canvas** renders stroke-order animation and the numbered stroke strip from SVG paths. **Jetpack Ink** handles the optional practice pad (P1).
- **WorkManager** drives reminders and scheduled backups. **Glance** builds the widget.
- Minimum SDK: API 26 (Android 8.0), as decided in `ARCHITECTURE.md`.

### 4.2 Data flow

```
 Build time (this repo / laptop)                   Runtime (phone, offline)
 ────────────────────────────────                   ────────────────────────
 word lists ─┐                                      ┌──────────────────────┐
 CC-CEDICT  ─┤                                      │  Content pack (RO)   │
 Wiktionary ─┼─► pipeline ─► pack.sqlite + /audio ─►│  words, senses,      │
 Tatoeba    ─┤   (validate,   + /images + /strokes  │  sentences, assets   │
 strokes    ─┤    normalise,  + manifest.json       └──────────┬───────────┘
 TTS / img  ─┘    evaluate)                                    │ read
                                                    ┌──────────▼───────────┐
                                                    │ Domain (use cases)   │
                                                    │ FSRS scheduler       │
                                                    └──────────┬───────────┘
                                                               │ read/write
                                                    ┌──────────▼───────────┐
                                                    │ Progress DB (Room)   │──► backup file
                                                    │ cards, review_log,   │──► Auto Backup
                                                    │ settings             │
                                                    └──────────────────────┘
```

**Key data concepts.**
- **Word (note):** one lexical item in a content pack, with a stable ID per pack.
- **Card:** one study direction for a word (recognition, listening, production, writing). Cards hold the FSRS state.
- **ReviewLog:** an append-only history of ratings. Card state can always be rebuilt from it.

### 4.3 Integration points
- **None at runtime.** No APIs, no auth, no network.
- **Storage Access Framework:** backup export/import and content-pack import (P1).
- **Android Auto Backup:** the progress DB and settings only. Content packs are excluded because they can be re-installed.

### 4.4 Size & performance budgets

| Item | Budget |
|---|---|
| MVP APK (with Mandarin HSK 1–6: ≈ 5,456 words) | ≤ 350 MB (sideloaded, so no Play size limit applies) |
| Per-word assets | **average ≤ 55 KB per word** in total (image ≈ 35 KB, word audio ≈ 5 KB, sentence audio ≈ 12 KB). Hard caps per file: image 60 KB, word audio 8 KB, sentence audio 20 KB |
| Each later language pack (A1–C1, ≈ 6,000–8,000 words) | ≤ 450 MB, installed as an importable pack |
| Cold start to Today screen | ≤ 1.5 s |
| Card flip and next-card transition | 60 fps, 0 janky frames |
| Audio tap-to-sound latency | ≤ 150 ms |
| Library search (11k entries) | ≤ 100 ms |
| Rating to persisted state | ≤ 50 ms, never on the main thread |

### 4.5 Security & privacy
- No personal data leaves the device. No analytics, crash-reporting SDKs or ads.
- No `INTERNET` permission in the MVP. Notification permission (Android 13+) is requested only when reminders are enabled.
- Backup files are plain (unencrypted) zip files the user controls, and contain no personal identifiers beyond study history.
- The progress DB uses transactions for every rating, so a crash mid-write cannot corrupt state. A schema-migration test covers every DB version.

### 4.6 Design requirements (detail in `DESIGN-SYSTEM.md`)
- A distinctive, award-quality visual identity, researched from Awwwards mobile-app winners. Not stock Material. Direction: tactile "ink & paper" (see `DESIGN-SYSTEM.md`).
- Chinese characters are shown in a **Kai (楷) typeface**, which matches handwriting and the stroke-order data far better than Song or Hei fonts.
- Light and dark themes. Tone colours for pinyin, with a colour-blind-safe option that adds tone-number marks.
- Accessibility:
  - WCAG 2.1 AA contrast.
  - Touch targets ≥ 48 dp.
  - Layout stays intact at 200 % font scale.
  - TalkBack labels on every control, with the card's text read out after a flip.
- Motion: the card flip, rating feedback and progress animations follow a shared motion spec, and respect "Remove animations".
- Fonts must render CJK, Cyrillic and Latin with Spanish diacritics and Russian stress marks (combining acute accent) correctly.

### 4.7 Target device: OPPO Reno4
- Snapdragon 720G, 8 GB RAM, 6.4" 1080×2400 AMOLED. The emulator used for development matches the screen size.
- Shipped with Android 10 (ColorOS 7.2). Yuki's minimum is Android 8, so every later update is covered.
- **ColorOS battery management** can freeze background apps. The widget refreshes itself whenever Yuki goes to the background, and Settings tells you how to allow background activity (Settings › Battery › Yuki) if it stops changing. Reminders (P1) will need the same permission.
- The AMOLED screen makes the dark theme cheap on battery; both themes are first-class.
- Installing: sideload the APK (allow "Install unknown apps" for your file manager).

---

## 5. Risks & Roadmap

### 5.1 Phased rollout

| Phase | Scope | Exit criteria |
|---|---|---|
| **M0: Content pipeline** | Scripts produce a validated Mandarin HSK 1–6 pack (words, glosses, sentences, audio, images, strokes) | All §3.4 pass bars met for HSK 1–6. Images for HSK 4–6 may trail by a few weeks (typographic cards until generated). |
| **MVP (v1.0)** | Mandarin HSK 1–6 bundled. P0 stories S1–S4, S7–S12, S15–S16 | Success criteria 3–5 met; I use it daily for 2 weeks |
| **v1.1** | Pack import from file. Listening/production cards (S6), reminders (S13), widget (S14), placement test, scheduled backups, optional practice pad (S5) | Practice pad stroke checks feel fair on 50 characters |
| **v1.2** | Spanish A1–C1 pack | Same §3.4 bars |
| **v1.3** | Russian A1–C1 pack (with stress marks and aspect pairs) | Same §3.4 bars |
| **v1.4** | English B2–C1 pack | Same §3.4 bars |
| **v1.5** | German A1–C1 pack | Same §3.4 bars |
| **v1.6** | French A1–C1 pack | Same §3.4 bars |
| **v1.7** | Arabic A1–C1 pack (MSA, fully vowelled) | Same §3.4 bars, plus vowelling checked on a 5 % sample |
| **v2.0** | HSK 7–9, C2 for ES/RU/EN. Possible: traditional characters, Anki import, pronunciation feedback | — |

### 5.2 Technical risks

| Risk | Impact | Mitigation |
|---|---|---|
| Human recordings don't cover all words (audio-cmn targets HSK 2.0; Lingua Libre coverage varies) | Mixed human/TTS audio | Pre-generate TTS for gaps with one consistent voice per language. Track the source per file, and replace with human audio when it becomes available. |
| TTS mispronounces Mandarin tones or polyphones (e.g., 行 xíng/háng, 了 le/liǎo) | Teaches wrong pronunciation | Pass pinyin/SSML phoneme hints to TTS, ASR-verify every file, and hand-check all known polyphones. |
| Free image tier is rate-limited (≈ 200/day) | HSK 1–6 takes ~3–4 weeks of daily batches | Start the image batch early (during M0). Use Fluent Emoji and typographic cards first, so fewer images need generating. The pipeline is resumable. |
| Mixed image sources look inconsistent | App feels patchy | One shared image frame and colour grade (design system). Style-reference images from Higgsfield anchor the generated set. |
| Free TTS endpoints change or disappear (unofficial Edge voices) | Can't generate more audio | Audio is generated once and bundled, so the app is never affected. Swap to another candidate in §3.2 for future packs. |
| APK size grows with images/audio | Slow install, storage use | Separate importable packs per level band; WebP and Opus; budget tracked in CI. |
| Bugs in our own FSRS port | Scheduling bugs | Replay 1,000+ histories generated by the official `py-fsrs` and require identical results (`ARCHITECTURE.md` §2.5). |
| Progress loss from a bug, migration or new phone | Breaks trust | Append-only review log, transactional writes, migration tests, export/import round-trip test, Auto Backup. |
| HSK 3.0 lists differ between sources; exam rollout still in flux in 2026 | Wrong level tags | Pin one source list with a version in the manifest. Show HSK 2.0 tags too. Repacking is cheap. |
| ТРКИ-1 (B1) list has no free copy; ТРКИ-2 PDF is a scan | Gaps or OCR errors in Russian levels | Use a CEFR-graded frequency list for B1, or buy the ebook (one-time, small). OCR the B2 PDF and spot-check it against Wiktionary. |

### 5.3 Open questions

Resolved in v0.2:
- Image style is a mix, unified by one frame (§3.2).
- Human audio first, then the best free TTS (§3.2).
- English starts at B2.
- Russian uses the ТРКИ minimums (§3.1).

Still open:
1. **TTS winner:** picked after the blind listening test in M0.
2. ~~**ТРКИ-1 (B1) source**~~ Decided 2026-10-29: a free frequency list for B1 (and for C1).

### 5.4 If this ever goes public
Before any public release:
- Re-check licences. CC BY-SA content requires attribution and share-alike for derived databases. Oxford lists and PCIC need permission.
- Re-generate any AI assets whose terms forbid redistribution.
- Add a privacy policy and Play Data Safety form, and an attribution screen. Every asset already stores its source, so the attribution screen is easy to build.

---

## Appendix A — Reference app teardown (*Chinese Vocab: HSK Flashcard*)

Play Store listing, observed 2026-10-29: 4.9★, 10K+ downloads, contains ads and in-app purchases.

| Their feature | Ours |
|---|---|
| ~5,000 hand-crafted illustrations | AI-generated illustrations in one consistent style (§3.2) |
| Native audio | Human recordings + pre-generated neural TTS (§3.2) |
| Adaptive SRS | FSRS-6, with interval preview and undo (S2) |
| Daily challenges, streaks, XP, collectibles | Streak + heatmap + session summary. No XP or collectibles in MVP (keep it calm). |
| AI tutor | Non-goal (needs network and API cost) |
| Heat-map calendar, stats | Yes (S12) |
| Reviews say: "can't go back to review words", "hard to find past lessons" | Full library with history, filters and search (S8, S9) |

## Appendix B — Sources consulted
- Reference app: https://play.google.com/store/apps/details?id=com.brilliantcorp.dailychinese
- HSK 3.0 overview and counts: https://www.thatsmandarin.com/hsk/hsk-3-0-2026-update-what-chinese-learners-need-to-know/ · https://mandarinbean.com/new-hsk-vocabulary/
- HSK word lists: https://github.com/drkameleon/complete-hsk-vocabulary · https://github.com/ivankra/hsk30
- FSRS in Kotlin: https://klibs.io/project/TheRockYT/FsrsKt · https://github.com/open-spaced-repetition/awesome-fsrs
- Mandarin audio: https://github.com/hugolpz/audio-cmn
- Lingua Libre: https://en.wikipedia.org/wiki/Lingua_Libre
- Stroke data: https://github.com/chanind/hanzi-writer (built on Make Me a Hanzi)
- Oxford 3000/5000: https://www.oxfordlearnersdictionaries.com/about/wordlists/oxford3000-5000
- ТРКИ minimums: https://www.plus.ac.at/wp-content/uploads/2021/06/Leksicheskii_minimum_po_russkomu_iazyku_kak_inostrannomu_2_uroven.pdf (B2) · https://cro.chel-edu.ru/innovatika/sovremennyy-rebenok-kakoy-on/baza.pdf (A2) · https://is.muni.cz/el/ped/jaro2017/RJ2MK_DIS4/___________.docx (A1) · https://journals.rudn.ru/russian-test/article/view/36501/ru_RU (level sizes)
- Shtooka: https://wiki.creativecommons.org/wiki/Shtooka_Project_-_free_audio_collections_of_words
- Tatoeba audio licensing: https://en.wikipedia.org/wiki/Tatoeba
- TTS rankings: https://artificialanalysis.ai/text-to-speech/leaderboard
- Gemini TTS free-tier reports: https://discuss.ai.google.dev/t/quota-increase-request-gemini-3-1-flash-tts-100-rpd-despite-active-billing/176598
- Cloudflare Workers AI pricing: https://developers.cloudflare.com/workers-ai/platform/pricing/
- Fluent Emoji (MIT): https://github.com/microsoft/fluentui-emoji
- Design reference: https://www.awwwards.com/sites/goshuin-every-stamp-a-memory
