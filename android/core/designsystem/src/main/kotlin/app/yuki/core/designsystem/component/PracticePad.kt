package app.yuki.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.yuki.core.designsystem.theme.Yuki
import app.yuki.core.model.Hanzi
import kotlin.math.hypot
import kotlinx.coroutines.delay

/**
 * Stroke checking for the practice pad, in Make Me a Hanzi glyph units (1024 box, y up).
 * A drawn stroke matches when, resampled, it runs close to the expected centre line in the
 * right direction. Lenient on purpose: it checks order and direction, not handwriting.
 */
object StrokeMatcher {
  private const val SAMPLES = 16
  const val MAX_MEAN_DISTANCE = 190f
  private const val MIN_LENGTH_SHARE = 0.35f

  fun matches(drawn: List<Pair<Float, Float>>, median: List<Pair<Float, Float>>): Boolean {
    if (drawn.size < 2 || median.size < 2) return false
    val medianLength = length(median)
    if (length(drawn) < medianLength * MIN_LENGTH_SHARE && length(drawn) < 120f) return false
    val a = resample(drawn)
    val b = resample(median)
    val forward = a.indices.map { dist(a[it], b[it]) }.average()
    val backward = a.indices.map { dist(a[it], b[SAMPLES - 1 - it]) }.average()
    return forward <= MAX_MEAN_DISTANCE && forward <= backward
  }

  private fun dist(p: Pair<Float, Float>, q: Pair<Float, Float>) = hypot(p.first - q.first, p.second - q.second)

  private fun length(pts: List<Pair<Float, Float>>) = pts.zipWithNext().sumOf { (p, q) -> dist(p, q).toDouble() }.toFloat()

  fun resample(pts: List<Pair<Float, Float>>, n: Int = SAMPLES): List<Pair<Float, Float>> {
    val total = length(pts)
    if (total == 0f) return List(n) { pts.first() }
    val step = total / (n - 1)
    val out = ArrayList<Pair<Float, Float>>(n)
    out.add(pts.first())
    var acc = 0f
    var prev = pts.first()
    var i = 1
    while (i < pts.size && out.size < n) {
      val cur = pts[i]
      val d = dist(prev, cur)
      if (acc + d >= step && d > 0f) {
        val t = (step - acc) / d
        val q = (prev.first + t * (cur.first - prev.first)) to (prev.second + t * (cur.second - prev.second))
        out.add(q)
        prev = q
        acc = 0f
      } else {
        acc += d
        prev = cur
        i++
      }
    }
    while (out.size < n) out.add(pts.last())
    return out
  }
}

/**
 * Optional on-phone writing practice (PRD S5): trace the character stroke by stroke over a
 * faint outline. Each stroke is checked for order and direction; after two misses the next
 * stroke flashes as a hint. Results never affect scheduling.
 */
@Composable
fun PracticePad(hanzi: Hanzi, modifier: Modifier = Modifier) {
  val geometry = remember(hanzi.ch) { StrokeGeometry(hanzi) }
  var done by remember(hanzi.ch) { mutableIntStateOf(0) }
  var misses by remember(hanzi.ch) { mutableIntStateOf(0) }
  var outline by remember { mutableStateOf(true) }
  var hint by remember(hanzi.ch) { mutableStateOf(false) }
  var flash by remember(hanzi.ch) { mutableIntStateOf(0) }
  val drawing = remember(hanzi.ch) { mutableStateListOf<Offset>() }
  val c = Yuki.colors
  val finished = done >= geometry.count

  LaunchedEffect(flash) {
    if (flash == 0) return@LaunchedEffect
    hint = true
    delay(900)
    hint = false
  }

  Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(
      when {
        finished -> "Well done: all ${geometry.count} strokes in order."
        misses > 0 -> "Stroke ${done + 1} of ${geometry.count}. Not quite: check the direction and where it starts."
        else -> "Stroke ${done + 1} of ${geometry.count}."
      },
      style = MaterialTheme.typography.bodyMedium,
      color = if (misses > 0 && !finished) c.again else c.neutral.ink2,
    )
    Canvas(
      Modifier
        .fillMaxWidth()
        .aspectRatio(1f)
        .border(1.5.dp, c.neutral.outline, RoundedCornerShape(8.dp))
        .semantics { contentDescription = "Writing pad for ${hanzi.ch}. Draw stroke ${(done + 1).coerceAtMost(geometry.count)} of ${geometry.count}" }
        .pointerInput(hanzi.ch, finished) {
          if (finished) return@pointerInput
          // Raw pointer events (no drag slop), so the stroke keeps its true starting point.
          awaitEachGesture {
            val down = awaitFirstDown()
            down.consume()
            drawing.clear()
            drawing.add(down.position)
            while (true) {
              val event = awaitPointerEvent()
              val change = event.changes.firstOrNull { it.id == down.id } ?: break
              change.consume()
              drawing.add(change.position)
              if (!change.pressed) break
            }
            val s = size.width.coerceAtMost(size.height) / 1024f
            val ox = (size.width - 1024f * s) / 2f
            val oy = (size.height - 1024f * s) / 2f
            val glyph = drawing.map { p -> ((p.x - ox) / s) to (900f - (p.y - oy) / s) }
            if (StrokeMatcher.matches(glyph, hanzi.medians[done])) {
              done++
              misses = 0
            } else if (glyph.size > 2) {
              misses++
              if (misses >= 2) flash++
            }
            drawing.clear()
          }
        },
    ) {
      val dash = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
      drawLine(c.neutral.outline, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 1.5f, pathEffect = dash)
      drawLine(c.neutral.outline, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1.5f, pathEffect = dash)
      drawLine(c.neutral.outline, Offset(0f, 0f), Offset(size.width, size.height), 1f, pathEffect = dash)
      drawLine(c.neutral.outline, Offset(size.width, 0f), Offset(0f, size.height), 1f, pathEffect = dash)
      val s = size.minDimension / 1024f
      withTransform({
        translate((size.width - 1024f * s) / 2f, (size.height - 1024f * s) / 2f)
        scale(s, -s, pivot = Offset.Zero)
        translate(0f, -900f)
      }) {
        geometry.outlines.forEachIndexed { i, path ->
          when {
            i < done -> drawPath(path, c.neutral.ink)
            i == done && hint -> drawPath(path, c.line.field.copy(alpha = 0.75f))
            outline -> drawPath(path, c.neutral.outline.copy(alpha = 0.45f))
          }
        }
      }
      if (drawing.size > 1) {
        val p = Path().apply {
          moveTo(drawing[0].x, drawing[0].y)
          for (k in 1 until drawing.size) lineTo(drawing[k].x, drawing[k].y)
        }
        drawPath(p, c.line.field, style = Stroke(width = 14.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
      }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      FilterChip(selected = outline, onClick = { outline = !outline }, label = { Text("Outline") })
      TextButton(onClick = { if (!finished) flash++ }, enabled = !finished) { Text("Hint") }
      TextButton(onClick = { done = 0; misses = 0; drawing.clear() }) { Text("Start over") }
    }
  }
}

/**
 * "How to write" sheet body: the stroke-order viewer for each character, with "✎ Practice"
 * swapping one character's viewer for the [PracticePad]. [onPracticingChange] lets the host
 * sheet stop dragging while you draw.
 */
@Composable
fun HowToWrite(characters: List<Hanzi>, onPracticingChange: (Boolean) -> Unit = {}) {
  var practicing by remember(characters) { mutableStateOf<Hanzi?>(null) }
  LaunchedEffect(practicing) { onPracticingChange(practicing != null) }
  val c = Yuki.colors
  Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
    Text(if (practicing != null) "Practise ${practicing!!.ch}" else "How to write", style = MaterialTheme.typography.titleLarge, color = c.neutral.ink)
    val p = practicing
    if (p != null) {
      PracticePad(p)
      TextButton(onClick = { practicing = null }) { Text("‹ Back to stroke order") }
    } else {
      characters.forEach { h -> StrokeOrderPanel(h, onPractice = { practicing = h }) }
    }
  }
}