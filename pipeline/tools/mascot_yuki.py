"""Yuki, the mascot: a flat snow spirit drawn like a transit pictogram (DESIGN-SYSTEM §5.13).

One geometry per pose, three outputs, so the app, the widget and the design files never drift:
  - android/core/designsystem/.../component/YukiArt.kt   Compose ImageVector parts; the scarf, the
                                                         hair-clip flake and the sparkles take the
                                                         current language's line colour at runtime
  - android/app/src/main/res/drawable/yuki_bust_<pose>.xml  widget / notification vectors (Persimmon)
  - design/mascot/yuki-<pose>.svg + design/mascot/preview.html (every pose on every line colour)

Coordinates are in a 120-unit square; Yuki stands on the bottom edge. Every outline is the same
ink stroke, like signage pictograms.

    python pipeline/tools/mascot_snow.py
"""

from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

INK = "#14161B"     # neutral.ink
SNOW = "#FFF8F3"    # warm white, same as the card paper and the logo flake
BLUSH = "#F4AFA6"
TONGUE = "#E8746A"
DROP = "#BFE0F5"    # worried sweat drop
TINT = "TINT"       # replaced by the line colour (Persimmon in the static files)
PERSIMMON = "#D63A22"
LINES = {           # Tokens.kt Lines.*.field, for the preview sheet
    "zh": "#D63A22", "es": "#B42D78", "ru": "#2B4FD8", "en": "#0B7A64",
    "de": "#8C5E00", "fr": "#6A3FD1", "ar": "#4E7614",
}
OUTLINE = 3.2
POSES = ("wave", "celebrate", "worried", "sleepy")


class Part:
  """One path: a fill and/or a stroke. `alpha` applies to the fill."""

  def __init__(self, d: str, fill: str | None = None, stroke: str | None = None, width: float = 0.0, alpha: float = 1.0):
    self.d, self.fill, self.stroke, self.width, self.alpha = d, fill, stroke, width, alpha


def ellipse(cx: float, cy: float, rx: float, ry: float | None = None) -> str:
  ry = rx if ry is None else ry
  return f"M{cx - rx:g},{cy:g}a{rx:g},{ry:g} 0 1,0 {2 * rx:g},0a{rx:g},{ry:g} 0 1,0 {-2 * rx:g},0z"


def arm(x1: float, y1: float, x2: float, y2: float) -> list[Part]:
  """An outlined capsule: a wide ink stroke under a narrower snow stroke."""
  d = f"M{x1:g},{y1:g}L{x2:g},{y2:g}"
  return [Part(d, stroke=INK, width=11.0), Part(d, stroke=SNOW, width=11.0 - 2 * OUTLINE)]


def sparkle(x: float, y: float, s: float) -> Part:
  d = f"M{x:g},{y - s:g}Q{x:g},{y:g} {x + s:g},{y:g}Q{x:g},{y:g} {x:g},{y + s:g}Q{x:g},{y:g} {x - s:g},{y:g}Q{x:g},{y:g} {x:g},{y - s:g}z"
  return Part(d, fill=TINT, stroke=INK, width=1.6)


def flake(cx: float, cy: float, r: float) -> list[Part]:
  """The hair-clip snowflake: three crossing strokes in the line colour on an ink outline."""
  import math
  d = ""
  for k in range(3):
    a = math.radians(90 + 60 * k)
    dx, dy = r * math.cos(a), r * math.sin(a)
    d += f"M{cx - dx:.2f},{cy - dy:.2f}L{cx + dx:.2f},{cy + dy:.2f}"
  return [Part(d, stroke=INK, width=5.6), Part(d, stroke=TINT, width=2.6)]


BODY = "M60,24C84,24 98,46 98,72C98,94 86,106 60,106C34,106 22,94 22,72C22,46 36,24 60,24z"
SCARF = "M19,79C40,86 80,86 101,79L101,90C80,97 40,97 19,90z"
TAIL = "M72,90L85,90L88,106Q80,109 73,106z"


def figure(arms: list[Part], face: list[Part], extras_front: list[Part] = (), extras_back: list[Part] = ()) -> list[Part]:
  return [
    Part(ellipse(60, 111, 27, 4), fill=INK, alpha=0.12),   # ground shadow
    *extras_back,
    *arms,                                                 # roots hide under the body
    Part(BODY, fill=SNOW, stroke=INK, width=OUTLINE),
    *flake(45, 36, 5.5),
    *face,
    Part(ellipse(42, 66, 5, 3), fill=BLUSH),
    Part(ellipse(78, 66, 5, 3), fill=BLUSH),
    Part(SCARF, fill=TINT, stroke=INK, width=OUTLINE),
    Part(TAIL, fill=TINT, stroke=INK, width=OUTLINE),
    *extras_front,
  ]


def open_eyes(y: float = 57, r: float = 3.6) -> list[Part]:
  return [
    Part(ellipse(48, y, r), fill=INK), Part(ellipse(72, y, r), fill=INK),
    Part(ellipse(49.2, y - 1.3, 1.1), fill=SNOW), Part(ellipse(73.2, y - 1.3, 1.1), fill=SNOW),
  ]


def stroke(d: str, w: float = 2.8) -> Part:
  return Part(d, stroke=INK, width=w)


def poses() -> dict[str, list[Part]]:
  return {
    "wave": figure(
      arm(88, 70, 104, 50) + arm(32, 72, 17, 84),
      open_eyes() + [stroke("M54,64Q60,70 66,64")],
    ),
    "celebrate": figure(
      arm(88, 68, 106, 48) + arm(32, 68, 14, 48),
      [
        stroke("M43,59Q48,52 53,59", 3.0), stroke("M67,59Q72,52 77,59", 3.0),
        Part("M52,63L68,63Q67,73 60,73Q53,73 52,63z", fill=INK, stroke=INK, width=1.4),
        Part(ellipse(60, 70.4, 3.4, 1.8), fill=TONGUE),
      ],
      extras_back=[sparkle(16, 28, 6), sparkle(104, 26, 5), sparkle(60, 9, 4.5)],
    ),
    "worried": figure(
      arm(32, 74, 22, 92) + arm(88, 74, 98, 92),
      open_eyes(58, 3.8) + [
        stroke("M41,50L52,46"), stroke("M68,46L79,50"),
        stroke("M53,67Q56.5,64 60,67Q63.5,70 67,67"),
      ],
      extras_front=[Part("M95,30Q88,41 95,45Q102,41 95,30z", fill=DROP, stroke=INK, width=2.0)],
    ),
    "sleepy": figure(
      arm(32, 76, 21, 94) + arm(88, 76, 99, 94),
      [
        stroke("M43,57Q48,62 53,57", 3.0), stroke("M67,57Q72,62 77,57", 3.0),
        Part(ellipse(60, 67, 2.6, 2.0), fill=INK),
      ],
      extras_back=[
        stroke("M86,24L96,24L86,34L96,34", 2.8),
        stroke("M101,8L107,8L101,14L107,14", 2.2),
      ],
    ),
  }


# ---------- outputs ----------

def colour(c: str, tint: str) -> str:
  return tint if c == TINT else c


def svg(parts: list[Part], tint: str = PERSIMMON) -> str:
  out = []
  for p in parts:
    a = [f'd="{p.d}"']
    a.append(f'fill="{colour(p.fill, tint)}"' if p.fill else 'fill="none"')
    if p.alpha != 1.0:
      a.append(f'fill-opacity="{p.alpha:g}"')
    if p.stroke:
      a.append(f'stroke="{colour(p.stroke, tint)}" stroke-width="{p.width:g}" stroke-linecap="round" stroke-linejoin="round"')
    out.append(f"  <path {' '.join(a)}/>")
  return '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 120 120">\n' + "\n".join(out) + "\n</svg>\n"


def android_vector(parts: list[Part], pose: str) -> str:
  rows = []
  for p in parts:
    a = [f'android:pathData="{p.d}"']
    if p.fill:
      a.append(f'android:fillColor="{colour(p.fill, PERSIMMON)}"')
      if p.alpha != 1.0:
        a.append(f'android:fillAlpha="{p.alpha:g}"')
    if p.stroke:
      a += [f'android:strokeColor="{colour(p.stroke, PERSIMMON)}"', f'android:strokeWidth="{p.width:g}"',
            'android:strokeLineCap="round"', 'android:strokeLineJoin="round"']
    rows.append("    <path " + "\n        ".join(a) + " />")
  return f"""<?xml version="1.0" encoding="utf-8"?>
<!-- Yuki ({pose}), Persimmon scarf, for the widget and notifications. Generated by pipeline/tools/mascot_snow.py. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="120dp"
    android:height="120dp"
    android:viewportWidth="120"
    android:viewportHeight="120">
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
  return f"""// Generated by pipeline/tools/mascot_snow.py. Do not edit by hand: change the script and re-run it.
package app.yuki.core.designsystem.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import app.yuki.core.model.Mood

/** Marks a colour that takes the line colour at runtime. */
private const val TINT = 1L

/** One path of the drawing: fill and stroke are ARGB, [TINT] or null. */
private class P(val d: String, val fill: Long?, val stroke: Long?, val width: Float, val alpha: Float)

private val parts: Map<Mood, List<P>> = mapOf(
{",\n".join(blocks)},
)

/** Yuki in [mood], with her scarf, hair-clip flake and sparkles in [tint]. 120 × 120 viewport. */
internal fun yukiVector(mood: Mood, tint: Color): ImageVector {{
  fun brush(c: Long?) = when (c) {{
    null -> null
    TINT -> SolidColor(tint)
    else -> SolidColor(Color(c))
  }}
  val b = ImageVector.Builder("yuki_${{mood.name.lowercase()}}", 120.dp, 120.dp, 120f, 120f)
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
  cells = []
  grounds = [("neutral", "#EEF0F3", PERSIMMON)] + [(k, v, v) for k, v in LINES.items()]
  for name, ground, tint in grounds:
    row = "".join(f'<div class="cell" style="background:{ground}">{svg(p, tint)}</div>' for p in all_poses.values())
    cells.append(f'<div class="row"><span>{name}</span>{row}</div>')
  return f"""<!doctype html><meta charset="utf-8"><title>Yuki mascot</title>
<style>body{{font:14px system-ui;margin:16px;background:#fff}}.row{{display:flex;gap:8px;align-items:center;margin:6px 0}}
.row span{{width:64px}}.cell{{width:140px;height:140px;border-radius:16px;display:grid;place-items:center}}.cell svg{{width:120px}}</style>
<div class="row"><span></span>{''.join(f'<div class="cell" style="height:auto">{p}</div>' for p in POSES)}</div>
{''.join(cells)}
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
