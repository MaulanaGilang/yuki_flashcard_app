package app.yuki.core.designsystem.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yuki.core.designsystem.theme.YukiType
import app.yuki.core.designsystem.theme.Unbounded
import app.yuki.core.designsystem.theme.Yuki

/** Metro-style line bullet: the app's atomic identity mark (DESIGN-SYSTEM §5.1). */
@Composable
fun LineBullet(label: String, color: Color, modifier: Modifier = Modifier, size: Dp = 28.dp, onColor: Color = Color.White) {
  Box(modifier.size(size).background(color, CircleShape), contentAlignment = Alignment.Center) {
    Text(
      label,
      color = onColor,
      fontFamily = Unbounded,
      fontWeight = FontWeight.Bold,
      fontSize = (size.value * 0.4f).sp,
      textAlign = TextAlign.Center,
      maxLines = 1,
    )
  }
}

/** One station on a route strip. */
data class Station(val label: String, val progress: Float)

/**
 * A line of stations (DESIGN-SYSTEM §5.2). Visited stations are filled, the current one is
 * larger with a halo, future ones hollow. Segments fill with level mastery.
 */
@Composable
fun RouteStrip(
  stations: List<Station>,
  currentIndex: Int,
  color: Color,
  modifier: Modifier = Modifier,
  contentDescription: String,
) {
  val track = Yuki.colors.neutral.outline
  val ground = Yuki.colors.neutral.surface2
  Column(modifier.fillMaxWidth().semantics { this.contentDescription = contentDescription }) {
    Canvas(Modifier.fillMaxWidth().height(28.dp)) {
      if (stations.isEmpty()) return@Canvas
      val y = size.height / 2
      val r = 7.dp.toPx()
      val step = if (stations.size == 1) 0f else (size.width - 2 * 12.dp.toPx()) / (stations.size - 1)
      val x0 = 12.dp.toPx()
      for (i in 0 until stations.size - 1) {
        val a = x0 + i * step
        val b = a + step
        drawLine(track, Offset(a, y), Offset(b, y), strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
        val fill = stations[i].progress.coerceIn(0f, 1f)
        if (i < currentIndex || fill > 0f) {
          val end = if (i < currentIndex) b else a + (b - a) * fill
          drawLine(color, Offset(a, y), Offset(end, y), strokeWidth = 5.dp.toPx(), cap = StrokeCap.Round)
        }
      }
      stations.forEachIndexed { i, _ ->
        val c = Offset(x0 + i * step, y)
        when {
          i == currentIndex -> {
            drawCircle(color.copy(alpha = 0.22f), radius = r * 1.9f, center = c)
            drawCircle(ground, radius = r * 1.25f, center = c)
            drawCircle(color, radius = r * 1.25f, center = c, style = Stroke(3.dp.toPx()))
          }
          i < currentIndex -> drawCircle(color, radius = r, center = c)
          else -> {
            drawCircle(ground, radius = r, center = c)
            drawCircle(track, radius = r, center = c, style = Stroke(3.dp.toPx()))
          }
        }
      }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
      stations.forEach { Text(it.label, style = YukiType.station.copy(fontSize = 9.sp), color = Yuki.colors.neutral.ink2) }
    }
  }
}
