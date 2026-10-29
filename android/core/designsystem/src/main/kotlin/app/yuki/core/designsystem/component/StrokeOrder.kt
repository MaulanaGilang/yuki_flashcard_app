package app.yuki.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.yuki.core.designsystem.theme.Yuki
import app.yuki.core.designsystem.theme.YukiType
import app.yuki.core.model.Hanzi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Parsed stroke outlines and centre lines, built once per character. */
class StrokeGeometry(hanzi: Hanzi) {
  val outlines: List<Path> = hanzi.strokes.map { PathParser().parsePathString(it).toPath() }
  val medians: List<Path> = hanzi.medians.map { pts ->
    Path().apply {
      pts.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x, y) else lineTo(x, y) }
    }
  }
  val lengths: List<Float> = medians.map { PathMeasure().apply { setPath(it, false) }.length }
  val count get() = outlines.size
}

/** Draws a character's strokes: [progress] = completed strokes + fraction of the active one. */
private fun DrawScope.drawStrokes(g: StrokeGeometry, progress: Float, ink: Color, active: Color, ghost: Color, highlight: Int = -1) {
  val s = size.minDimension / 1024f
  withTransform({
    translate((size.width - 1024f * s) / 2f, (size.height - 1024f * s) / 2f)
    scale(s, -s, pivot = Offset.Zero)
    translate(0f, -900f)
  }) {
    val done = progress.toInt()
    val frac = progress - done
    g.outlines.forEachIndexed { i, outline ->
      when {
        i == highlight -> drawPath(outline, active)
        i < done -> drawPath(outline, ink)
        i == done && frac > 0f -> {
          drawPath(outline, ghost)
          val seg = Path()
          PathMeasure().apply { setPath(g.medians[i], false) }.getSegment(0f, g.lengths[i] * frac, seg, true)
          clipPath(outline) {
            drawPath(seg, active, style = Stroke(width = 150f, cap = StrokeCap.Round, join = StrokeJoin.Round))
          }
        }
        else -> drawPath(outline, ghost)
      }
    }
  }
}

/** 米字格: the dashed guide grid on Chinese practice paper. */
private fun DrawScope.drawRiceGrid(color: Color) {
  val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
  val w = size.width
  val h = size.height
  drawLine(color, Offset(w / 2, 0f), Offset(w / 2, h), 1.5f, pathEffect = dash)
  drawLine(color, Offset(0f, h / 2), Offset(w, h / 2), 1.5f, pathEffect = dash)
  drawLine(color, Offset(0f, 0f), Offset(w, h), 1f, pathEffect = dash)
  drawLine(color, Offset(w, 0f), Offset(0f, h), 1f, pathEffect = dash)
}

/** Static or animated character on a rice grid. */
@Composable
fun StrokeCanvas(geometry: StrokeGeometry, progress: Float, modifier: Modifier = Modifier, highlight: Int = -1) {
  val c = Yuki.colors
  Canvas(
    modifier
      .aspectRatio(1f)
      .border(1.5.dp, c.neutral.outline, RoundedCornerShape(6.dp)),
  ) {
    drawRiceGrid(c.neutral.outline)
    drawStrokes(geometry, progress, c.neutral.ink, c.line.field, c.neutral.outline.copy(alpha = 0.55f), highlight)
  }
}

/**
 * Stroke-order viewer built for copying onto paper (PRD S4): animation with play, loop, step,
 * speed and a write-along "Hold" mode that waits for a tap after each stroke, plus the
 * numbered stroke strip.
 */
@Composable
fun StrokeOrderPanel(hanzi: Hanzi, modifier: Modifier = Modifier, onPractice: (() -> Unit)? = null) {
  val geometry = remember(hanzi.ch) { StrokeGeometry(hanzi) }
  val progress = remember(hanzi.ch) { Animatable(geometry.count.toFloat()) }
  val scope = rememberCoroutineScope()
  var playing by remember(hanzi.ch) { mutableStateOf(false) }
  var loop by remember { mutableStateOf(false) }
  var hold by remember { mutableStateOf(false) }
  var speed by remember { mutableStateOf(1f) }
  var waitingTap by remember { mutableStateOf(false) }
  var runId by remember { mutableIntStateOf(0) }
  val reduceMotion = rememberReduceMotion()

  LaunchedEffect(playing, runId) {
    if (!playing) return@LaunchedEffect
    do {
      if (progress.value >= geometry.count) progress.snapTo(0f)
      var i = progress.value.toInt()
      while (i < geometry.count) {
        if (reduceMotion) progress.snapTo((i + 1).toFloat())
        else progress.animateTo((i + 1).toFloat(), tween(((450 / speed) * (0.6f + geometry.lengths[i] / 900f)).toInt().coerceAtLeast(150), easing = LinearEasing))
        i++
        if (hold && i < geometry.count) {
          waitingTap = true
          while (waitingTap) delay(50)
        } else {
          delay((120 / speed).toLong())
        }
      }
      if (loop) delay(800)
    } while (loop && playing)
    playing = false
  }

  val c = Yuki.colors
  Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Box(
        Modifier
          .size(132.dp)
          .semantics { contentDescription = "Stroke order for ${hanzi.ch}, ${hanzi.strokeCount} strokes" },
      ) {
        StrokeCanvas(geometry, progress.value, Modifier.fillMaxWidth())
        if (waitingTap) {
          TextButton(onClick = { waitingTap = false }, modifier = Modifier.align(Alignment.BottomCenter)) { Text("Next stroke") }
        }
      }
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${hanzi.strokeCount} strokes" + (hanzi.radical?.let { " · radical $it" } ?: ""), style = MaterialTheme.typography.bodyMedium, color = c.neutral.ink2)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          TextButton(onClick = { if (playing) playing = false else { playing = true; runId++ } }) { Text(if (playing) "Pause" else "Play") }
          TextButton(onClick = {
            playing = false
            scope.launch { progress.snapTo((progress.value.toInt() - 1).coerceAtLeast(0).toFloat()) }
          }) { Text("‹") }
          TextButton(onClick = {
            playing = false
            scope.launch { progress.snapTo((progress.value.toInt() + 1).coerceAtMost(geometry.count).toFloat()) }
          }) { Text("›") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          FilterChip(selected = loop, onClick = { loop = !loop }, label = { Text("Loop") })
          FilterChip(selected = hold, onClick = { hold = !hold }, label = { Text("Hold") })
          FilterChip(selected = speed != 1f, onClick = { speed = when (speed) { 1f -> 0.5f; 0.5f -> 2f; else -> 1f } }, label = { Text("${speed}×".replace(".0", "")) })
        }
        if (onPractice != null) TextButton(onClick = onPractice) { Text("✎ Practice") }
      }
    }
    StrokeStrip(geometry, current = progress.value.toInt() - 1)
  }
}

/** Numbered cumulative frames: stroke 1, strokes 1–2, … (DESIGN-SYSTEM §5.6). */
@Composable
fun StrokeStrip(geometry: StrokeGeometry, current: Int, modifier: Modifier = Modifier) {
  val c = Yuki.colors
  Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    for (k in 0 until geometry.count) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        StrokeCanvas(
          geometry,
          progress = k.toFloat(),
          highlight = k,
          modifier = Modifier
            .size(44.dp)
            .background(if (k == current) c.artTint else Color.Transparent, RoundedCornerShape(6.dp)),
        )
        Text("${k + 1}", style = YukiType.station, color = if (k == current) c.line.field else c.neutral.ink2, modifier = Modifier.padding(top = 2.dp))
      }
    }
  }
}
