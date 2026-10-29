package app.yuki.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yuki.core.designsystem.component.SectionLabel
import app.yuki.core.designsystem.theme.Yuki
import app.yuki.core.designsystem.theme.scriptFamily
import app.yuki.core.model.Lang
import app.yuki.core.model.script.Letter
import app.yuki.core.model.script.ScriptGuide

/** Pinyin tones, the "alphabet" a Mandarin learner needs first. */
private val pinyinTones = listOf(
  Letter("mā", "tone 1", "high, level", "high and flat, like holding a note"),
  Letter("má", "tone 2", "rising", "rises, like asking \"what?\""),
  Letter("mǎ", "tone 3", "dipping", "dips low, then rises a little"),
  Letter("mà", "tone 4", "falling", "falls sharply, like a firm \"no!\""),
  Letter("ma", "neutral", "light", "short and light, no tone"),
)

/** Alphabet and reading rules for a language (PRD S17). */
@Composable
fun AlphabetScreen(lang: Lang, onBack: () -> Unit) {
  val c = Yuki.colors
  val guide = ScriptGuide.of(lang)
  val title = guide?.title ?: if (lang == Lang.ZH) "Pinyin & tones" else "Reading ${lang.englishName}"
  val intro = guide?.intro ?: if (lang == Lang.ZH) "Pinyin spells Mandarin with Latin letters. The mark over the vowel is the tone, and the tone changes the meaning: mā (mother), mǎ (horse)." else ""
  val letters = guide?.alphabet?.takeIf { it.isNotEmpty() } ?: if (lang == Lang.ZH) pinyinTones else emptyList()
  LazyColumn(
    Modifier.fillMaxSize().background(c.neutral.ground).windowInsetsPadding(WindowInsets.safeDrawing),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    item {
      IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Back" }) { Text("←", fontSize = 22.sp, color = c.neutral.ink) }
      Text(title, style = MaterialTheme.typography.headlineMedium, color = c.neutral.ink)
      Text(intro, style = MaterialTheme.typography.bodyLarge, color = c.neutral.ink2, modifier = Modifier.padding(top = 6.dp, bottom = 8.dp))
    }
    items(letters, key = { it.glyph + it.name }) { l -> LetterRow(l, lang) }
    val rules = guide?.rules.orEmpty()
    if (rules.isNotEmpty()) {
      item { SectionLabel("Spelling → sound", Modifier.padding(top = 8.dp)) }
      items(rules, key = { it.spelling }) { r ->
        Row(
          Modifier.fillMaxWidth().background(c.neutral.surface, RoundedCornerShape(14.dp)).padding(14.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(r.spelling, style = MaterialTheme.typography.titleMedium, color = c.line.field, modifier = Modifier.widthIn(min = 96.dp))
          Column(Modifier.weight(1f)) {
            Text(r.sounds, style = MaterialTheme.typography.bodyLarge, color = c.neutral.ink)
            Text(r.example, style = MaterialTheme.typography.bodySmall, color = c.neutral.ink2)
          }
        }
      }
    }
  }
}

@Composable
private fun LetterRow(l: Letter, lang: Lang) {
  val c = Yuki.colors
  Row(
    Modifier.fillMaxWidth().background(c.neutral.surface, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      l.glyph,
      fontFamily = scriptFamily(lang),
      fontWeight = FontWeight.Medium,
      fontSize = 30.sp,
      lineHeight = if (lang.rtl) 52.sp else 38.sp,
      color = c.neutral.ink,
      textAlign = TextAlign.Center,
      modifier = Modifier.widthIn(min = 72.dp),
    )
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(l.latin, style = MaterialTheme.typography.titleMedium, color = c.line.field)
        Text("  ·  ${l.name}", style = MaterialTheme.typography.bodySmall, color = c.neutral.ink2)
      }
      Text(l.sound, style = MaterialTheme.typography.bodyMedium, color = c.neutral.ink)
      l.lookAlike?.let { Text("⚠ $it", style = MaterialTheme.typography.bodySmall, color = c.again) }
    }
  }
}
