package app.yuki.core.designsystem.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.yuki.core.designsystem.theme.Brand
import app.yuki.core.designsystem.theme.Yuki
import app.yuki.core.model.Mood
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun Mood.label() = when (this) {
  Mood.WAVE -> "Yuki waving"
  Mood.CELEBRATE -> "Yuki celebrating"
  Mood.WORRIED -> "Yuki looking worried"
  Mood.SLEEPY -> "Yuki sleeping"
}

/**
 * Yuki, the mascot (PRD S19): an ink-line avatar (YukiArt.kt) whose scarf, hair-clip flake and
 * sparkles take [tint], the current line colour by default. A gentle idle
 * "breathing" bob, a pop when her mood changes, two little hops when celebrating, and a hop when
 * tapped. All motion is in graphics layers and stops when the system "Remove animations" setting is on.
 */
@Composable
fun YukiMascot(
  mood: Mood,
  modifier: Modifier = Modifier,
  size: Dp = 120.dp,
  tint: Color = Yuki.colors.line.field,
  /** The language whose outfit she wears (Breton top for French, yukata for Japanese…); null = neutral cardigan and scarf. */
  outfit: app.yuki.core.model.Lang? = null,
  onTap: (() -> Unit)? = null,
) {
  val outfitKey = outfit?.code ?: "neutral"
  val reduceMotion = rememberReduceMotion()
  val idle = rememberInfiniteTransition(label = "yuki-idle")
  val breath by idle.animateFloat(
    initialValue = 0f, targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
    label = "breath",
  )
  val hop = remember { Animatable(0f) }
  val scope = rememberCoroutineScope()

  LaunchedEffect(mood, reduceMotion) {
    if (reduceMotion) return@LaunchedEffect
    if (mood == Mood.CELEBRATE) {
      repeat(2) {
        hop.animateTo(-1f, tween(180))
        hop.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
        delay(120)
      }
    }
  }

  Box(
    modifier
      .size(size)
      .semantics { contentDescription = mood.label() }
      .then(
        if (onTap == null) Modifier
        else Modifier.clickable(remember { MutableInteractionSource() }, indication = null) {
          onTap()
          if (!reduceMotion) scope.launch {
            hop.animateTo(-0.6f, tween(140))
            hop.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
          }
        },
      ),
    contentAlignment = Alignment.BottomCenter,
  ) {
    AnimatedContent(
      targetState = mood,
      transitionSpec = {
        if (reduceMotion) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
        else (scaleIn(spring(dampingRatio = 0.55f), initialScale = 0.85f) + fadeIn(tween(160))) togetherWith fadeOut(tween(120))
      },
      label = "mood",
    ) { m ->
      Image(
        painter = rememberVectorPainter(remember(m, tint, outfitKey) { yukiVector(m, tint, outfitKey) }),
        contentDescription = null,
        modifier = Modifier
          .size(size)
          .graphicsLayer {
            val b = if (reduceMotion) 0f else breath
            transformOrigin = TransformOrigin(0.5f, 1f)
            scaleY = 1f + 0.018f * b
            scaleX = 1f - 0.006f * b
            translationY = hop.value * size.toPx() * 0.12f
          },
      )
    }
  }
}

/** Speech bubble pointing left toward the mascot. */
@Composable
fun SpeechBubble(text: String, modifier: Modifier = Modifier) {
  val c = Yuki.colors
  Row(modifier, verticalAlignment = Alignment.CenterVertically) {
    Canvas(Modifier.size(width = 10.dp, height = 16.dp)) {
      val p = Path().apply {
        moveTo(size.width, 0f)
        lineTo(0f, size.height / 2)
        lineTo(size.width, size.height)
        close()
      }
      drawPath(p, c.neutral.surface)
    }
    Text(
      text,
      style = MaterialTheme.typography.bodyMedium,
      color = c.neutral.ink,
      modifier = Modifier
        .background(c.neutral.surface, RoundedCornerShape(16.dp))
        .padding(horizontal = 14.dp, vertical = 10.dp),
    )
  }
}

/** Yuki with her speech bubble, as used at the top of Today. Neutral screens use the brand maroon scarf. */
@Composable
fun MascotGreeting(
  mood: Mood,
  message: String,
  modifier: Modifier = Modifier,
  tint: Color = Brand.Maroon,
  onTap: (() -> Unit)? = null,
) {
  Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    YukiMascot(mood, size = 112.dp, tint = tint, onTap = onTap)
    Spacer(Modifier.width(4.dp))
    SpeechBubble(message, Modifier.weight(1f))
  }
}
