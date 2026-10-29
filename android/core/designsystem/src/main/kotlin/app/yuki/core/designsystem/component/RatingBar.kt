package app.yuki.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yuki.core.designsystem.theme.Yuki
import app.yuki.core.designsystem.theme.YukiType
import app.yuki.core.model.Rating

/**
 * Again · Hard · Good · Easy with the next interval under each label (DESIGN-SYSTEM §5.5).
 * Disabled until the card has been flipped. "Good" wears the line colour.
 */
@Composable
fun RatingBar(intervals: Map<Rating, String>, enabled: Boolean, onRate: (Rating) -> Unit, modifier: Modifier = Modifier) {
  val c = Yuki.colors
  Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
    Rating.entries.forEach { rating ->
      val label = when (rating) {
        Rating.AGAIN -> "Again"
        Rating.HARD -> "Hard"
        Rating.GOOD -> "Good"
        Rating.EASY -> "Easy"
      }
      val interval = intervals[rating].orEmpty()
      val content: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(label, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
          if (interval.isNotEmpty()) Text(interval, style = YukiType.station.copy(fontSize = 9.sp, letterSpacing = 0.04.sp))
        }
      }
      val m = Modifier
        .weight(1f)
        .heightIn(min = 52.dp)
        .semantics { contentDescription = if (interval.isEmpty()) label else "$label, next review in $interval" }
      val padding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
      when (rating) {
        Rating.AGAIN -> OutlinedButton(
          onClick = { onRate(rating) }, enabled = enabled, modifier = m, shape = CircleShape, contentPadding = padding,
          border = BorderStroke(1.5.dp, if (enabled) c.again else c.again.copy(alpha = 0.4f)),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = c.again),
        ) { content() }
        Rating.GOOD -> Button(
          onClick = { onRate(rating) }, enabled = enabled, modifier = m, shape = CircleShape, contentPadding = padding,
          colors = ButtonDefaults.buttonColors(containerColor = c.line.field, contentColor = Color.White),
        ) { content() }
        else -> Button(
          onClick = { onRate(rating) }, enabled = enabled, modifier = m, shape = CircleShape, contentPadding = padding,
          colors = ButtonDefaults.buttonColors(containerColor = c.neutral.surface2, contentColor = c.neutral.ink),
        ) { content() }
      }
    }
  }
}

/** "4d", "10m", "3mo", "1.2y" for the rating buttons. */
fun formatInterval(seconds: Long): String = when {
  seconds < 60 -> "<1m"
  seconds < 3600 -> "${seconds / 60}m"
  seconds < 86_400 -> "${seconds / 3600}h"
  seconds < 30L * 86_400 -> "${seconds / 86_400}d"
  seconds < 365L * 86_400 -> "${seconds / (30L * 86_400)}mo"
  else -> "%.1fy".format(seconds / (365.0 * 86_400))
}
