package app.yuki.core.scheduler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressionTest {
  private fun stats(introduced: Int, inReview: Int, mastered: Int = 0, reviews: Int = 0, correct: Int = 0) =
    LevelStats(total = 100, introduced = introduced, inReview = inReview, mastered = mastered, recentReviews = reviews, recentCorrect = correct)

  @Test
  fun stationStates() {
    assertEquals(StationState.NOT_STARTED, Progression.state(stats(0, 0)))
    assertEquals(StationState.RIDING, Progression.state(stats(40, 30)))
    // All introduced but only 70 % in review: still riding.
    assertEquals(StationState.RIDING, Progression.state(stats(100, 70)))
    assertEquals(StationState.ARRIVED, Progression.state(stats(100, 85, reviews = 100, correct = 90)))
    // Enough in review but recall only 80 %: not arrived yet.
    assertEquals(StationState.RIDING, Progression.state(stats(100, 85, reviews = 100, correct = 80)))
    // Too few reviews to judge recall: arrival goes by coverage alone.
    assertEquals(StationState.ARRIVED, Progression.state(stats(100, 85, reviews = 5, correct = 2)))
    assertEquals(StationState.MASTERED, Progression.state(stats(100, 95, mastered = 92)))
  }

  @Test
  fun paceGuard() {
    assertEquals(Pace(10, null, null), PaceGuard.adjust(10, reviews7d = 10, correct7d = 2, dueToday = 20, maxReviews = 200))
    val slow = PaceGuard.adjust(10, reviews7d = 100, correct7d = 76, dueToday = 20, maxReviews = 200)
    assertEquals(5, slow.newCards)
    assertEquals(PaceReason.STRUGGLING, slow.reason)
    assertEquals(PaceReason.PAUSED, PaceGuard.adjust(10, 100, 65, 20, 200).reason)
    assertEquals(0, PaceGuard.adjust(10, 100, 65, 20, 200).newCards)
    assertEquals(PaceReason.BACKLOG, PaceGuard.adjust(10, 100, 95, 250, 200).reason)
    assertNull(PaceGuard.adjust(10, 100, 95, 20, 200).reason)
  }
}
