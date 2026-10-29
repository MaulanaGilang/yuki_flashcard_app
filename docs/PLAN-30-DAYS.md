# Yuki: the 30-day finish

**Window:** Day 1 = Thu 29 Oct 2026. Day 30 = Fri 27 Nov 2026, when the Claude subscription ends.
**Rule:** everything is built by **Day 21 (Wed 18 Nov)**. Days 22–24 are for testing and Days 25–30 for fixing. Nothing ships half-done: a language either has a complete pack or waits for v1.1.
**You** (the owner) test nothing until Day 22. Until then, Claude checks its own work with unit tests, emulator runs and the pipeline's evaluate reports.

## What "done" means on Day 21

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

### Week 1: Days 1–7 (29 Oct – 4 Nov)
- **Mandarin pack finished:** audio done, free-image pass, evaluate, package `.kpack`. *(running now)*
- **Shared multi-language engine:** generalise the pipeline from Mandarin-only to recipes per language, with the kaikki.org ingest, Tatoeba sentences, a TTS voice per language, the shared concept-image library and CEFR banding.
- **Spanish and English packs** built end to end on the new engine. They're the easiest, so they prove it works.
- `pipeline/tools/daily.ps1` plus a "Running it yourself (no Claude)" README.

### Week 2: Days 8–14 (5 – 11 Nov)
- **Russian:** word list (ТРКИ plus Anki/KELLY for B1/C1), stress marks, transliteration check against the app's reading guide.
- **German:** articles and gender on every noun, plural forms.
- **French:** gender, elision (l', d'), liaison notes where Wiktionary has them.
- App checks with real packs: big-pack performance (library search under 100 ms, study start, stats over a year of fake history), gender chips and the reading guides on real data.

### Week 3: Days 15–21 (12 – 18 Nov)
- **Arabic:** fully vowelled headwords and sentences, the transliteration check, right-to-left layout on every screen.
- **All seven packs** re-packaged with every picture made so far, then evaluated (meaning coverage, audio, broken files, size).
- **Release APK v1.0** signed, with packs installed by `.kpack` (they're too large to bundle in the APK).
- Hand-over docs: how to rebuild a pack, how to add images, how to install updates, how to restore a backup.

### Days 22–24 (19 – 21 Nov): you test
- Install the v1.0 APK and all seven packs on the Reno4.
- Use the evaluate spot-check sheets (`pipeline/work/<pack>/report.html`) to check content quickly.
- Write every problem in one list (screen, word, what's wrong).

### Days 25–30 (22 – 27 Nov): fix
- Fix the list in order: crashes, then wrong content, then confusing UI, then polish.
- Final build, final push, and switch the daily image job to Windows Task Scheduler.
- **Day 30:** everything is in GitHub, and the keystore is backed up off the laptop.

## Risks and what happens then

| Risk | Plan |
|---|---|
| Claude usage limits slow the build (already hit once) | Languages run as parallel sessions in Weeks 2–3; if limits bite, Arabic moves to a "v1.1 recipe ready" state with docs. |
| A free word list is missing or unusable | Anki shared deck for that level (word list only), then frequency banding. |
| Pack size too large for the phone | Opus audio at a lower bitrate; sentence audio for the first two sentences only. |
| Picture quality looks uneven | Typographic cards for anything weak; FLUX keeps replacing pictures after Day 30. |
| ColorOS kills reminders or the widget | Battery-settings note is in onboarding and Settings; checked during testing. |
