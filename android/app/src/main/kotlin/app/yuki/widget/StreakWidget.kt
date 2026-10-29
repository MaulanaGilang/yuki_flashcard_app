package app.yuki.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.yuki.MainActivity
import app.yuki.R
import app.yuki.YukiApp
import app.yuki.core.model.CoachLine
import app.yuki.core.model.DayStatus
import app.yuki.core.model.Mood
import app.yuki.core.model.YukiCoach
import kotlinx.coroutines.launch

private fun Mood.bust(): Int = when (this) {
  Mood.WAVE -> R.drawable.yuki_bust_wave
  Mood.CELEBRATE -> R.drawable.yuki_bust_celebrate
  Mood.WORRIED -> R.drawable.yuki_bust_worried
  Mood.SLEEPY -> R.drawable.yuki_bust_sleepy
}

/**
 * Streak widget with Yuki (PRD S14/S19), in the spirit of Duolingo's and Bunpo's: the flame and
 * day count, and Yuki reminding you, worried in the evening if today's session is still to do,
 * celebrating once it's done. Responsive: 2×1 compact, 4×1 with the full sentence, 2×2 larger Yuki.
 */
class StreakWidget : GlanceAppWidget() {
  override val sizeMode = SizeMode.Responsive(setOf(SMALL, WIDE, TALL))

  override suspend fun provideGlance(context: Context, id: GlanceId) {
    val graph = (context.applicationContext as YukiApp).graph
    val status = runCatching { graph.progress.dayStatus() }.getOrElse { DayStatus(0, false, 0, 12, hasLines = false) }
    val line = YukiCoach.line(status)
    provideContent { StreakBody(context, status, line) }
  }

  companion object {
    val SMALL = DpSize(110.dp, 48.dp)
    val WIDE = DpSize(250.dp, 48.dp)
    val TALL = DpSize(110.dp, 110.dp)

    fun refreshAll(context: Context) {
      val app = context.applicationContext as YukiApp
      app.graph.appScope.launch { runCatching { StreakWidget().updateAll(app) } }
    }
  }
}

private val Ground = ColorProvider(day = Color(0xFFFFF4EE), night = Color(0xFF231915))
private val Ink = ColorProvider(day = Color(0xFF14161B), night = Color(0xFFF1F2F4))
private val Ink2 = ColorProvider(day = Color(0xFF4A505C), night = Color(0xFFA6ACB8))
private val Flame = ColorProvider(day = Color(0xFFD63A22), night = Color(0xFFFF7A5C))
private val Dim = ColorProvider(day = Color(0xFF8A8F99), night = Color(0xFF7D828C))

@Composable
private fun StreakBody(context: Context, status: DayStatus, line: CoachLine) {
  val size = LocalSize.current
  val wide = size.width >= StreakWidget.WIDE.width
  val tall = size.height >= StreakWidget.TALL.height
  val open = Intent(context, MainActivity::class.java)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
  val lit = status.studiedToday && status.streak > 0
  val streakStyle = TextStyle(color = if (lit) Flame else Dim, fontSize = if (tall) 30.sp else 24.sp, fontWeight = FontWeight.Bold)
  val messageStyle = TextStyle(color = Ink2, fontSize = 12.sp)
  val modifier = GlanceModifier.fillMaxSize().background(Ground).cornerRadius(22.dp).clickable(actionStartActivity(open))

  if (tall && !wide) {
    Column(modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
      Image(ImageProvider(line.mood.bust()), contentDescription = "Yuki", modifier = GlanceModifier.size(72.dp))
      Text("🔥 ${status.streak}", style = streakStyle)
      Text(line.short, style = messageStyle, maxLines = 1)
    }
    return
  }
  Row(modifier.padding(start = 0.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
    Image(ImageProvider(line.mood.bust()), contentDescription = "Yuki", modifier = GlanceModifier.size(if (tall) 96.dp else 72.dp))
    Spacer(GlanceModifier.width(6.dp))
    Column(verticalAlignment = Alignment.CenterVertically) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text("🔥", style = TextStyle(fontSize = if (tall) 24.sp else 18.sp))
        Spacer(GlanceModifier.width(4.dp))
        Text("${status.streak}", style = streakStyle)
        if (wide) {
          Spacer(GlanceModifier.width(6.dp))
          Text(if (status.streak == 1) "day" else "days", style = TextStyle(color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium))
        }
      }
      Spacer(GlanceModifier.height(2.dp))
      Text(if (wide) line.message else line.short, style = messageStyle, maxLines = if (wide) 2 else 1)
    }
  }
}

class StreakWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget: GlanceAppWidget = StreakWidget()
}
