# Content sources per language

Checked 2026-10-29. All sources are free. Licences are noted because every pack ships a `credits.json`. The app is personal use (not sold), so NC (non-commercial) sources are fine; SA (share-alike) sources mean the pack keeps the same licence.

## Shared by every language

| What | Source | Licence | Notes |
|---|---|---|---|
| Meanings (English), POS, IPA, gender, plurals, Arabic vowelling | Wiktionary via kaikki.org, English-Wiktionary extract per language: `https://kaikki.org/dictionary/<Language>/kaikki.org-dictionary-<Language>.jsonl` (Spanish 1.0 GB; dump 2026-10-01) | CC BY-SA 4.0 / GFDL (Wiktionary) | The per-language postprocessed files are marked **deprecated** on kaikki.org. Fetch them early and pin the sha256 in `fetch.lock.json`. Fallback: stream-filter `https://kaikki.org/dictionary/raw-wiktextract-data.jsonl.gz` (2.9 GB) by `lang_code`. `kaikki.org/downloads/<code>/…` files are the *non-English* Wiktionaries (glosses in that language), so don't use them for meanings. |
| Example sentences + English translations | Tatoeba per-language exports: `https://downloads.tatoeba.org/exports/per_language/<iso3>/<iso3>_sentences_detailed.tsv.bz2`, `<iso3>-eng_links.tsv.bz2`, plus `eng_sentences.tsv.bz2` | CC BY 2.0 FR; audio licence per speaker | iso3 codes: spa, eng, rus, deu, fra, ara. |
| Human word audio (first choice) | Lingua Libre on Wikimedia Commons, category `Lingua Libre pronunciation-<iso3>` | CC BY-SA 4.0 (mostly; check per file) | Same mechanism as the zh recipe's `lingua_libre`. |
| TTS fallback | edge-tts | Output used for personal study | Voices below (from `edge-tts --list-voices`, 2026-10-29). |
| Frequency backup / C1 top-up | hermitdave/FrequencyWords, 2018 OpenSubtitles lists (`content/2018/<code>/<code>_50k.txt`) | CC BY-SA 4.0 | Used to rank words inside a level and to fill thin levels. |
| Pictures | Shared concept library: Fluent Emoji (MIT), then OpenMoji (CC BY-SA 4.0), Twemoji (CC BY 4.0), Openverse / Wikimedia Commons (CC0, PDM, BY, BY-SA), then FLUX later | per image, in `credits.json` | Keyed by English concept, so all languages share one picture per concept. Abstract words get typographic cards. |

**Voices:**

| Language | Primary | Alternate |
|---|---|---|
| es (Spain) | `es-ES-ElviraNeural` (F) | `es-ES-AlvaroNeural` (M) |
| en | `en-GB-SoniaNeural` (F) | `en-US-AvaNeural` (F), `en-US-AndrewNeural` (M) |
| ru | `ru-RU-SvetlanaNeural` (F) | `ru-RU-DmitryNeural` (M) |
| de | `de-DE-KatjaNeural` (F) | `de-DE-ConradNeural` (M) |
| fr | `fr-FR-DeniseNeural` (F) | `fr-FR-HenriNeural` (M) |
| ar (MSA) | `ar-SA-ZariyahNeural` (F) | `ar-SA-HamedNeural` (M) |

**Decided 2026-10-30:** each language uses **both** voices: one female, one male. A word keeps one voice, picked by a stable hash of its id, and its example sentence uses the other one, so every card has both speakers. Human recordings still come first. English is **American**: `en-US-AvaNeural` (F) and `en-US-AndrewNeural` (M), with US IPA preferred. The Mandarin audio already made stays as it is; a male voice for zh sentences (`zh-CN-YunxiNeural`) is an optional later update.

## What the app reads from each word (pack contract)

`word.headword` is what the card shows big:
- **es, fr:** nouns carry their article, e.g. `el libro`, `la manzana`, `l'eau (f.)` → `l'eau`.
- **de:** nouns carry their article, e.g. `der Hund`.
- **ru:** stress mark U+0301 on the stressed vowel, e.g. `молоко́`; `search_key` drops it.
- **ar:** fully vowelled.

`word.extra_json` keys the app reads:

| Key | Type | Example | Shown as |
|---|---|---|---|
| `gender` | string | `"m."`, `"f."`, `"n."`, `"m./f."` | under the headword, plus a chip |
| `measure_word` | string (zh) | `"个"` | chip `量 个` |
| `syllables`, `tones` | arrays (zh) | `["píng","guǒ"]`, `[2,3]` | tone-coloured pinyin |
| `grammar` | array of short strings | ru `["impf.", "pf. сказа́ть"]`; de `["pl. die Hunde"]`; es/fr `["irregular"]`; en `["uncountable"]` | one chip each, in order |

Keep chips under about 18 characters. `word.reading` is IPA for es/fr/de/en (`/manˈθana/`) and the stressed form or transliteration source for ru/ar. The app makes the Latin transliteration itself.

## Word lists (levels)

### Spanish, A1–C1: ELELex (CEFRLex)
- `https://cental.uclouvain.be/cefrlex/static/resources/es/ELELex.tsv`: 14,290 rows. Columns are `word`, `tag` (FreeLing), `level_freq@a1…c1`, `total_freq@total`, `nb_doc@a1…c1`, `nb_doc@total`. Tag map: `https://cental.uclouvain.be/cefrlex/static/tagsets/es/freeling_to_elelex.yaml`.
- Licence: **CC BY-NC-SA 4.0**. Cite François et al., ELELex (CLIN 2017 / PLIN 2018).
- Level rule: a word's level is the **first level where it appears in ≥ 2 textbooks** (`nb_doc@<level> >= 2`), content words only (N, V, A, R tags). That gives roughly A1 1,792 · A2 2,012 · B1 1,480 · B2 844 · C1 606 = **6,734**. C1 is thin, so top it up with FrequencyWords `es` ranks 6,000–12,000 not already listed, capped near 1,200 C1 words.
- Function words (articles, prepositions, pronouns) come from the same file, filtered to `nb_doc@total >= 20`, and are kept at their first level.

### English, B2–C1 (the user starts at B2)
- B2: CEFR-J Vocabulary Profile 1.5, `https://raw.githubusercontent.com/openlanguageprofiles/olp-en-cefrj/master/cefrj-vocabulary-profile-1.5.csv` (columns `headword,pos,CEFR,...`). B2 has **2,778** entries. Licence: free for research and commercial use **with citation**; copyright Tono Laboratory, TUFS.
- C1: Octanove Vocabulary Profile C1/C2 1.0, `.../octanove-vocabulary-profile-c1c2-1.0.csv` (columns `headword,pos,CEFR,notes`). C1 has **1,111** entries. Licence: **CC BY-SA 4.0**.
- Total about **3,900**. CEFR-J A1–B1 (5,021) are left out, as the PRD says (English starts at B2). Headwords like `a.m./A.M./am/AM` are split on `/`, and the first form is the headword.

### Russian, German, French, Arabic (Weeks 2–3)
Order of preference: official free list > an Anki shared deck for that exam level (headword and level only, deck URL and author credited) > FrequencyWords banded to the PRD level sizes.

- **fr: FLELex (found, ready).** `https://cental.uclouvain.be/cefrlex/static/resources/fr/FleLex_TT_Beacco.tsv` (14,236 lemmas, TreeTagger POS; also `FleLex_TT.csv`, and `FleLex_CRF.csv` with 17,871 lemmas). Licence **CC BY-NC-SA 4.0**. Same first-level-in-≥2-textbooks rule as ELELex. Levels A1–C2; keep A1–C1.
- **de: Goethe official lists (found).** A1 `https://www.goethe.de/pro/relaunch/prf/ca/A1_SD1_Wortliste_02.pdf` (also the newer `https://www.goethe.de/pro/relaunch/prf/sw/Goethe-Zertifikat_A1_Fit1_Wortliste.pdf`), B1 `https://www.goethe.de/pro/relaunch/prf/da/Goethe-Zertifikat_B1_Wortliste.pdf`. The A2 file is still to find on goethe.de. These are PDF tables: extract them with pdfplumber, and check articles and plurals. There's no official B2/C1 list (Goethe doesn't publish one), so B2–C1 come from FrequencyWords `de` banded to size, minus A1–B1 words. Copyright Goethe-Institut; personal use only.
- **ru and ar: KELLY lists (downloaded and checked).** KELLY (Kilgarriff et al. 2014, *Language Resources and Evaluation*, doi:10.1007/s10579-013-9251-2) is hosted at `https://ssharoff.github.io/kelly/` and prefetched to `work/prefetch/kelly/`. The licence is the project's **CC BY-NC-SA 2.0** (no separate file ships with the ru/ar lists; cite the paper). Files are `.xls`, so add `xlrd==2.0.1` to the pipeline.
  - `ru_m3.xls`: 8,958 rows, columns `Lemma, CEFR, POS, Frq abs, Frq ipm`. Normalise CEFR to upper case: A1 938, A2 1,060, B1 1,562, B2 3,423, C1 1,393 (**8,376** at A1–C1). ТРКИ A1/A2/B2 (PRD §3.1 links) take priority, KELLY fills B1 and C1, and words in both keep the ТРКИ level.
  - `ar_m3.xls`: 8,893 rows. Headword is column 5 (`الكلمة الأصلية Original word`, unvowelled), level column 6 (`CEFR level`), POS column 3 (Arabic labels: اسم noun, صفة adj, فعل verb…), frequency column 7. Corrections are in column 4: use them when filled. A1 775, A2 1,246, B1 1,553, B2 1,703, C1 1,440 (**6,717** at A1–C1). It's the main Arabic list. Vowel the headwords from Wiktionary (kaikki `Arabic` entries, matched on the unvowelled form plus POS); words without a vowelled Wiktionary form are dropped and logged.
- **Arabic sentences are thin on Tatoeba** (the `ara` export is 1.2 MB, against 9–22 MB for spa/rus/deu/fra). Use Wiktionary usage examples (kaikki `senses[].examples` with `english`) as the second sentence source, and hand-written CC0 gap fillers (like `zh_extra_sentences.tsv`) for the rest.
- All of the above was prefetched on 2026-10-29 to `pipeline/work/prefetch/` (kaikki dumps for Spanish, English, Russian, German, French and Arabic, plus every word list, frequency list and Tatoeba export).
- If KELLY can't be used, take an Anki shared deck for ТРКИ-1 / ТРКИ-3 (Russian) or an MSA frequency deck (Arabic), word list only.
