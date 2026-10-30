package app.yuki.core.designsystem.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import app.yuki.core.model.Mood
import org.junit.Assert.assertTrue
import org.junit.Test

class YukiArtTest {
  private fun paths(mood: Mood, tint: Color) = (yukiVector(mood, tint).root as VectorGroup).filterIsInstance<VectorPath>()

  @Test
  fun everyPoseParses() {
    for (mood in Mood.entries) {
      val paths = paths(mood, Color.Red)
      assertTrue("$mood has paths", paths.size > 10)
      assertTrue("$mood: every path has nodes", paths.all { it.pathData.isNotEmpty() })
    }
  }

  @Test
  fun scarfTakesTheTint() {
    val tint = Color(0xFF2B4FD8)
    for (mood in Mood.entries) {
      assertTrue("$mood uses the tint", paths(mood, tint).any { (it.fill as? SolidColor)?.value == tint })
    }
  }
}
