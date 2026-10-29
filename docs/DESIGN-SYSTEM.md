# Design System — Yuki

| | |
|---|---|
| **Status** | v0.1 — direction chosen, tokens defined, ready for review |
| **Date** | 2026-10-29 |
| **Platform** | Android, Jetpack Compose, Material 3 (brand-themed) |
| **Companion docs** | `docs/PRD.md` (v0.2), `docs/ARCHITECTURE.md` (next) |

---

## 1. Direction: "Every word is a card you collect along the line"

You chose a blend of three well-reviewed references. Each one has a single job, so they don't compete:

| Layer | Borrowed from | Its job in Yuki |
|---|---|---|
| **Structure** | Transit wayfinding (metro and KRL line maps) | Each language is a **line** with its own colour. Each level is a **station**. Your progress is where the train is. Navigation, progress bars and the Today screen all speak this language. |
| **Mood** | **Drops** (Google Play Best App 2018; 4.7★, 319K reviews) | Study screens are **drenched in the line's colour**, the whole screen, not a thin accent. White art, big confident type, fast and cheerful but grown-up. |
| **Object** | **Pokémon TCG Pocket** (Google Play Best Game 2025, praised for tactile cards) | The flashcard is a **physical collectible card**: proper card proportions, a tilt-reactive shine, and a **foil finish that upgrades as you master the word**. |

**What it refuses:**
- The category defaults: Duolingo-style cartoon mascots, Anki's grey utility look.
- The over-used "zen" opposite: cream paper, serif, red seal stamp.

**The memory test.** After one session you should remember a colour-flooded screen, a card that shines when you tilt your phone, and a little train moving one station closer to HSK 3.

### Where each layer applies

| Surface | Mode | Treatment |
|---|---|---|
| Study session, session summary, level page | Experience | **Drenched**: full line-colour field, collectible card, route strip |
| Today | Operate (with personality) | Neutral "platform" ground with **line strips**, one per language |
| Library, word detail, stats, settings | Operate | **Restrained**: neutral surfaces. The line colour appears only in bullets, badges and charts. Standard Material components. |

---

## 2. Colour

**Strategy:** drenched where you study, restrained where you manage. Colour roles map onto Material 3, so system components (dialogs, snackbars, switches) theme correctly. The **primary** role is the current language's line colour, swapped by theme per language. Dynamic Colour (wallpaper-based) is off: the line colours are the identity.

### 2.1 Line colours (one per language)

All values were checked with the WCAG formula. White text on every field passes **AA (≥ 4.5 : 1)**.

| Line | Token | Field (light) | White text | Deep | Art-tile tint |
|---|---|---|---|---|---|
| 中文 Mandarin, **Persimmon line** | `line.zh` | `#D63A22` | 4.68 | `#8E2414` | `#FAE7E4` |
| ES Spanish, **Bougainvillea line** | `line.es` | `#B42D78` | 5.85 | `#6E1A4A` | `#F6E6EF` |
| RU Russian, **Cobalt line** | `line.ru` | `#2B4FD8` | 6.54 | `#1A2F86` | `#E6EAFA` |
| EN English, **Jade line** | `line.en` | `#0B7A64` | 5.27 | `#064B3D` | `#E2EFEC` |
| DE German, **Ochre line** | `line.de` | `#8C5E00` | 5.65 | `#553900` | `#F1ECE0` |
| FR French, **Iris line** | `line.fr` | `#6A3FD1` | 6.47 | `#3E2380` | `#EDE8F9` |
| AR Arabic, **Olive line** | `line.ar` | `#4E7614` | 5.35 | `#2F470B` | `#EAEFE3` |

**Stations deepen as you go.** Each level's field is the line colour mixed toward *Deep*, so higher levels feel heavier and more serious. Contrast rises with depth.

| Line | Station ramp (level 1 → last) |
|---|---|
| Persimmon (HSK 1–9) | `#D63A22` `#D13821` `#CC3720` `#C7351F` `#C2341E` `#BD321D` `#B8311C` `#B32F1B` `#AE2E1A` |
| Bougainvillea (A1–C2) | `#B42D78` `#AC2B73` `#A5296E` `#9D2769` `#952564` `#8E235F` |
| Cobalt (A1–C2) | `#2B4FD8` `#294BCF` `#2748C6` `#2544BD` `#2441B4` `#223DAB` |
| Jade (B2–C2 used) | `#0B7A64` `#0A7560` `#0A705B` `#096A57` `#096553` `#08604F` |

**Dark theme fields** use `mix(field, deep, 0.35)`. That stays vivid at night, keeps white text above 6 : 1, and leaves enough separation from the dark card. Plain *Deep* sat at only ~1.5 : 1 against the card, so it was rejected.

### 2.2 Platform neutrals

These are cool, slightly blue-tinted greys, like station concrete and enamel signs, chosen so the four line colours read cleanly on them.

| Token | Light | Dark | Use |
|---|---|---|---|
| `neutral.ground` | `#EEF0F3` | `#0D0F13` | App background (Operate screens) |
| `neutral.surface` | `#FFFFFF` | `#181B21` | Cards, sheets, list rows |
| `neutral.surface2` | `#F6F7F9` | `#22262E` | Grouped rows, inputs, dark-theme card face |
| `neutral.outline` | `#D5D9E0` | `#343944` | Dividers, outlines |
| `ink.primary` | `#14161B` | `#F1F2F4` | Body text (15.9 : 1 / 17.1 : 1) |
| `ink.secondary` | `#4A505C` | `#A6ACB8` | Secondary text (8.1 : 1 / 7.6 : 1) |

### 2.3 Pinyin tone colours

Tones are always shown by **colour plus tone mark**, so colour is never the only signal. An optional setting also adds tone numbers (`ma3`) for colour-blind users. All values pass AA on both card faces.

| Tone | Light card | Ratio | Dark card | Ratio |
|---|---|---|---|---|
| 1 ā (high) | `#C8102E` | 5.88 | `#FF7A86` | 6.88 |
| 2 á (rising) | `#A65300` | 5.44 | `#FFAE5C` | 9.41 |
| 3 ǎ (dip) | `#1C7F35` | 5.08 | `#62D584` | 9.34 |
| 4 à (falling) | `#1F4FD1` | 6.78 | `#8AABFF` | 7.68 |
| neutral | `#5F6673` | 5.78 | `#A0A7B3` | 7.12 |

Tone colours belong to sound only. The stroke-order viewer does **not** use them: strokes are ink, with the line colour marking the active stroke.

### 2.4 Semantic & rating colours

| Token | Light | Dark | Use |
|---|---|---|---|
| `rate.again` | `#C62828` | `#FF8A80` | "Again" button, lapse marks |
| `rate.hard` | `ink.secondary` | `ink.secondary` | "Hard" (neutral on purpose) |
| `rate.good` | current line field | current line field | "Good": the default, the line's own colour |
| `rate.easy` | current line deep | `#FFFFFF` on line | "Easy" |
| `state.success` | `#1C7F35` | `#62D584` | Backup OK, import OK |
| `state.warning` | `#A65300` | `#FFAE5C` | Schema mismatch, low storage |

### 2.5 Foil tiers (mastery)

Foil is the reward channel. It replaces XP and badges, so the card itself shows how well you know the word. Tiers come from the FSRS state and stability.

| Tier | Rule | Finish | Pips |
|---|---|---|---|
| **New** | never reviewed | Plain card, no border sheen | ○○○ |
| **Learning** | in learning/relearning steps | Matte: subtle paper grain | ●○○ |
| **Review** | stability < 21 days | Gloss: soft specular highlight that follows tilt | ●●○ |
| **Mastered** | stability ≥ 21 days | **Holo foil**: iridescent sweep (`#FF9AA2 → #FFE29A → #9AF5C9 → #9AD4FF → #C9A7FF` at 30 % over the art), follows tilt | ●●● |
| **Gold** | stability ≥ 180 days | Gold foil rim (`#E8C66A`) + holo art | ★ |

---

## 3. Typography

Each face was chosen for a script job. All are free and open (OFL) and bundled in the app; no font downloads at runtime.

| Role | Face | Why this one |
|---|---|---|
| **Display / signage**: station names, level bullets, big counts, the summary board | **Unbounded** | Wide, heavy and geometric, like metro signage and Drops' chunky confidence. Covers Latin and Cyrillic, so Russian line names render in the same voice. Used sparingly. |
| **UI & body**: meanings, sentences (Latin/Cyrillic), labels, buttons | **Onest** | A clean modern grotesk designed with Cyrillic as a first-class script, so Russian never looks like a fallback. Tabular figures for stats. |
| **Hanzi**: headwords, sentences, character detail | **LXGW WenKai (霞鹜文楷)** | A **Kai (楷)** face that matches handwriting and the stroke-order data, so what you see is what you write on paper. Subset to the characters in installed packs (≈ 3,000 for HSK 1–6, roughly 2–3 MB instead of ~20 MB). |
| **Arabic**: headwords, sentences, alphabet guide | **Noto Naskh Arabic** | Naskh is the clear book style learners read first, and it places every vowel mark (fatḥa, kasra, ḍamma, shadda, sukūn, tanwīn) cleanly. Taller line height (≈ 1.7×) so marks never clip. |
| **Fallback** | Noto Sans / Noto Sans SC | For any glyph a face lacks. |

**Glyph checks (CI test, P0):**
- Onest must render pinyin tone vowels **ā á ǎ à ē é ě è ī í ǐ ì ō ó ǒ ò ū ú ǔ ù ǖ ǘ ǚ ǜ ü** without fallback.
- Russian stress marks (combining acute U+0301, e.g. *молоко́*) must sit correctly over Cyrillic vowels.
- Spanish **ñ ¿ ¡ á é í ó ú ü** must render.
- If Onest fails any of these, pinyin and stressed Russian switch to Noto Sans for that run only.

### 3.1 Type scale (sp: always follows the system font size)

Material 3 roles, with brand faces swapped in, plus three custom roles for the card.

| Role | Face / weight | Size / line | Use |
|---|---|---|---|
| `glyph.hero` *(custom)* | WenKai 400 | 88 / 100 (1–2 chars), 64 / 76 (3–4 chars), 44 / 52 (5+) | Hanzi headword on card |
| `word.hero` *(custom)* | Onest 700 | 44 / 52 → auto-shrink to 32 | ES/RU/EN headword on card |
| `reading` *(custom)* | Onest 500 | 24 / 32 | Pinyin / IPA under the headword |
| `displaySmall` | Unbounded 700 | 36 / 44 | Summary board figures, level name on level page |
| `headlineMedium` | Unbounded 600 | 28 / 36 | Screen titles on drenched screens |
| `titleLarge` | Onest 700 | 22 / 28 | Section titles, sheet titles |
| `titleMedium` | Onest 600 | 16 / 24 | List row titles, card back section labels |
| `bodyLarge` | Onest 400 | 16 / 24 | Meanings, sentence translation |
| `bodyLarge.zh` | WenKai 400 | 20 / 30 | Chinese example sentences |
| `bodyMedium` | Onest 400 | 14 / 20 | Secondary text |
| `labelLarge` | Onest 600 | 14 / 20, +0.2 tracking | Buttons |
| `labelSmall` | Unbounded 600 | 11 / 16, +0.8 tracking, caps | Station labels ("HSK 2"), card number |

Rules:
- One headword per card, never truncated. It auto-shrinks through the three steps, then wraps.
- At 200 % font scale the card scrolls internally. The rating bar never moves off-screen.

---

## 4. Shape, spacing, elevation

| Token | Value | Use |
|---|---|---|
| `space` unit | 4 dp grid; common steps 4 / 8 / 12 / 16 / 24 / 32 / 48 | Everything |
| Screen gutter | 24 dp on drenched screens, 16 dp on Operate screens | |
| `shape.card` | 24 dp corners | Collectible card (real TCG proportions) |
| `shape.art` | 18 dp corners | Art window inside the card |
| `shape.button` | Fully rounded (pill) | Rating buttons, primary actions, like station sign capsules |
| `shape.chip` | 10 dp | Grammar chips (measure word, gender, aspect) |
| `shape.sheet` | 28 dp top corners | Bottom sheets (Material default) |
| Touch targets | ≥ 48 × 48 dp, ≥ 8 dp apart | Every control |

**Elevation** uses Material tonal surfaces, with one exception: the collectible card gets a real soft shadow (`y 12 dp, blur 32 dp, 25 % of line deep`). It's a physical object sitting on the colour field. In dark theme the card also gets a **1 dp rim in the line colour at 60 %** so it never melts into the background.

---

## 5. Components

### 5.1 Line bullet
A circle in the line colour with the level number or language code in Unbounded, e.g. **①** for HSK 1 or **中** for the Mandarin line. Sizes are 20 / 28 / 40 dp. It's the app's atomic identity mark: used in lists, on cards and in the route strip.

### 5.2 Route strip
A horizontal line of stations, one per level. Visited stations are filled, the current one is larger with a pulsing ring, and future ones are hollow. The line segment fills in proportion to level mastery (mastered words ÷ level words).
- **Today:** one strip per active language.
- **Study session:** a compact strip at the bottom shows *this session's* stops (card 14 of 34) as a moving train dot.
- **Level page:** a vertical strip of word groups.
- **Accessibility:** announced as "HSK 2, 412 of 772 words mastered, current station".

### 5.3 Collectible card (the hero component)
Proportions are **63 : 88** (the real trading-card ratio). Width is the screen width minus 2 × 24 dp.

**Front:**
```
┌────────────────────────────────┐
│ ② HSK 2 · noun          ●●○    │  ← line bullet, station, part of speech, foil pips
│ ┌────────────────────────────┐ │
│ │                            │ │
│ │       [ art window ]       │ │  ← image on art-tile tint, die-cut sticker style
│ │                            │ │
│ └────────────────────────────┘ │
│                                │
│             苹果               │  ← glyph.hero / word.hero
│          (píngguǒ)             │  ← reading: hidden by default for zh
│                                │
│   ▶︎                  No. 0412  │  ← audio (48 dp), frequency rank as card number
└────────────────────────────────┘
```

**Back** (same frame, flipped):
```
┌────────────────────────────────┐
│ ② HSK 2                 ●●○    │
│ 苹果  píngguǒ           ▶︎      │  ← tone-coloured pinyin, word audio
│ ────────────────────────────── │
│ n.  apple                      │  ← meanings by part of speech
│ [量 个 gè]                     │  ← grammar chip(s)
│ ────────────────────────────── │
│ 我每天吃一个苹果。      ▶︎      │  ← sentence (WenKai) + audio
│ wǒ měitiān chī yí ge píngguǒ   │
│ I eat an apple every day.      │
│ ────────────────────────────── │
│ ┌──米──┐ ┌──米──┐   8 · 8 strokes│  ← stroke panels (one per character)
│ │  苹  │ │  果  │   ✎ Practice  │
│ └──────┘ └──────┘              │
└────────────────────────────────┘
```

**Behaviour:**
- Tap or swipe up to flip.
- When the card is idle, it tilts with the phone (±6°, using the rotation sensor). The foil and specular layers move with the tilt.
- Tilt runs **only** on the visible card. It pauses when the screen is off and turns off entirely when the system "Remove animations" setting is on.

### 5.4 Art window (unifies mixed image sources)
Every image, whatever its source, gets the same treatment:
1. An **art-tile background** in the line's tint colour (§2.1).
2. A **die-cut sticker treatment**: objects with a transparent background (Fluent Emoji 3D, generated art) get a 6 dp white outline and a soft drop shadow, made at build time by dilating the alpha mask.
3. **Photos** become rounded rectangles (`shape.art`) with a 6 dp white border, like a sticker cut from a photo.
4. **Typographic cards** (abstract words): the art window shows the headword's meaning as a large Unbounded keyword on the tint, e.g. **"BUT"** for 但是, with a small line-colour glyph.

**Generated-art style prompt (fixed for every batch):**
> *"single {subject}, glossy soft 3D icon in the style of Microsoft Fluent Emoji 3D, rounded friendly shapes, soft studio lighting, subtle gradients, centered, isolated on plain white background, no text, no border"*

This matches the generated illustrations to the free Fluent Emoji 3D set, so the two biggest image sources look like one family. Backgrounds are removed locally on CPU (free) before the sticker step.

### 5.5 Rating bar
Four pill buttons on a `neutral.surface` sheet docked to the bottom. They never sit directly on the colour field, so contrast is guaranteed.
```
 ( Again )  ( Hard )  ( Good )  ( Easy )
    1m        2d        4d        9d
```
- **Good** is filled with the line colour (the primary action). **Again** is outlined in `rate.again`. **Hard** and **Easy** are tonal.
- The next interval sits under each label in `labelSmall`.
- The buttons are disabled (40 % opacity) until the card is flipped.
- **Undo** appears as a snackbar ("Rated Good · Undo") for 5 s, and stays in the overflow menu for the whole session.

### 5.6 Stroke panel
- A 米字格 (rice-grid) square in `neutral.outline` dashes.
- Strokes draw in `ink.primary`. The active stroke is in the **line colour** with a moving brush tip, and finished strokes settle to ink.
- **Numbered stroke strip** below: cumulative frames, horizontally scrollable, current frame outlined.
- Controls: ▶︎/❚❚, ↺ loop, ‹ › step, speed 0.5× / 1× / 2×, **Hold** (write-along: pauses after each stroke until you tap), and a small ✎ **Practice** text button that opens the optional writing pad.

### 5.7 Arrival board (session summary)
Styled like a station departures/arrivals board: Unbounded figures on the line's deep colour, with rows that flip in one by one like split-flap characters.
```
 ARRIVED      HSK 2 · Persimmon line
 ─────────────────────────────────────
 Cards        34        Time   12 min
 New          10        Accuracy  88 %
 Streak       13 days   ●●● +3 foils
 ─────────────────────────────────────
 Missed: 冷 · 打算 · 突然     [ Review these ]
```

### 5.8 Other components (standard Material 3, themed)
- Top app bar (small/centre).
- Navigation bar with 4 destinations: **Today · Library · Stats · Settings**.
- Bottom sheets, snackbars, dialogs (only for destructive confirmations like restore or reset), switches, sliders (desired retention), segmented buttons (card types), search bar (library).

### 5.9 Status badges (library rows)
`New` (outline), `Learning` (tonal), `Review` (line colour), `Mastered` (holo-foil mini chip), `Suspended` (strikethrough, `ink.secondary`). Each carries a text label, never colour alone.

---

### 5.10 Right-to-left text
- The app UI stays left-to-right (it's in English). Arabic **text** (headword, sentences, letter tiles) runs right to left, aligned to the right edge, with `LocaleList("ar")` so TalkBack reads it in Arabic.
- The Arabic line badge is ع instead of letters.

### 5.11 Reading aid (PRD S17)
- **Transliteration line:** in the line colour, under or beside the headword (`reading` style, 20 sp).
- **Letter tiles:** a flow of rounded tiles, each holding the glyph (script face, 24 sp), its Latin sound (line colour) and an optional note (`labelSmall`). Tiles with a note use the art-tile tint so they stand out. RTL scripts lay tiles out right to left.
- **Tips:** bullet list under the tiles.
- **Alphabet guide:** one row per letter: big glyph (72 dp column), Latin sound + name, description, and a red ⚠ look-alike warning where the letter resembles a different Latin letter.

### 5.12 Home-screen widget (2×1)
- A rounded (20 dp) tile drenched in the word's line colour: a small white dot (the line bullet), the headword at 22 sp bold white, and "reading · meaning" at 13 sp, 85 % white, one line each.
- Built with Glance (RemoteViews), so it uses system fonts. Tapping it opens the word page.
- Empty state: "Open Yuki to start your first line".

### 5.13 Yuki, the mascot
- **Source files:**
  - Raw renders live in `design/mascot/raw/`, made with Nano Banana 2.1 on Higgsfield from the reference image, chibi style.
  - Background cut-outs, the widget busts and the icon sizes are produced by `pipeline/tools/mascot_assets.py`, locally and free.
- **Poses:** wave, celebrate, worried, sleepy. Full-body sprites are 640 px tall (WebP, ~40 KB); widget busts are 256 px.
- **Look:** cream cardigan, red-orange snowflake clip (Yuki = snow), clover necklace. Always modest and friendly.
- **On Today:** 112 dp, with the speech bubble on a `neutral.surface` card and a tail pointing at her.
- **Motion:**
  - Idle: scaleY +1.8 % over 1.6 s, reversing.
  - Mood change: spring pop from 85 % scale.
  - Celebrate: two hops of 12 % of her height.
  - Tap: one small hop.
- **Streak widget:** warm off-white ground (`#FFF4EE` / night `#231915`), flame and count in Persimmon once today is done (grey before), message in `ink.secondary`.
- **App icon:** Yuki's face on Persimmon, shoulders bleeding off the bottom edge. The 512 px store icon is in `design/logo/`.

## 6. Screens (blueprint)

| Screen | Ground | Key content |
|---|---|---|
| **Today** | `neutral.ground` | Greeting and streak flame count. One **line card** per language: route strip, "24 due · 10 new", and a big pill **Start**. A "Study all lines" button when 2+ languages are active. |
| **Study session** | **Drenched** (station colour) | Top: close ✕, station name, card count. Centre: collectible card. Bottom: rating sheet and compact route strip. |
| **Session summary** | **Drenched deep** | Arrival board, missed-words row, **Done**. Foil upgrades play one sweep each (max 3). |
| **Level page** | Drenched header, neutral list | Level hero (station name, mastery %, route). Word grid shows foil state per card, like a collection album. |
| **Library** | `neutral.ground` | Search bar, filter chips, rows: headword, reading, gloss, status badge, line bullet. |
| **Word detail** | Neutral, art tinted | Full card back, review history timeline (dots in rating colours), actions (Suspend, Mark known, Reset, Study now). |
| **Character detail** | Neutral | Large stroke panel, stroke strip, radical, readings, "words with 果". |
| **Stats** | Neutral | Year heatmap (cells in the line-colour ramp), streaks, retention, 30-day forecast bars, per-level mastery bars. |
| **Settings** | Neutral | Standard M3 preference list: limits, audio, card types, appearance, tone numbers, backup & restore, reminders. |
| **Onboarding** | Drenched, cycling line colours | 1) Pick lines (languages) as a map. 2) Pick your station (level). 3) Daily goal. Higgsfield-made hero art (one image per language). |

---

## 7. Motion & haptics

Material motion with one signature moment per surface. Everything honours **Remove animations**, falling back to a crossfade or instant change.

| Moment | Spec | Haptic |
|---|---|---|
| **Card flip** (signature) | 3D rotateY 0 → 180°, 360 ms, M3 *emphasized decelerate*. The camera distance gives real perspective. Content swaps at 90°. | `CLOCK_TICK` at 90° |
| Next card | Rated card slides **along the line**: left for Good/Easy, down into the deck for Again. Next card rises in. Shared-axis X, 300 ms. | `CONFIRM` on Good/Easy, `REJECT` on Again |
| Train dot | Moves one stop per card along the compact route strip, 250 ms standard easing | — |
| Foil upgrade | Holo sweep across the card, 600 ms, once per upgrade, max 3 per summary | `LONG_PRESS` |
| Arrival board rows | Split-flap flip-in, 40 ms stagger per row | — |
| Stroke animation | ~450 ms per stroke at 1×; brush tip eases in and out | — |
| Screen changes | Container transform from line card → session; fade-through between tabs | — |

Performance budget: every animation holds 60 fps on the target phone. The tilt and foil layers use `graphicsLayer` (GPU) and never trigger recomposition per frame.

---

## 8. Accessibility checklist (release gate)

- [ ] Every text/background pair meets AA. Line fields, neutrals and tone colours are verified in this document. New pairs get checked with the same script.
- [ ] Colour is never the only signal: tone marks (plus optional numbers), status text labels, rating labels.
- [ ] Touch targets ≥ 48 dp. The audio button is always 48 dp.
- [ ] 200 % font scale: no clipped text; the card scrolls internally; the rating bar is always reachable.
- [ ] TalkBack:
  - The card front reads "苹果, HSK 2 noun, double-tap to flip".
  - The back reads its reading, meaning and sentence in order.
  - Rating buttons announce their interval ("Good, next review in 4 days").
- [ ] Hanzi, pinyin and Russian get the correct `LocaleSpan` (`zh-CN`, `ru`, `es`, `en`) so TalkBack pronounces them in the right language.
- [ ] Remove animations: no flip rotation (crossfade instead), no tilt, no foil sweep.
- [ ] Dark theme designed and checked separately (field tone, card rim), not inverted.

---

## 9. Compose implementation notes (handed to the architecture doc)

- `YukiTheme(line: Line)` wraps `MaterialTheme`. It swaps `colorScheme.primary` and its container colours to the active line, and exposes extra tokens through `LocalYukiColors` (line ramp, tint, foil, tones, rating colours).
- `Typography` maps the roles in §3.1. The custom roles (`glyph.hero`, `word.hero`, `reading`) live in `LocalYukiType`.
- Fonts are bundled in `res/font`. The WenKai subset is generated by the content pipeline from pack characters, with `pyftsubset` (free).
- The collectible card is one composable with `face: Front | Back`, `foil: FoilTier` and `tilt: State<Offset>`. Foil is drawn with a `Brush.sweepGradient` plus `BlendMode.Overlay` in a `drawWithCache` layer.
- All tokens are defined once, in `ui/theme/Tokens.kt`. No hex literals in feature code; a lint rule enforces it.

---

## 10. References
- Drops on Google Play (Best App 2018; 4.7★, 319K reviews): https://play.google.com/store/apps/details?id=com.languagedrops.drops.international
- Drops illustration story (3,000+ illustrations, later unified in style): https://yourstory.com/2019/01/app-fridays-drops-google-play-languages
- Google Play Best of 2025 (Pokémon TCG Pocket, Best Game, for its tactile card design): https://blog.google/products/google-play/best-apps-games-2025/
- Awwwards: GoShuin (tactile, bilingual collection app): https://www.awwwards.com/sites/goshuin-every-stamp-a-memory
- Fonts (all OFL): Unbounded and Onest on Google Fonts; LXGW WenKai: https://github.com/lxgw/LxgwWenKai
- Microsoft Fluent Emoji 3D (MIT): https://github.com/microsoft/fluentui-emoji
