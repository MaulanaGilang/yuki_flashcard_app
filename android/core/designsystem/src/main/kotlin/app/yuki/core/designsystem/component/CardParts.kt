package app.yuki.core.designsystem.component

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yuki.core.designsystem.theme.Unbounded
import app.yuki.core.designsystem.theme.Yuki
import app.yuki.core.designsystem.theme.YukiType
import app.yuki.core.model.FoilTier
import app.yuki.core.model.Lang
import app.yuki.core.model.Script
import app.yuki.core.designsystem.theme.Naskh
import androidx.compose.ui.text.style.TextDirection

/** Top row of a card: line bullet, station + part of speech, foil pips. */
@Composable
fun CardHeader(bullet: String, label: String, foil: FoilTier, modifier: Modifier = Modifier) {
  val c = Yuki.colors
  Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    LineBullet(bullet, c.line.field, size = 24.dp)
    Spacer(Modifier.width(8.dp))
    Text(label.uppercase(), style = YukiType.station, color = c.neutral.ink2, modifier = Modifier.weight(1f), maxLines = 1)
    Text(foilPips(foil), style = YukiType.station.copy(letterSpacing = 2.sp), color = if (foil == FoilTier.GOLD) app.yuki.core.designsystem.theme.Semantic.Gold else c.line.field)
  }
}

fun foilPips(foil: FoilTier) = when (foil) {
  FoilTier.NEW -> "○○○"
  FoilTier.LEARNING -> "●○○"
  FoilTier.REVIEW -> "●●○"
  FoilTier.MASTERED -> "●●●"
  FoilTier.GOLD -> "★"
}

/** What sits in the art window (DESIGN-SYSTEM §5.4). */
sealed interface Art {
  data class Picture(val bytes: ByteArray) : Art
  data class Typographic(val keyword: String) : Art
}

/** Art window: line-tinted tile; images sit inside it like die-cut stickers. */
@Composable
fun ArtWindow(art: Art, modifier: Modifier = Modifier) {
  val c = Yuki.colors
  Box(
    modifier.fillMaxWidth().background(c.artTint, RoundedCornerShape(18.dp)),
    contentAlignment = Alignment.Center,
  ) {
    when (art) {
      is Art.Picture -> {
        val bitmap = remember(art) { BitmapFactory.decodeByteArray(art.bytes, 0, art.bytes.size)?.asImageBitmap() }
        if (bitmap != null) {
          Image(bitmap, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(16.dp))
        }
      }
      is Art.Typographic -> Text(
        art.keyword.uppercase(),
        fontFamily = Unbounded,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        color = c.line.field,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(16.dp),
      )
    }
  }
}

/** Pinyin coloured by tone, one span per syllable; tone marks always kept (DESIGN-SYSTEM §2.3). */
@Composable
fun toneText(syllables: List<String>, tones: List<Int>, showToneNumbers: Boolean = false, separator: String = ""): AnnotatedString {
  val c = Yuki.colors
  return buildAnnotatedString {
    syllables.forEachIndexed { i, syl ->
      if (i > 0) append(separator)
      val tone = tones.getOrNull(i) ?: 5
      withStyle(SpanStyle(color = c.tone(tone))) {
        append(syl)
        if (showToneNumbers) append(tone.toString())
      }
    }
  }
}

/** Large headword with the right face and size for its script. */
@Composable
fun Headword(text: String, lang: Lang, modifier: Modifier = Modifier, maxStyle: TextStyle? = null) {
  val style = when {
    maxStyle != null -> maxStyle
    lang.script == Script.HAN -> YukiType.glyphHero(text.codePointCount(0, text.length))
    lang.script == Script.ARABIC -> TextStyle(fontFamily = Naskh, fontWeight = FontWeight.SemiBold, fontSize = 48.sp, lineHeight = 80.sp)
    text.length > 12 -> YukiType.wordHero.copy(fontSize = 32.sp, lineHeight = 40.sp)
    else -> YukiType.wordHero
  }
  Text(
    text,
    style = style.copy(localeList = LocaleList(lang.code)),
    color = Yuki.colors.neutral.ink,
    textAlign = TextAlign.Center,
    modifier = modifier,
  )
}

/** Body-size text in [lang]'s script (example sentences); right-to-left scripts align right. */
@Composable
fun ScriptText(text: String, lang: Lang, modifier: Modifier = Modifier, color: Color = Yuki.colors.neutral.ink) {
  val style = when (lang.script) {
    Script.HAN -> YukiType.sentenceZh
    Script.ARABIC -> TextStyle(fontFamily = Naskh, fontSize = 22.sp, lineHeight = 40.sp)
    else -> MaterialTheme.typography.bodyLarge
  }
  Text(
    text,
    style = style.copy(localeList = LocaleList(lang.code), textDirection = if (lang.rtl) TextDirection.Rtl else TextDirection.Content),
    color = color,
    modifier = modifier.fillMaxWidth(),
  )
}

/** 48 dp round play button in the line colour. */
@Composable
fun AudioButton(onClick: () -> Unit, enabled: Boolean, contentDescription: String, modifier: Modifier = Modifier, small: Boolean = false) {
  FilledIconButton(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier.size(if (small) 40.dp else 48.dp),
    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Yuki.colors.line.field, contentColor = Color.White),
  ) {
    Icon(PlayIcon, contentDescription = contentDescription, modifier = Modifier.size(if (small) 18.dp else 22.dp))
  }
}

/** Small grammar chip: measure word, gender, aspect. */
@Composable
fun GrammarChip(text: String, modifier: Modifier = Modifier) {
  val c = Yuki.colors
  Text(
    text,
    style = MaterialTheme.typography.labelMedium,
    color = c.neutral.ink,
    modifier = modifier
      .background(c.neutral.surface2, RoundedCornerShape(10.dp))
      .border(1.dp, c.neutral.outline, RoundedCornerShape(10.dp))
      .padding(horizontal = 10.dp, vertical = 4.dp),
  )
}

@Composable
fun CardDivider() {
  Box(Modifier.fillMaxWidth().padding(vertical = 10.dp).height(1.dp).background(Yuki.colors.neutral.outline))
}

/** Pill-shaped status badge with a text label (never colour alone). */
@Composable
fun StatusBadge(text: String, container: Color, content: Color, modifier: Modifier = Modifier) {
  Text(
    text,
    style = MaterialTheme.typography.labelMedium,
    color = content,
    modifier = modifier.background(container, CircleShape).padding(horizontal = 10.dp, vertical = 3.dp),
  )
}

val PlayIcon: ImageVector = ImageVector.Builder("play", 24.dp, 24.dp, 24f, 24f).apply {
  path(fill = androidx.compose.ui.graphics.SolidColor(Color.Black)) {
    moveTo(8f, 5.5f)
    lineTo(19f, 12f)
    lineTo(8f, 18.5f)
    close()
  }
}.build()

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
  Text(text.uppercase(), style = YukiType.station, color = Yuki.colors.neutral.ink2, modifier = modifier)
}

@Composable
fun Stack(modifier: Modifier = Modifier, gap: Int = 8, content: @Composable () -> Unit) {
  Column(modifier, verticalArrangement = Arrangement.spacedBy(gap.dp)) { content() }
}
