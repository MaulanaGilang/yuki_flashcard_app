package app.yuki.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import app.yuki.core.model.Lang

@Immutable
data class YukiColors(
  val line: LineColors,
  val neutral: NeutralColors,
  val tones: List<Color>,
  val again: Color,
  val isDark: Boolean,
) {
  /** Background of drenched (study) screens. */
  val field: Color get() = if (isDark) line.nightField else line.field
  val artTint: Color get() = if (isDark) line.tintDark else line.tintLight
  fun tone(tone: Int): Color = tones[(tone.coerceIn(1, 5)) - 1]
}

val LocalYukiColors = staticCompositionLocalOf {
  YukiColors(Lines.Persimmon, Neutrals.Light, Tones.Light, Semantic.AgainLight, isDark = false)
}

object Yuki {
  val colors: YukiColors
    @Composable @ReadOnlyComposable get() = LocalYukiColors.current
}

/**
 * App theme. The active language's line colour becomes Material's primary, so system
 * components (switches, dialogs, snackbars) follow the line (DESIGN-SYSTEM §2).
 */
@Composable
fun YukiTheme(lang: Lang = Lang.ZH, darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
  val line = Lines.of(lang)
  val neutral = if (darkTheme) Neutrals.Dark else Neutrals.Light
  val primary = if (darkTheme) line.nightField else line.field
  val scheme = if (darkTheme) {
    darkColorScheme(
      primary = primary, onPrimary = Color.White,
      primaryContainer = line.tintDark, onPrimaryContainer = neutral.ink,
      secondary = line.deep, onSecondary = Color.White,
      secondaryContainer = line.tintDark, onSecondaryContainer = neutral.ink,
      background = neutral.ground, onBackground = neutral.ink,
      surface = neutral.surface, onSurface = neutral.ink,
      surfaceVariant = neutral.surface2, onSurfaceVariant = neutral.ink2,
      surfaceContainer = neutral.surface, surfaceContainerHigh = neutral.surface2,
      surfaceContainerLow = neutral.surface, surfaceContainerLowest = neutral.ground,
      outline = neutral.outline, outlineVariant = neutral.outline,
      error = Semantic.AgainDark,
    )
  } else {
    lightColorScheme(
      primary = primary, onPrimary = Color.White,
      primaryContainer = line.tintLight, onPrimaryContainer = neutral.ink,
      secondary = line.deep, onSecondary = Color.White,
      secondaryContainer = line.tintLight, onSecondaryContainer = neutral.ink,
      background = neutral.ground, onBackground = neutral.ink,
      surface = neutral.surface, onSurface = neutral.ink,
      surfaceVariant = neutral.surface2, onSurfaceVariant = neutral.ink2,
      surfaceContainer = neutral.surface, surfaceContainerHigh = neutral.surface2,
      surfaceContainerLow = neutral.surface, surfaceContainerLowest = neutral.ground,
      outline = neutral.outline, outlineVariant = neutral.outline,
      error = Semantic.AgainLight,
    )
  }
  val colors = YukiColors(line, neutral, if (darkTheme) Tones.Dark else Tones.Light, if (darkTheme) Semantic.AgainDark else Semantic.AgainLight, darkTheme)
  CompositionLocalProvider(LocalYukiColors provides colors) {
    MaterialTheme(colorScheme = scheme, typography = YukiTypography, content = content)
  }
}
