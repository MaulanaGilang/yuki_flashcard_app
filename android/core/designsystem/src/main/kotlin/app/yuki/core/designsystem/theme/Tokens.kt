package app.yuki.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import app.yuki.core.model.Lang

/** All colour literals live here (DESIGN-SYSTEM §2). Feature code uses tokens only. */
@Immutable
data class LineColors(val field: Color, val deep: Color, val tintLight: Color, val tintDark: Color) {
  /** Station colour: deepens with level (DESIGN-SYSTEM §2.1). */
  fun station(levelIndex: Int, levelCount: Int): Color {
    val t = if (levelCount <= 1) 0f else levelIndex.toFloat() / (levelCount - 1) * 0.55f
    return lerp(field, deep, t)
  }

  /** Dark theme field: field mixed 35 % toward deep. */
  val nightField: Color get() = lerp(this.field, deep, 0.35f)
}

/** Brand colour for the logo and neutral screens (DESIGN-SYSTEM §2.2), separate from the language lines. */
object Brand {
  val Maroon = Color(0xFF7A1F2B)
}

object Lines {
  val Persimmon = LineColors(Color(0xFFD63A22), Color(0xFF8E2414), Color(0xFFFAE7E4), Color(0xFF3A1A15))
  val Bougainvillea = LineColors(Color(0xFFB42D78), Color(0xFF6E1A4A), Color(0xFFF6E6EF), Color(0xFF351527))
  val Cobalt = LineColors(Color(0xFF2B4FD8), Color(0xFF1A2F86), Color(0xFFE6EAFA), Color(0xFF18203F))
  val Jade = LineColors(Color(0xFF0B7A64), Color(0xFF064B3D), Color(0xFFE2EFEC), Color(0xFF0F2A24))
  val Ochre = LineColors(Color(0xFF8C5E00), Color(0xFF553900), Color(0xFFF1ECE0), Color(0xFF29200F))
  val Iris = LineColors(Color(0xFF6A3FD1), Color(0xFF3E2380), Color(0xFFEDE8F9), Color(0xFF211A3D))
  val Olive = LineColors(Color(0xFF4E7614), Color(0xFF2F470B), Color(0xFFEAEFE3), Color(0xFF1B2613))

  fun of(lang: Lang) = when (lang) {
    Lang.ZH -> Persimmon
    Lang.ES -> Bougainvillea
    Lang.RU -> Cobalt
    Lang.EN -> Jade
    Lang.DE -> Ochre
    Lang.FR -> Iris
    Lang.AR -> Olive
  }
}

@Immutable
data class NeutralColors(
  val ground: Color,
  val surface: Color,
  val surface2: Color,
  val outline: Color,
  val ink: Color,
  val ink2: Color,
)

object Neutrals {
  val Light = NeutralColors(Color(0xFFEEF0F3), Color(0xFFFFFFFF), Color(0xFFF6F7F9), Color(0xFFD5D9E0), Color(0xFF14161B), Color(0xFF4A505C))
  val Dark = NeutralColors(Color(0xFF0D0F13), Color(0xFF181B21), Color(0xFF22262E), Color(0xFF343944), Color(0xFFF1F2F4), Color(0xFFA6ACB8))
}

/** Pinyin tone colours: index 0 = tone 1 … index 4 = neutral (DESIGN-SYSTEM §2.3). */
object Tones {
  val Light = listOf(Color(0xFFC8102E), Color(0xFFA65300), Color(0xFF1C7F35), Color(0xFF1F4FD1), Color(0xFF5F6673))
  val Dark = listOf(Color(0xFFFF7A86), Color(0xFFFFAE5C), Color(0xFF62D584), Color(0xFF8AABFF), Color(0xFFA0A7B3))
}

object Semantic {
  val AgainLight = Color(0xFFC62828)
  val AgainDark = Color(0xFFFF8A80)
  val Gold = Color(0xFFE8C66A)
  val Holo = listOf(Color(0xFFFF9AA2), Color(0xFFFFE29A), Color(0xFF9AF5C9), Color(0xFF9AD4FF), Color(0xFFC9A7FF), Color(0xFFFF9AA2))
  val OnField = Color(0xFFFFFFFF)
}
