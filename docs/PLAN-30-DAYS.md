# Yuki: the 30-day finish

**Window:** Day 1 = Thu 29 Oct 2026. Day 30 = Fri 27 Nov 2026, when the Claude subscription ends.
**Rule (fast plan, agreed 30 Oct):** everything is built by **Day 6 (Tue 3 Nov)**. Testing starts **Day 7 (Wed 4 Nov)**, and Days 7–30 are for testing and fixing. A slip of a day or two (usage limits, a bad word list) is fine. **Day 21 (Wed 18 Nov) stays the hard fallback** for the build. Nothing ships half-done: a language either has a complete pack or waits for v1.1.
**You** (the owner) test nothing until the build is done. Until then, Claude checks its own work with unit tests, emulator runs and the pipeline's evaluate reports.

## What "done" means at the end of the build

| Language | Levels | Approx. words | Word list (free) | Pack |
|---|---|---|---|---|
| Mandarin | HSK 1–6 | 5,456 | HSK 3.0 official | `zh-hsk-1-6` |
| Spanish | A1–C1 | ~6,000 | CEFRLex ELELex / PCIC lists, frequency cross-check | `es-cefr-a1-c1` |
| English | B2–C1 | ~3,500 | CEFR-J (to B2) + Octanove C1 (CC BY-SA) | `en-cefr-b2-c1` |
| Russian | A1–C1 | ~7,000 | ТРКИ A1/A2/B2; B1/C1 from an Anki deck or KELLY list, frequency fallback | `ru-cefr-a1-c1` |
| German | A1–C1 | ~6,500 | Goethe A1–B1; B2–C1 from DeReWo frequency | `de-cefr-a1-c1` |
| French | A1–C1 | ~6,000 | CEFRLex FLELex | `fr-cefr-a1-c1` |
| Arabic (MSA) | A1–C1 | ~5,000 | KELLY Arabic list, frequency fallback | `ar-cefr-a1-c1` |

*Each source's licence and coverage is checked on the first day of that language. If a source fails, the fallback is a frequency list banded to CEFR sizes.*

**Every card has:** headword (with gender, measure word or vowel marks where the language needs them), reading (pinyin, IPA or transliteration), English meanings, one or more example sentences with translations, word audio and sentence audio, and a picture or a typographic card.

**Shared sources for all languages:** Wiktionary via kaikki.org (meanings, IPA, gender, Arabic vowelling; CC BY-SA), Tatoeba sentences (CC BY 2.0 FR), and edge-tts neural voices (unlimited).

## Pictures: free first, generated later

Pictures are the slowest part: Cloudflare's free tier makes about 170 images a day, about 3,500 by Day 21. So:

1. **One picture library shared by all languages.** "Apple" is the same picture in all seven packs. Images are keyed by English concept, not by word, which cuts the work to about 4,000 unique concrete concepts.
2. **Free sources fill it first:** Fluent Emoji (MIT), then OpenMoji and Twemoji, then Openverse and Wikimedia Commons photos with CC0, CC BY or CC BY-SA licences, for concrete nouns. Each image's credit goes in `credits.json`.
3. **Abstract words** ("because", "maybe", "however") get a clean typographic card. That's deliberate, not a gap.
4. **FLUX** runs every morning on the most frequent concrete concepts that still look weak. Better pictures arrive as pack updates after Day 30; you install them from Settings.

## Schedule

Each language's audio takes about 5–8 hours of laptop time (12,000–17,000 clips), so builds run overnight and one at a time; parallel runs risk the TTS service throttling us. Recipes are written and checked during the day.

| Day | Date | Work | Laptop builds |
|---|---|---|---|
| 1 | Thu 29 Oct | Shared multi-language engine, Spanish and English recipes, `daily.ps1` | Mandarin audio |
| 2 | Fri 30 Oct | **Mandarin pack done** (106 MB, 5,456 words). German recipe finished: articles and gender on every noun, plurals | Spanish, then English (overnight) |
| 3 | Sat 31 Oct | **Russian** recipe: KELLY + ТРКИ-2 levels, stress marks, aspect pairs, transliteration check. **French** recipe: gender, elision (l', d') | German |
| 4 | Sun 1 Nov | Check the German pack's report. App checks with real packs: library search under 100 ms, study start, gender chips and reading guides on real data | Russian, then French |
| 5 | Mon 2 Nov | **Arabic** recipe: fully vowelled headwords and sentences, Wiktionary example sentences, transliteration check, right-to-left layout on every screen | Arabic |
| 6 | Tue 3 Nov | **All seven packs** re-packaged with every picture made so far, then evaluated. **Release APK v1.0** signed. Hand-over docs | Final repackage |

### From Day 7 (Wed 4 Nov): you test, then fix
- Install the v1.0 APK and all seven packs on the Reno4.
- Use the evaluate spot-check sheets (`pipeline/work/<pack>/report.html`) to check content quickly.
- Write every problem in one list (screen, word, what's wrong).
- Fix the list in order: crashes, then wrong content, then confusing UI, then polish. Test, fix, repeat until Day 30.
- Pictures keep improving: the daily FLUX job runs every morning, and updated packs install from Settings.
- Before Day 30: final build, final push, and switch the daily image job to Windows Task Scheduler.
- **Day 30 (Fri 27 Nov):** everything is in GitHub, and the keystore is backed up off the laptop.

## Risks and what happens then

| Risk | Plan |
|---|---|
| Claude usage limits slow the build (already hit twice) | Builds keep running while Claude is at its limit (keep the app open, laptop awake); only recipe writing pauses. A slip moves the dates by a day or two, never past Day 21. If limits bite hard, Arabic moves to a "v1.1 recipe ready" state with docs. |
| Wikimedia throttles downloads (HTTP 429) | `YUKI_CONTACT` in `pipeline/.env` identifies the downloader; skipped human recordings fall back to TTS and are retried on later runs. |
| A free word list is missing or unusable | Anki shared deck for that level (word list only), then frequency banding. |
| Pack size too large for the phone | Opus audio at a lower bitrate; sentence audio for the first two sentences only. |
| Picture quality looks uneven | Typographic cards for anything weak; FLUX keeps replacing pictures after Day 30. |
| ColorOS kills reminders or the widget | Battery-settings note is in onboarding and Settings; checked during testing. |
