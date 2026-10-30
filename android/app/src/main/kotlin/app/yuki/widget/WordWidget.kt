package app.yuki.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.yuki.MainActivity
import app.yuki.YukiApp
import app.yuki.core.designsystem.theme.Lines
import app.yuki.core.model.Lang
import app.yuki.core.model.script.ScriptGuide
import kotlinx.coroutines.launch

/** What the 2×1 widget shows. */
data class WidgetWord(
  val wordId: String,
  val lang: Lang,
  val headword: String,
  /** Pinyin, transliteration or IPA. */
  val reading: String,
  val gloss: String,
)

/**
 * 2×1 "word of the day" home-screen widget (PRD S14). One word you're learning, drenched in its
 * line colour; tap to open it. Refreshed every 6 hours by the system and whenever Yuki goes to
 * the background.
 */
class WordWidget : GlanceAppWidget() {
  override val sizeMode = SizeMode.Exact

  override suspend fun provideGlance(context: Context, id: GlanceId) {
    val graph = (context.applicationContext as YukiApp).graph
    graph.packs.ensureInstalled()
    val word = pick(graph)
    provideContent { WidgetBody(context, word) }
  }

  private suspend fun pick(graph: app.yuki.AppGraph): WidgetWord? {
    val detail = graph.study.wordOfTheDay() ?: return null
    val w = detail.word
    val reading = when {
      w.lang == Lang.ZH && w.syllables.isNotEmpty() -> w.syllables.joinToString("")
      w.lang.needsTransliteration -> ScriptGuide.of(w.lang)?.transliterate(w.headword).orEmpty()
      w.lang == Lang.JA -> listOf(w.reading.takeIf { it != w.headword }, app.yuki.core.model.script.KanaGuide.transliterate(w.reading.ifBlank { w.headword }))
        .filterNot { it.isNullOrBlank() }.joinToString(" ")
      else -> w.reading
    }
    return WidgetWord(w.id, w.lang, w.headword, reading, detail.senses.firstOrNull()?.gloss.orEmpty())
  }

  companion object {
    const val EXTRA_WORD_ID = "app.yuki.extra.WORD_ID"

    fun refreshAll(context: Context) {
      val app = context.applicationContext as YukiApp
      app.graph.appScope.launch { runCatching { WordWidget().updateAll(app) } }
    }
  }
}

@Composable
private fun WidgetBody(context: Context, word: WidgetWord?) {
  val white = ColorProvider(Color.White)
  val soft = ColorProvider(Color.White.copy(alpha = 0.85f))
  val field = Lines.of(word?.lang ?: Lang.ZH).field
  val open = Intent(context, MainActivity::class.java)
    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    .apply { word?.let { putExtra(WordWidget.EXTRA_WORD_ID, it.wordId) } }
  Row(
    GlanceModifier
      .fillMaxSize()
      .background(ColorProvider(field))
      .cornerRadius(20.dp)
      .padding(horizontal = 14.dp, vertical = 8.dp)
      .clickable(actionStartActivity(open)),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    if (word == null) {
      Text("Open Yuki to start your first line", style = TextStyle(color = white, fontSize = 14.sp, fontWeight = FontWeight.Medium))
      return@Row
    }
    Box(GlanceModifier.size(8.dp).cornerRadius(4.dp).background(white)) {}
    Spacer(GlanceModifier.width(10.dp))
    Column {
      Text(word.headword, maxLines = 1, style = TextStyle(color = white, fontSize = 22.sp, fontWeight = FontWeight.Bold))
      val second = listOf(word.reading, word.gloss).filter { it.isNotBlank() }.joinToString(" · ")
      Text(second, maxLines = 1, style = TextStyle(color = soft, fontSize = 13.sp))
    }
  }
}

class WordWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget: GlanceAppWidget = WordWidget()
}
