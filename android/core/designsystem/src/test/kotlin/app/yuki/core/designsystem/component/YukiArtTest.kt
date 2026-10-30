package app.yuki.core.designsystem.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import app.yuki.core.model.Lang
import app.yuki.core.model.Mood
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YukiArtTest {
  private val outfits = listOf("neutral") + Lang.entries.map { it.code }

  private fun paths(mood: Mood, tint: Color, outfit: String = "neutral") =
    (yukiVector(mood, tint, outfit).root as VectorGroup).filterIsInstance<VectorPath>()

  @Test
  fun everyOutfitAndPoseParses() {
    for (outfit in outfits) for (mood in Mood.entries) {
      val paths = paths(mood, Color.Red, outfit)
      assertTrue("$outfit/$mood has paths", paths.size > 10)
      assertTrue("$outfit/$mood: every path has nodes", paths.all { it.pathData.isNotEmpty() })
    }
  }

  @Test
  fun accentsTakeTheTint() {
    val tint = Color(0xFF2B4FD8)
    for (outfit in outfits) for (mood in Mood.entries) {
      val tinted = paths(mood, tint, outfit).any { (it.fill as? SolidColor)?.value == tint || (it.stroke as? SolidColor)?.value == tint }
      assertTrue("$outfit/$mood uses the tint", tinted)
    }
  }

  @Test
  fun everyLanguageHasItsOwnOutfit() {
    val neutral = paths(Mood.WAVE, Color.Red).map { it.pathData }
    val seen = mutableSetOf(neutral)
    for (lang in Lang.entries) {
      val drawing = paths(Mood.WAVE, Color.Red, lang.code).map { it.pathData }
      assertNotEquals("${lang.code} differs from neutral", neutral, drawing)
      assertTrue("${lang.code} differs from every other outfit", seen.add(drawing))
    }
    // Unknown outfits fall back to neutral rather than crashing.
    assertTrue(paths(Mood.WAVE, Color.Red, "xx").map { it.pathData } == neutral)
  }
}
