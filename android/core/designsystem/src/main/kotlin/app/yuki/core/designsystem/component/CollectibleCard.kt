package app.yuki.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.yuki.core.designsystem.theme.Semantic
import app.yuki.core.designsystem.theme.Yuki
import app.yuki.core.model.FoilTier
import androidx.compose.animation.core.CubicBezierEasing
import android.provider.Settings

/** Material 3 "emphasized decelerate" easing, used for the flip. */
val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

private val CardShape = RoundedCornerShape(24.dp)

/** True when the system "Remove animations" setting is on. */
@Composable
fun rememberReduceMotion(): Boolean {
  val context = LocalContext.current
  return remember {
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
  }
}

/**
 * The collectible flashcard (DESIGN-SYSTEM §5.3): real 63:88 trading-card proportions, a 3D
 * flip, a gentle tilt that follows the phone, and a foil finish that reflects mastery.
 * Tilt and foil run in graphics layers only, so they never recompose per frame.
 */
@Composable
fun CollectibleCard(
  flipped: Boolean,
  onFlip: () -> Unit,
  foil: FoilTier,
  tilt: () -> Offset,
  accessibilityLabel: String,
  modifier: Modifier = Modifier,
  front: @Composable ColumnScope.() -> Unit,
  back: @Composable ColumnScope.() -> Unit,
) {
  val reduceMotion = rememberReduceMotion()
  val rotation by animateFloatAsState(
    targetValue = if (flipped) 180f else 0f,
    animationSpec = tween(if (reduceMotion) 0 else 360, easing = EmphasizedDecelerate),
    label = "flip",
  )
  val density = LocalDensity.current
  val colors = Yuki.colors
  val rim = if (colors.isDark) colors.line.field.copy(alpha = 0.6f) else Color.Transparent
  val goldRim = foil == FoilTier.GOLD

  Box(
    modifier
      .fillMaxWidth()
      .aspectRatio(63f / 88f)
      .graphicsLayer {
        val t = if (reduceMotion) Offset.Zero else tilt()
        rotationY = rotation + t.x * 6f
        rotationX = -t.y * 6f
        cameraDistance = 14f * density.density
      }
      .semantics(mergeDescendants = false) {
        contentDescription = accessibilityLabel
        onClick(label = if (flipped) "Flip back" else "Flip") { onFlip(); true }
      }
      .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onFlip),
  ) {
    val showBack = rotation > 90f
    Box(
      Modifier
        .fillMaxSize()
        .graphicsLayer { if (showBack) rotationY = 180f }
        .shadow(18.dp, CardShape, ambientColor = colors.line.deep, spotColor = colors.line.deep)
        .clip(CardShape)
        .background(colors.neutral.surface)
        .border(if (goldRim) 3.dp else 1.dp, if (goldRim) Semantic.Gold else rim, CardShape),
    ) {
      Column(Modifier.fillMaxSize().padding(16.dp)) {
        if (showBack) back() else front()
      }
      if (!showBack) FoilLayer(foil, tilt)
    }
  }
}

/** Holo and gloss layers drawn over the whole card face. */
@Composable
private fun BoxScope.FoilLayer(foil: FoilTier, tilt: () -> Offset) {
  if (foil == FoilTier.NEW || foil == FoilTier.LEARNING) return
  val holo = foil == FoilTier.MASTERED || foil == FoilTier.GOLD
  Box(
    Modifier
      .matchParentSize()
      .drawWithCache {
        onDrawWithContent {
          val t = tilt()
          val center = Offset(size.width * (0.5f + t.x * 0.4f), size.height * (0.35f + t.y * 0.4f))
          if (holo) {
            drawRect(Brush.sweepGradient(Semantic.Holo, center = center), alpha = 0.28f, blendMode = BlendMode.Overlay)
          }
          drawRect(
            Brush.radialGradient(listOf(Color.White.copy(alpha = 0.55f), Color.Transparent), center = center, radius = size.width * 0.6f),
            alpha = if (holo) 0.5f else 0.6f,
          )
        }
      },
  )
}
