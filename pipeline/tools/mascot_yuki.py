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
    "de": "#8C5E00", "fr": "#6A3FD1", "ar": "#4E7614",
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


def figure(face: list[Part], back: list[Part] = (), front: list[Part] = ()) -> list[Part]:
  return [
    *back,
    Part(HAIR_BACK, fill=INK),
    outlined(NECK),
    outlined(CARDIGAN),
    line("M100,160L100,206M74,166L80,206M126,166L120,206"),
    Part(ellipse(93, 182, 1.9), fill=INK), Part(ellipse(93, 196, 1.9), fill=INK),
    outlined(ellipse(69, 96, 5, 7)), outlined(ellipse(131, 96, 5, 7)),
    outlined(ellipse(100, 92, 31, 35)),
    Part(BANGS, fill=INK),
    Part(ellipse(82, 105, 6, 3.4), fill=BLUSH, alpha=0.7), Part(ellipse(118, 105, 6, 3.4), fill=BLUSH, alpha=0.7),
    line("M101,97L97,107L102,107", 2.4),
    *face,
    outlined(SCARF, TINT),
    outlined(TAIL, TINT),
    *flake(125, 64, 6.5),
    *front,
  ]


DOT_EYES = [Part(ellipse(88, 95, 2.9), fill=INK), Part(ellipse(112, 95, 2.9), fill=INK)]
CALM_BROWS = [line("M82,85L94,83M106,83L118,85")]


def poses() -> dict[str, list[Part]]:
  return {
    "wave": figure(
      DOT_EYES + CALM_BROWS + [line("M92,113Q100,119 108,113")],
    ),
    "celebrate": figure(
      CALM_BROWS + [
        line("M82,97Q88,90 94,97"), line("M106,97Q112,90 118,97"),
        Part("M90,110L110,110Q109,122 100,122Q91,122 90,110z", fill=INK, stroke=INK, width=2),
        Part(ellipse(100, 118.5, 4, 2), fill=TONGUE),
      ],
      back=[sparkle(34, 50, 8), sparkle(168, 44, 7), sparkle(100, 14, 6)],
    ),
    "worried": figure(
      DOT_EYES + [
        line("M80,86L93,80M107,80L120,86"),
        line("M91,114Q95.5,110 100,114Q104.5,118 109,114", 2.6),
      ],
      front=[Part("M147,56Q138,70 147,75Q156,70 147,56z", fill=DROP, stroke=INK, width=2.6)],
    ),
    "sleepy": figure(
      CALM_BROWS + [
        line("M82,94Q88,100 94,94"), line("M106,94Q112,100 118,94"),
        Part(ellipse(100, 113, 3, 2.5), fill=INK),
      ],
      front=[line("M146,36L158,36L146,48L158,48"), line("M166,18L174,18L166,26L174,26", 2.5)],
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


def kotlin(all_poses: dict[str, list[Part]]) -> str:
  blocks = []
  for pose, parts in all_poses.items():
    rows = ",\n".join(
      f'    P("{p.d}", {kt_colour(p.fill)}, {kt_colour(p.stroke)}, {p.width:g}f, {p.alpha:g}f)' for p in parts
    )
    blocks.append(f"  Mood.{pose.upper()} to listOf(\n{rows},\n  )")
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

private val parts: Map<Mood, List<P>> = mapOf(
{",\n".join(blocks)},
)

/** Yuki in [mood], with her scarf, hair-clip flake and sparkles in [tint]. {VIEW} × {VIEW} viewport. */
internal fun yukiVector(mood: Mood, tint: Color): ImageVector {{
  fun brush(c: Long?) = when (c) {{
    null -> null
    TINT -> SolidColor(tint)
    else -> SolidColor(Color(c))
  }}
  val b = ImageVector.Builder("yuki_${{mood.name.lowercase()}}", 120.dp, 120.dp, {VIEW}f, {VIEW}f)
  for (p in parts.getValue(mood)) {{
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


def preview(all_poses: dict[str, list[Part]]) -> str:
  rows = []
  grounds = [("neutral", "#EEF0F3", MAROON)] + [(k, v, v) for k, v in LINES.items()]
  for name, ground, tint in grounds:
    cells = "".join(f'<div class="cell" style="background:{ground}">{svg(p, tint)}</div>' for p in all_poses.values())
    rows.append(f'<div class="row"><span>{name}</span>{cells}</div>')
  head = "".join(f'<div class="cell" style="height:auto">{p}</div>' for p in all_poses)
  return f"""<!doctype html><meta charset="utf-8"><title>Yuki mascot</title>
<style>body{{font:14px system-ui;margin:16px;background:#fff}}.row{{display:flex;gap:8px;align-items:center;margin:6px 0}}
.row span{{width:64px}}.cell{{width:140px;height:140px;border-radius:16px;display:grid;place-items:center;overflow:hidden}}.cell svg{{width:132px}}</style>
<div class="row"><span></span>{head}</div>
{''.join(rows)}
"""


def main() -> None:
  all_poses = poses()
  kt = ROOT / "android/core/designsystem/src/main/kotlin/app/yuki/core/designsystem/component/YukiArt.kt"
  kt.write_text(kotlin(all_poses), encoding="utf-8")
  drawable = ROOT / "android/app/src/main/res/drawable"
  design = ROOT / "design/mascot"
  design.mkdir(parents=True, exist_ok=True)
  for pose, parts in all_poses.items():
    (drawable / f"yuki_bust_{pose}.xml").write_text(android_vector(parts, pose), encoding="utf-8")
    (design / f"yuki-{pose}.svg").write_text(svg(parts), encoding="utf-8")
  (design / "preview.html").write_text(preview(all_poses), encoding="utf-8")
  print("wrote YukiArt.kt, 4 widget vectors, 4 svgs and design/mascot/preview.html")


if __name__ == "__main__":
  main()
