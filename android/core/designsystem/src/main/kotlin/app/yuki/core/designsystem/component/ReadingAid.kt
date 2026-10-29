package app.yuki.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import app.yuki.core.designsystem.theme.Yuki
import app.yuki.core.designsystem.theme.YukiType
import app.yuki.core.designsystem.theme.scriptFamily
import app.yuki.core.model.Lang
import app.yuki.core.model.script.ScriptGuide

/** Latin reading of a non-Latin word, e.g. "yábloko" under я́блоко (PRD S17). */
@Composable
fun TransliterationLine(text: String, lang: Lang, modifier: Modifier = Modifier) {
  val guide = ScriptGuide.of(lang) ?: return
  val latin = remember(text) { guide.transliterate(text) }
  if (latin.isBlank()) return
  Text(latin, style = YukiType.reading.copy(fontSize = 20.sp), color = Yuki.colors.line.field, textAlign = TextAlign.Center, modifier = modifier)
}

/**
 * "How to read this word": letter-by-letter tiles (glyph over its Latin sound, with a note such
 * as "unstressed: sounds like a"), followed by the reading tips that apply to this word.
 * Tiles follow the script's own direction, so Arabic reads right to left.
 */
@Composable
fun HowToReadPanel(word: String, lang: Lang, modifier: Modifier = Modifier) {
  val guide = ScriptGuide.of(lang) ?: return
  val units = remember(word) { guide.breakdown(word) }
  val hints = remember(word) { guide.hints(word) }
  val c = Yuki.colors
  Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
    if (units.isNotEmpty()) {
      CompositionLocalProvider(LocalLayoutDirection provides if (lang.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
          units.forEach { u ->
            Column(
              Modifier
                .widthIn(min = 44.dp)
                .background(if (u.note != null) c.artTint else c.neutral.surface2, RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Text(u.glyph, fontFamily = scriptFamily(lang), fontSize = 24.sp, lineHeight = if (lang.rtl) 40.sp else 30.sp, color = c.neutral.ink)
              Text(u.latin.ifEmpty { "·" }, style = MaterialTheme.typography.labelLarge, color = c.line.field)
              u.note?.let { Text(it, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp), color = c.neutral.ink2, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 96.dp)) }
            }
          }
        }
      }
    }
    hints.forEach { h ->
      Row(verticalAlignment = Alignment.Top) {
        Text("•  ", color = c.line.field, style = MaterialTheme.typography.bodyMedium)
        Text(h, style = MaterialTheme.typography.bodyMedium, color = c.neutral.ink, modifier = Modifier.fillMaxWidth())
      }
    }
  }
}
