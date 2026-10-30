"""Yuki, the mascot: an ink-line avatar (DESIGN-SYSTEM §5.13).

Black lines, white fills and one accent colour: her scarf, snowflake hair clip and sparkles take
the current line colour at runtime, and brand maroon on neutral screens.

One geometry per pose, three outputs, so the app, the widget and the design files never drift:
  - android/core/designsystem/.../component/YukiArt.kt   Compose ImageVector parts, tinted at runtime
  - android/app/src/main/res/drawable/yuki_bust_<pose>.xml  widget / notification vectors (maroon)
  - design/mascot/yuki-<pose>.svg + design/mascot/preview.html (every pose on every line colour)

Coordinates are in a 200-unit square; the bust is cut by the bottom edge.

    python pipeline/tools/mascot_yuki.py
"""

from __future__ import annotations

import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

INK = "#14161B"     # neutral.ink
PAPER = "#FFFFFF"
BLUSH = "#F4AFA6"
TONGUE = "#E8746A"
DROP = "#BFE0F5"    # worried sweat drop
TINT = "TINT"       # replaced by the line colour (maroon in the static files)
MAROON = "#7A1F2B"  # Brand.Maroon: logo and neutral screens
LINES = {           # Tokens.kt Lines.*.field, for the preview sheet
    "zh": "#D63A22", "es": "#B42D78", "ru": "#2B4FD8", "en": "#0B7A64",
    "de": "#8C5E00", "fr": "#6A3FD1", "ar": "#4E7614", "ja": "#2E7D32",
}
LINE = 3.0          # every outline
VIEW = 200


class Part:
  """One path: a fill and/or a stroke. `alpha` applies to the fill."""

  def __init__(self, d: str, fill: str | None = None, stroke: str | None = None, width: float = 0.0, alpha: float = 1.0):
    self.d, self.fill, self.stroke, self.width, self.alpha = d, fill, stroke, width, alpha


def ellipse(cx: float, cy: float, rx: float, ry: float | None = None) -> str:
  ry = rx if ry is None else ry
  return f"M{cx - rx:g},{cy:g}a{rx:g},{ry:g} 0 1,0 {2 * rx:g},0a{rx:g},{ry:g} 0 1,0 {-2 * rx:g},0z"


def outlined(d: str, fill: str = PAPER) -> Part:
  return Part(d, fill=fill, stroke=INK, width=LINE)


def line(d: str, w: float = LINE) -> Part:
  return Part(d, stroke=INK, width=w)



def sparkle(x: float, y: float, s: float) -> Part:
  d = f"M{x:g},{y - s:g}Q{x:g},{y:g} {x + s:g},{y:g}Q{x:g},{y:g} {x:g},{y + s:g}Q{x:g},{y:g} {x - s:g},{y:g}Q{x:g},{y:g} {x:g},{y - s:g}z"
  return Part(d, fill=TINT, stroke=INK, width=2.0)


def flake(cx: float, cy: float, r: float) -> list[Part]:
  """The hair-clip snowflake: three crossing strokes in the accent colour, edged in white on the hair."""
  d = ""
  for k in range(3):
    a = math.radians(90 + 60 * k)
    dx, dy = r * math.cos(a), r * math.sin(a)
    d += f"M{cx - dx:.2f},{cy - dy:.2f}L{cx + dx:.2f},{cy + dy:.2f}"
  return [Part(d, stroke=PAPER, width=6.2), Part(d, stroke=TINT, width=3.2)]


HAIR_BACK = "M60,96C56,52 80,36 100,36C122,36 146,52 140,96C142,118 146,134 150,146C120,156 80,156 50,146C56,132 60,116 60,96z"
BANGS = "M68,90C64,58 84,50 102,50C122,50 138,62 132,92C126,78 116,70 104,68C98,80 84,86 68,90z"
NECK = "M89,118L111,118L113,146L87,146z"
CARDIGAN = "M30,204C32,168 60,150 100,150C140,150 168,168 170,204L170,206L30,206z"
SCARF = "M70,138C84,150 116,150 130,138L134,152C118,166 82,166 66,152z"
TAIL = "M106,158L122,156L128,192L110,194z"


BREAD = "#F3D9A6"   # baguette and pretzel: the one non-accent colour, because white bread looks wrong
SKY = "#BFE0F5"


# ---------- outfit helpers ----------

def _bez(p0, p1, p2, p3, t):
  return tuple((1 - t) ** 3 * a + 3 * (1 - t) ** 2 * t * b + 3 * (1 - t) * t ** 2 * c + t ** 3 * d for a, b, c, d in zip(p0, p1, p2, p3))


def body_left_x(y: float) -> float:
  """x of the top's left edge (the CARDIGAN shoulder curve) at height y; the right edge mirrors it."""
  best = min((_bez((30, 204), (32, 168), (60, 150), (100, 150), i / 400) for i in range(401)), key=lambda p: abs(p[1] - y))
  return best[0]


def band(y: float, h: float, inset: float = 2.4, fill: str = TINT) -> Part:
  """A horizontal band across the top, fitted inside its outline."""
  xl = body_left_x(y + h / 2) + inset
  return Part(f"M{xl:.1f},{y:g}L{200 - xl:.1f},{y:g}L{200 - xl:.1f},{y + h:g}L{xl:.1f},{y + h:g}z", fill=fill)


def star8(cx: float, cy: float, r: float, fill: str = PAPER) -> Part:
  """Eight-pointed star: two overlapping squares."""
  pts = []
  for k in range(16):
    rr = r if k % 2 == 0 else r * 0.62
    a = math.radians(-90 + 22.5 * k)
    pts.append((cx + rr * math.cos(a), cy + rr * math.sin(a)))
  return Part("M" + "L".join(f"{x:.1f},{y:.1f}" for x, y in pts) + "z", fill=fill)


def top(lines: bool = False) -> list[Part]:
  """The plain white top every outfit starts from; `lines` adds the cardigan's opening and buttons."""
  parts = [outlined(CARDIGAN)]
  if lines:
    parts += [line("M100,160L100,206M74,166L80,206M126,166L120,206"),
              Part(ellipse(93, 182, 1.9), fill=INK), Part(ellipse(93, 196, 1.9), fill=INK)]
  return parts


class Outfit:
  """What a language changes: the top, things behind the head (buns), things over the hair (hats,
  braids, flowers), whether the snowflake clip shows, and one prop sticker beside her shoulder."""

  def __init__(self, body, behind_head=(), over_hair=(), clip=True, prop=()):
    self.body, self.behind_head, self.over_hair, self.clip, self.prop = list(body), list(behind_head), list(over_hair), clip, list(prop)


def outfits() -> dict[str, Outfit]:
  o: dict[str, Outfit] = {}

  # Neutral: cardigan and scarf, as before.
  o["neutral"] = Outfit(top(lines=True) + [outlined(SCARF, TINT), outlined(TAIL, TINT)])

  # Mandarin: standing mandarin collar, diagonal qipao flap with knot buttons; two buns; tea cup.
  o["zh"] = Outfit(
    top() + [
      line("M100,153Q120,158 133,170L133,206"),
      outlined(ellipse(117, 160, 4.2, 2.6), TINT), outlined(ellipse(133, 180, 4.2, 2.6), TINT),
      outlined("M81,136Q100,143 119,136L121,153Q100,160 79,153z", TINT),
    ],
    behind_head=[Part(ellipse(70, 50, 13), fill=INK), Part(ellipse(130, 50, 13), fill=INK),
                 Part("M60,58Q70,64 80,58", stroke=TINT, width=3.2), Part("M120,58Q130,64 140,58", stroke=TINT, width=3.2)],
    prop=[
      outlined("M150,152L182,152Q180,174 166,176Q152,174 150,152z"),
      Part("M151,158L181,158L180,164L152,164z", fill=TINT),
      Part(ellipse(185, 160, 4, 5.5), stroke=INK, width=2.6),
      line("M144,180L188,180", 3.2),
      line("M160,144Q156,139 160,134M171,144Q175,139 171,134", 2.2),
    ],
  )

  # Japanese: yukata, collar crossed LEFT over RIGHT (the wearer's left panel on top: from the
  # viewer's right shoulder down to the left), obi sash; a bun with a kanzashi; uchiwa fan.
  obi_y = 188
  o["ja"] = Outfit(
    top() + [
      outlined("M78,149L89,149L117,190L106,194z", TINT),     # under panel (wearer's right)
      outlined("M122,149L111,149L83,190L94,194z", TINT),     # over panel (wearer's left)
      band(obi_y, 16), line(f"M{body_left_x(obi_y) + 2:.1f},{obi_y}L{200 - body_left_x(obi_y) - 2:.1f},{obi_y}", 2.6),
      Part(f"M{body_left_x(obi_y + 8) + 3:.1f},{obi_y + 8}L{200 - body_left_x(obi_y + 8) - 3:.1f},{obi_y + 8}", stroke=PAPER, width=2),
    ],
    behind_head=[Part(ellipse(100, 33, 15, 12), fill=INK)],
    over_hair=[
      line("M112,34L128,24", 2.6),
      outlined(ellipse(130, 23, 6), TINT), Part(ellipse(130, 23, 2), fill=PAPER),
      line("M127,29L125,40M133,29L136,39", 1.8), Part(ellipse(125, 41, 1.8), fill=TINT), Part(ellipse(136, 40, 1.8), fill=TINT),
    ],
    prop=[
      line("M168,160L168,186", 4.2),
      outlined(ellipse(168, 146, 15, 14), TINT),
      Part("M168,158L158,136M168,158L168,132M168,158L178,136", stroke=PAPER, width=1.6),
    ],
  )

  # Spanish: blouse in the line colour with a white ruffle across the shoulders; a bougainvillea
  # flower behind the ear (instead of the clip); abanico fan.
  # Ruffle: a white band along the neckline whose lower edge is scalloped.
  def ruffle_y(x):
    return 152 + ((x - 100) / 50) ** 2 * 14

  xs = [50 + i * 6.25 for i in range(17)]
  top_edge = "L".join(f"{x:.1f},{ruffle_y(x) - 1:.1f}" for x in xs)
  scallops = "".join(f"A3.4,3.4 0 0,1 {x:.1f},{ruffle_y(x) + 9:.1f}" for x in reversed(xs[:-1]))
  ruffle = Part(f"M{top_edge}L{xs[-1]:.1f},{ruffle_y(xs[-1]) + 9:.1f}{scallops}z", fill=PAPER, stroke=INK, width=2.2)
  o["es"] = Outfit(
    [outlined(CARDIGAN, TINT), ruffle],
    over_hair=[
      *(Part(ellipse(132 + 6 * math.cos(math.radians(a)), 74 + 6 * math.sin(math.radians(a)), 5), fill=TINT, stroke=INK, width=2)
        for a in (-90, 30, 150)),
      Part(ellipse(132, 74, 2.4), fill=PAPER),
    ],
    clip=False,
    prop=[
      outlined("M148,166A21,21 0 0,1 188,166L168,174z", TINT),
      Part("M168,174L150,160M168,174L157,149M168,174L168,145M168,174L179,149M168,174L186,160", stroke=PAPER, width=1.6),
      line("M168,174L168,184", 3.6),
    ],
  )

  # French: Breton stripes and a beret; a tote with a baguette.
  o["fr"] = Outfit(
    top() + [band(y, 5) for y in (162, 173, 184, 195)] + [line("M80,151Q100,159 120,151")],
    over_hair=[
      Part("M64,58C62,38 86,26 108,26C132,26 146,40 142,54C140,62 126,62 104,62C84,62 66,66 64,58z", fill=TINT, stroke=INK, width=LINE),
      line("M108,26L111,19", 3.2),
    ],
    prop=[
      Part("M116,151C128,158 138,166 142,170", stroke=INK, width=5.5),
      Part("M116,151C128,158 138,166 142,170", stroke=PAPER, width=1.5),
      Part("M143.1,174.9L165.1,108.9A7.5,7.5 0 0,1 179.3,113.7L157.3,179.7z", fill=BREAD, stroke=INK, width=LINE),
      line("M158.5,151L166.1,147.6M162.2,140L169.8,136.6M165.9,128.9L173.5,125.5M155,162L162.6,158.6", 2.2),
      outlined("M134,166L184,166L188,206L130,206z"),
      line("M142,166Q159,178 176,166", 2.6),
      Part(ellipse(159, 188, 7), fill=TINT, stroke=INK, width=2.2),
    ],
  )

  # German: white blouse with puff sleeves, a laced bodice in the line colour; a braided crown;
  # pretzel.
  o["de"] = Outfit(
    top() + [
      line("M48,186Q56,168 74,162M152,186Q144,168 126,162", 2.4),   # puffed-sleeve seams
      outlined("M70,206L77,168Q100,176 123,168L130,206z", TINT),
      Part("M94,176L106,182L94,188L106,194L94,200", stroke=PAPER, width=2),
      line("M80,152Q100,160 120,152"),
    ],
    over_hair=[
      # Braided crown: a chain of plaits, each edged in white so it shows on the black hair.
      *(Part(ellipse(100 + 34 * math.cos(math.radians(a)), 66 + 26 * math.sin(math.radians(a)), 5.4, 4.2),
             fill=INK, stroke=PAPER, width=1.5) for a in range(-160, -10, 14)),
    ],
    prop=[
      Part("M156,170C146,150 166,140 168,158C170,140 190,150 180,170C174,180 162,180 156,170Z", stroke=INK, width=10),
      Part("M156,170C146,150 166,140 168,158C170,140 190,150 180,170C174,180 162,180 156,170Z", stroke=BREAD, width=5),
      Part(ellipse(160, 152, 1.3), fill=PAPER), Part(ellipse(176, 152, 1.3), fill=PAPER), Part(ellipse(168, 175, 1.3), fill=PAPER),
    ],
  )

  # Russian: a platok headscarf in the line colour with small white Gzhel flowers, knotted under
  # the chin (it covers the clip); matryoshka.
  gzhel = [Part(ellipse(x, y, 2.6), fill=PAPER) for x, y in ((82, 46), (100, 40), (118, 46), (70, 64), (130, 64), (64, 84), (136, 84))]
  o["ru"] = Outfit(
    top(lines=True),
    over_hair=[
      outlined("M58,104C52,52 78,30 100,30C122,30 148,52 142,104C138,80 124,62 100,60C76,62 62,80 58,104z", TINT),
      *gzhel,
      outlined("M58,104C62,120 76,132 94,136L92,142C72,138 58,124 54,108z", TINT),
      outlined("M142,104C138,120 124,132 106,136L108,142C128,138 142,124 146,108z", TINT),
      outlined(ellipse(100, 139, 8, 6), TINT),
      outlined("M96,143L86,162L98,158z", TINT), outlined("M104,143L114,162L102,158z", TINT),
    ],
    clip=False,
    prop=[
      outlined(ellipse(168, 166, 12, 15), TINT),
      outlined(ellipse(168, 146, 9)),
      Part(ellipse(165, 146, 1.2), fill=INK), Part(ellipse(171, 146, 1.2), fill=INK),
      Part(ellipse(168, 168, 4.5), fill=PAPER),
      Part(ellipse(168, 168, 1.6), fill=TINT),
    ],
  )

  # Arabic: a shawl over the shoulders in the line colour with white eight-pointed stars; dallah.
  o["ar"] = Outfit(
    top() + [
      # A shawl over both shoulders, its two ends hanging down the front.
      outlined("M30,206C32,170 58,150 84,148L94,152C86,170 84,190 86,206z", TINT),
      outlined("M170,206C168,170 142,150 116,148L106,152C114,170 116,190 114,206z", TINT),
      star8(58, 186, 6.5), star8(142, 186, 6.5), star8(74, 163, 5), star8(126, 163, 5),
    ],
    prop=[
      Part("M176,158Q188,154 192,140", stroke=INK, width=3.4),
      outlined("M156,180Q150,166 158,154L176,154Q184,166 178,180z"),
      outlined("M158,154L176,154L167,140z", TINT),
      Part(ellipse(167, 138, 2.6), fill=INK),
      line("M152,182L182,182", 3.2),
      Part("M156,166L178,166", stroke=TINT, width=3),
    ],
  )

  # English (US): denim jacket with collar and chest pockets, a bandana in the line colour; to-go coffee.
  o["en"] = Outfit(
    top() + [
      line("M100,170L100,206"),
      outlined("M64,180L86,180L86,194L64,194z"), outlined("M114,180L136,180L136,194L114,194z"),
      line("M64,186L86,186M114,186L136,186", 2),
      outlined("M84,150L70,172L96,166z"), outlined("M116,150L130,172L104,166z"),
      outlined("M82,146Q100,152 118,146L100,172z", TINT),
      Part(ellipse(96, 154, 1.4), fill=PAPER), Part(ellipse(104, 154, 1.4), fill=PAPER), Part(ellipse(100, 162, 1.4), fill=PAPER),
    ],
    prop=[
      outlined("M156,146L180,146L176,182L160,182z"),
      outlined("M153,140L183,140L182,147L154,147z", TINT),
      Part("M157,158L179,158L178,168L158,168z", fill=TINT),
      line("M164,134Q161,130 164,125M172,134Q175,130 172,125", 2.2),
    ],
  )
  return o


def figure(outfit: Outfit, face: list[Part], back: list[Part] = (), front: list[Part] = ()) -> list[Part]:
  return [
    *back,
    Part(HAIR_BACK, fill=INK),
    *outfit.behind_head,
    outlined(NECK),
    *outfit.body,
    outlined(ellipse(69, 96, 5, 7)), outlined(ellipse(131, 96, 5, 7)),
    outlined(ellipse(100, 92, 31, 35)),
    Part(BANGS, fill=INK),
    Part(ellipse(82, 105, 6, 3.4), fill=BLUSH, alpha=0.7), Part(ellipse(118, 105, 6, 3.4), fill=BLUSH, alpha=0.7),
    line("M101,97L97,107L102,107", 2.4),
    *face,
    *outfit.over_hair,
    *(flake(125, 64, 6.5) if outfit.clip else []),
    *outfit.prop,
    *front,
  ]


DOT_EYES = [Part(ellipse(88, 95, 2.9), fill=INK), Part(ellipse(112, 95, 2.9), fill=INK)]
CALM_BROWS = [line("M82,85L94,83M106,83L118,85")]


def poses(outfit: Outfit) -> dict[str, list[Part]]:
  return {
    "wave": figure(
      outfit,
      DOT_EYES + CALM_BROWS + [line("M92,113Q100,119 108,113")],
    ),
    "celebrate": figure(
      outfit,
      CALM_BROWS + [
        line("M82,97Q88,90 94,97"), line("M106,97Q112,90 118,97"),
        Part("M90,110L110,110Q109,122 100,122Q91,122 90,110z", fill=INK, stroke=INK, width=2),
        Part(ellipse(100, 118.5, 4, 2), fill=TONGUE),
      ],
      back=[sparkle(30, 46, 8), sparkle(172, 40, 7), sparkle(46, 14, 5)],
    ),
    "worried": figure(
      outfit,
      DOT_EYES + [
        line("M80,86L93,80M107,80L120,86"),
        line("M91,114Q95.5,110 100,114Q104.5,118 109,114", 2.6),
      ],
      front=[Part("M150,62Q141,76 150,81Q159,76 150,62z", fill=DROP, stroke=INK, width=2.6)],
    ),
    "sleepy": figure(
      outfit,
      CALM_BROWS + [
        line("M82,94Q88,100 94,94"), line("M106,94Q112,100 118,94"),
        Part(ellipse(100, 113, 3, 2.5), fill=INK),
      ],
      front=[line("M150,40L162,40L150,52L162,52"), line("M170,22L178,22L170,30L178,30", 2.5)],
    ),
  }


# ---------- outputs ----------

def colour(c: str, tint: str) -> str:
  return tint if c == TINT else c


def svg(parts: list[Part], tint: str = MAROON) -> str:
  out = []
  for p in parts:
    a = [f'd="{p.d}"', f'fill="{colour(p.fill, tint)}"' if p.fill else 'fill="none"']
    if p.alpha != 1.0:
      a.append(f'fill-opacity="{p.alpha:g}"')
    if p.stroke:
      a.append(f'stroke="{colour(p.stroke, tint)}" stroke-width="{p.width:g}" stroke-linecap="round" stroke-linejoin="round"')
    out.append(f"  <path {' '.join(a)}/>")
  return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {VIEW} {VIEW}">\n' + "\n".join(out) + "\n</svg>\n"


def android_vector(parts: list[Part], pose: str) -> str:
  rows = []
  for p in parts:
    a = [f'android:pathData="{p.d}"']
    if p.fill:
      a.append(f'android:fillColor="{colour(p.fill, MAROON)}"')
      if p.alpha != 1.0:
        a.append(f'android:fillAlpha="{p.alpha:g}"')
    if p.stroke:
      a += [f'android:strokeColor="{colour(p.stroke, MAROON)}"', f'android:strokeWidth="{p.width:g}"',
            'android:strokeLineCap="round"', 'android:strokeLineJoin="round"']
    rows.append("    <path " + "\n        ".join(a) + " />")
  return f"""<?xml version="1.0" encoding="utf-8"?>
<!-- Yuki ({pose}), maroon scarf, for the widget and notifications. Generated by pipeline/tools/mascot_yuki.py. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="120dp"
    android:height="120dp"
    android:viewportWidth="{VIEW}"
    android:viewportHeight="{VIEW}">
{chr(10).join(rows)}
</vector>
"""


def kt_colour(c: str | None) -> str:
  if c is None:
    return "null"
  if c == TINT:
    return "TINT"
  return f"0xFF{c[1:].upper()}"


def kotlin(all_outfits: dict[str, dict[str, list[Part]]]) -> str:
  blocks = []
  for outfit, by_pose in all_outfits.items():
    for pose, parts in by_pose.items():
      rows = ",\n".join(
        f'    P("{p.d}", {kt_colour(p.fill)}, {kt_colour(p.stroke)}, {p.width:g}f, {p.alpha:g}f)' for p in parts
      )
      blocks.append(f'  ("{outfit}" to Mood.{pose.upper()}) to listOf(\n{rows},\n  )')
  return f"""// Generated by pipeline/tools/mascot_yuki.py. Do not edit by hand: change the script and re-run it.
package app.yuki.core.designsystem.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import app.yuki.core.model.Mood

/** Marks a colour that takes the accent colour at runtime. */
private const val TINT = 1L

/** One path of the drawing: fill and stroke are ARGB, [TINT] or null. */
private class P(val d: String, val fill: Long?, val stroke: Long?, val width: Float, val alpha: Float)

/** Keyed by (outfit, mood). Outfits: "neutral" plus one per language code. */
private val parts: Map<Pair<String, Mood>, List<P>> = mapOf(
{",\n".join(blocks)},
)

/**
 * Yuki in [mood] wearing [outfit] ("neutral" or a language code), with every accent (scarf,
 * collar, hat, prop) in [tint]. Unknown outfits fall back to neutral. {VIEW} × {VIEW} viewport.
 */
internal fun yukiVector(mood: Mood, tint: Color, outfit: String = "neutral"): ImageVector {{
  fun brush(c: Long?) = when (c) {{
    null -> null
    TINT -> SolidColor(tint)
    else -> SolidColor(Color(c))
  }}
  val b = ImageVector.Builder("yuki_${{outfit}}_${{mood.name.lowercase()}}", 120.dp, 120.dp, {VIEW}f, {VIEW}f)
  for (p in parts[outfit to mood] ?: parts.getValue("neutral" to mood)) {{
    b.addPath(
      pathData = addPathNodes(p.d),
      fill = brush(p.fill),
      fillAlpha = p.alpha,
      stroke = brush(p.stroke),
      strokeLineWidth = p.width,
      strokeLineCap = StrokeCap.Round,
      strokeLineJoin = StrokeJoin.Round,
    )
  }}
  return b.build()
}}
"""


def preview(all_outfits: dict[str, dict[str, list[Part]]]) -> str:
  """Each outfit in every pose on its own line colour, plus the same outfit on the neutral ground."""
  rows = []
  for name, by_pose in all_outfits.items():
    tint = MAROON if name == "neutral" else LINES[name]
    grounds = ["#EEF0F3"] if name == "neutral" else [tint, "#EEF0F3"]
    for ground in grounds:
      cells = "".join(f'<div class="cell" style="background:{ground}">{svg(p, tint)}</div>' for p in by_pose.values())
      rows.append(f'<div class="row"><span>{name}</span>{cells}</div>')
  head = "".join(f'<div class="cell" style="height:auto">{p}</div>' for p in next(iter(all_outfits.values())))
  return f"""<!doctype html><meta charset="utf-8"><title>Yuki mascot</title>
<style>body{{font:14px system-ui;margin:16px;background:#fff}}.row{{display:flex;gap:8px;align-items:center;margin:6px 0}}
.row span{{width:64px}}.cell{{width:140px;height:140px;border-radius:16px;display:grid;place-items:center;overflow:hidden}}.cell svg{{width:132px}}</style>
<div class="row"><span></span>{head}</div>
{''.join(rows)}
"""


def main() -> None:
  all_outfits = {name: poses(outfit) for name, outfit in outfits().items()}
  kt = ROOT / "android/core/designsystem/src/main/kotlin/app/yuki/core/designsystem/component/YukiArt.kt"
  kt.write_text(kotlin(all_outfits), encoding="utf-8")
  drawable = ROOT / "android/app/src/main/res/drawable"
  design = ROOT / "design/mascot"
  design.mkdir(parents=True, exist_ok=True)
  # The widget and notifications always show the neutral outfit.
  for pose, parts in all_outfits["neutral"].items():
    (drawable / f"yuki_bust_{pose}.xml").write_text(android_vector(parts, pose), encoding="utf-8")
    (design / f"yuki-{pose}.svg").write_text(svg(parts), encoding="utf-8")
  (design / "preview.html").write_text(preview(all_outfits), encoding="utf-8")
  print(f"wrote YukiArt.kt ({len(all_outfits)} outfits), 4 widget vectors, 4 svgs and design/mascot/preview.html")


if __name__ == "__main__":
  main()
