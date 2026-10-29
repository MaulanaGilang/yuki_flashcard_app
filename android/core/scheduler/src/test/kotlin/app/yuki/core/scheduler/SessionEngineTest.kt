package app.yuki.core.scheduler

import app.yuki.core.model.CardState
import app.yuki.core.model.CardType
import app.yuki.core.model.FoilTier
import app.yuki.core.model.Lang
import app.yuki.core.model.Memory
import app.yuki.core.model.Rating
import app.yuki.core.model.StudyCard
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionEngineTest {
  private val now = Instant.parse("2026-10-29T10:00:00Z")
  private val endOfDay = Instant.parse("2026-10-29T21:00:00Z")
  private val fsrs = Fsrs(enableFuzzing = false)

  private fun newCard(i: Int) = StudyCard("n$i", "w$i", Lang.ZH, CardType.RECOGNITION, Memory.NEW, newRank = i)

  private fun reviewCard(i: Int, dueHoursAgo: Long) = StudyCard(
    "r$i", "rw$i", Lang.ZH, CardType.RECOGNITION,
    Memory(CardState.REVIEW, null, 10.0, 5.0, now.minus(Duration.ofHours(dueHoursAgo)), now.minus(Duration.ofDays(10)), 3, 0),
    newRank = 1000 + i,
  )

  @Test
  fun interleavesOneNewAfterFourReviews() {
    val order = SessionEngine.interleave((1..8).map { reviewCard(it, 1) }, (1..3).map { newCard(it) }).map { it.id }
    assertEquals(listOf("r1", "r2", "r3", "r4", "n1", "r5", "r6", "r7", "r8", "n2", "n3"), order)
  }

  @Test
  fun planRespectsLimitsAndOrder() {
    val cards = (1..5).map { reviewCard(it, it.toLong()) } + (1..20).map { newCard(21 - it) }
    val plan = SessionPlan.build(cards, endOfDay, maxReviews = 3, newAllowance = 4)
    assertEquals(listOf("r5", "r4", "r3"), plan.reviews.map { it.id })
    assertEquals(listOf("n1", "n2", "n3", "n4"), plan.new.map { it.id })
  }

  @Test
  fun againOnNewCardComesBackLaterInSession() {
    val engine = SessionEngine(fsrs, SessionPlan(emptyList(), emptyList(), listOf(newCard(1), newCard(2))), endOfDay)
    assertEquals("n1", engine.current(now)!!.id)
    engine.answer(Rating.AGAIN, now)
    assertEquals("n2", engine.current(now)!!.id)
    engine.answer(Rating.EASY, now)
    // n1 is due in 1 minute: within learn-ahead, so it is shown instead of ending the session.
    val back = engine.current(now.plusSeconds(5))!!
    assertEquals("n1", back.id)
    assertEquals(CardState.LEARNING, back.memory.state)
    engine.answer(Rating.GOOD, now.plusSeconds(70))
    assertEquals("n1", engine.current(now.plusSeconds(800))!!.id)
    engine.answer(Rating.GOOD, now.plusSeconds(800))
    assertNull(engine.current(now.plus(Duration.ofHours(1))))
    assertEquals(4, engine.answeredCount)
  }

  @Test
  fun undoRestoresCardAndQueue() {
    val engine = SessionEngine(fsrs, SessionPlan(emptyList(), emptyList(), listOf(newCard(1), newCard(2))), endOfDay)
    engine.current(now)
    val answer = engine.answer(Rating.AGAIN, now)
    assertEquals(Memory.NEW, answer.before)
    val undone = engine.undo()!!
    assertEquals("n1", undone.card.id)
    assertEquals("n1", engine.current(now)!!.id)
    assertEquals(0, engine.answeredCount)
    assertEquals(2, engine.remaining)
    assertNull(engine.undo())
  }

  @Test
  fun previewOrdersIntervals() {
    val preview = fsrs.preview(reviewCard(1, 1).memory, now)
    assertTrue(preview[Rating.AGAIN]!! < preview[Rating.HARD]!!)
    assertTrue(preview[Rating.HARD]!! <= preview[Rating.GOOD]!!)
    assertTrue(preview[Rating.GOOD]!! < preview[Rating.EASY]!!)
  }

  @Test
  fun studyDayStartsAtFourAm() {
    val day = StudyDay(ZoneId.of("Asia/Jakarta"))
    // 01:30 WIB on 30 Oct still belongs to the 29 Oct study day.
    val lateNight = Instant.parse("2026-10-29T18:30:00Z")
    assertEquals(LocalDate.of(2026, 10, 29), day.dateOf(lateNight))
    assertEquals(Instant.parse("2026-10-29T21:00:00Z"), day.end(lateNight))
  }

  @Test
  fun foilTiers() {
    assertEquals(FoilTier.NEW, FoilTier.of(Memory.NEW))
    val review = reviewCard(1, 1).memory
    assertEquals(FoilTier.REVIEW, FoilTier.of(review))
    assertEquals(FoilTier.MASTERED, FoilTier.of(review.copy(stability = 30.0)))
    assertEquals(FoilTier.GOLD, FoilTier.of(review.copy(stability = 200.0)))
  }
}
