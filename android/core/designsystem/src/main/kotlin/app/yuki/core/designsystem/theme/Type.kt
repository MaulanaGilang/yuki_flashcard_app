package app.yuki.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.yuki.core.designsystem.R

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, vararg weights: Int) = FontFamily(
  weights.map { w ->
    Font(res, weight = FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
  },
)

/** Display / signage: stations, counts, the arrival board. */
val Unbounded = variable(R.font.unbounded_variable, 500, 600, 700, 800)

/** UI and body text; Cyrillic first-class. */
val Onest = variable(R.font.onest_variable, 400, 500, 600, 700)

/** Kai-style hanzi that match handwriting and the stroke data (mainland GB forms). */
val WenKai = FontFamily(Font(R.font.lxgw_wenkai_gb, FontWeight.Normal))

/** Naskh Arabic: the clear book style learners read first; renders every vowel mark. */
val Naskh = variable(R.font.noto_naskh_arabic, 400, 500, 600, 700)

/** The face for words and sentences in [lang]'s script. */
fun scriptFamily(lang: app.yuki.core.model.Lang): FontFamily = when (lang.script) {
  app.yuki.core.model.Script.HAN -> WenKai
  app.yuki.core.model.Script.ARABIC -> Naskh
  else -> Onest
}

/** Custom card roles (DESIGN-SYSTEM §3.1). */
object YukiType {
  fun glyphHero(chars: Int) = TextStyle(
    fontFamily = WenKai,
    fontSize = when { chars <= 2 -> 88.sp; chars <= 4 -> 64.sp; else -> 44.sp },
    lineHeight = when { chars <= 2 -> 100.sp; chars <= 4 -> 76.sp; else -> 52.sp },
  )

  val wordHero = TextStyle(fontFamily = Onest, fontWeight = FontWeight.Bold, fontSize = 44.sp, lineHeight = 52.sp)
  val reading = TextStyle(fontFamily = Onest, fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 32.sp)
  val sentenceZh = TextStyle(fontFamily = WenKai, fontSize = 20.sp, lineHeight = 30.sp)
  val station = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.08.em)
}

val YukiTypography = Typography(
  displaySmall = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 44.sp),
  headlineLarge = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 40.sp),
  headlineMedium = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp),
  headlineSmall = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 30.sp),
  titleLarge = TextStyle(fontFamily = Onest, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
  titleMedium = TextStyle(fontFamily = Onest, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
  titleSmall = TextStyle(fontFamily = Onest, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
  bodyLarge = TextStyle(fontFamily = Onest, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
  bodyMedium = TextStyle(fontFamily = Onest, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
  bodySmall = TextStyle(fontFamily = Onest, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
  labelLarge = TextStyle(fontFamily = Onest, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.02.em),
  labelMedium = TextStyle(fontFamily = Onest, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
  labelSmall = TextStyle(fontFamily = Unbounded, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.08.em),
)
