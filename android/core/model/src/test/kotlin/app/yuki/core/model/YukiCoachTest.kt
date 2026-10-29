package app.yuki.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YukiCoachTest {
  private fun status(streak: Int = 5, studied: Boolean = false, due: Int = 20, hour: Int = 10, lines: Boolean = true) =
    DayStatus(streak, studied, due, hour, lines)

  @Test
  fun moods() {
    assertEquals(Mood.WAVE, YukiCoach.line(status(lines = false)).mood)
    assertEquals(Mood.WAVE, YukiCoach.line(status()).mood)
    assertEquals(Mood.WORRIED, YukiCoach.line(status(hour = 20)).mood)
    assertEquals(Mood.CELEBRATE, YukiCoach.line(status(studied = true)).mood)
    assertEquals(Mood.SLEEPY, YukiCoach.line(status(studied = true, hour = 23)).mood)
    assertEquals(Mood.SLEEPY, YukiCoach.line(status(streak = 0, due = 0)).mood)
  }

  @Test
  fun worriedLineMentionsTheStreak() {
    assertTrue("5-day" in YukiCoach.line(status(hour = 21)).message)
    // No streak yet in the evening: encourage, don't worry.
    assertEquals(Mood.WAVE, YukiCoach.line(status(streak = 0, hour = 21)).mood)
  }
}
