package app.yuki.core.scheduler

import app.yuki.core.model.CardState
import app.yuki.core.model.Memory
import app.yuki.core.model.Rating
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Replays reference histories produced by py-fsrs (`pipeline/tools/fsrs_vectors.py`). */
class FsrsVectorTest {

  @Serializable
  data class Vectors(val py_fsrs: String, val cases: List<Case>)

  @Serializable
  data class Case(val config: Config, val steps: List<Step>)

  @Serializable
  data class Config(
    val desired_retention: Double,
    val learning_steps: List<Long>,
    val relearning_steps: List<Long>,
    val maximum_interval: Int,
  )

  @Serializable
  data class Step(
    val rating: Int,
    val t: Long,
    val state: Int,
    val step: Int?,
    val stability: Double,
    val difficulty: Double,
    val due: Long,
  )

  private fun micros(us: Long): Instant = Instant.EPOCH.plus(us, ChronoUnit.MICROS)

  private fun Instant.toMicros(): Long = epochSecond * 1_000_000 + nano / 1_000

  private fun close(expected: Double, actual: Double) = abs(expected - actual) <= 1e-9 * maxOf(1.0, abs(expected))

  @Test
  fun matchesPyFsrsReference() {
    val text = javaClass.classLoader!!.getResource("fsrs_vectors.json")!!.readText()
    val vectors = Json { ignoreUnknownKeys = true }.decodeFromString<Vectors>(text)
    assertTrue("expected many cases", vectors.cases.size >= 1000)
    var steps = 0
    vectors.cases.forEachIndexed { caseIndex, case ->
      val fsrs = Fsrs(
        desiredRetention = case.config.desired_retention,
        learningSteps = case.config.learning_steps.map { Duration.ofSeconds(it) },
        relearningSteps = case.config.relearning_steps.map { Duration.ofSeconds(it) },
        maximumInterval = case.config.maximum_interval,
        enableFuzzing = false,
      )
      var memory = Memory.NEW
      case.steps.forEachIndexed { stepIndex, s ->
        memory = fsrs.review(memory, Rating.fromValue(s.rating), micros(s.t))
        val where = "case $caseIndex step $stepIndex"
        val expectedState = when (s.state) {
          1 -> CardState.LEARNING
          2 -> CardState.REVIEW
          3 -> CardState.RELEARNING
          else -> error("bad state ${s.state}")
        }
        assertEquals("$where state", expectedState, memory.state)
        assertEquals("$where step", s.step, memory.step)
        assertTrue("$where stability ${s.stability} vs ${memory.stability}", close(s.stability, memory.stability!!))
        assertTrue("$where difficulty ${s.difficulty} vs ${memory.difficulty}", close(s.difficulty, memory.difficulty!!))
        assertEquals("$where due", s.due, memory.due!!.toMicros())
        steps++
      }
    }
    println("FSRS vectors: ${vectors.cases.size} cases, $steps reviews matched py-fsrs ${vectors.py_fsrs}")
  }

  @Test
  fun fuzzStaysWithinRangeAndIsDeterministicWithInjectedRandom() {
    val now = Instant.parse("2026-10-29T08:00:00Z")
    val mature = Memory(CardState.REVIEW, null, 40.0, 5.0, now, now.minus(Duration.ofDays(40)), 8, 0)
    val plainDue = Fsrs(enableFuzzing = false).review(mature, Rating.GOOD, now).due!!
    val low = Fsrs(random = { 0.0 }).review(mature, Rating.GOOD, now).due!!
    val high = Fsrs(random = { 0.999 }).review(mature, Rating.GOOD, now).due!!
    assertTrue(low <= plainDue && plainDue <= high)
    val spreadDays = Duration.between(low, high).toDays()
    assertTrue("fuzz spread $spreadDays days", spreadDays in 1..20)
  }

  @Test
  fun lapsesCountOnlyFromReviewState() {
    val fsrs = Fsrs(enableFuzzing = false)
    var t = Instant.parse("2026-01-01T00:00:00Z")
    var m = fsrs.review(Memory.NEW, Rating.AGAIN, t)
    assertEquals(0, m.lapses)
    m = fsrs.review(m, Rating.EASY, t.plusSeconds(120).also { t = it })
    assertEquals(CardState.REVIEW, m.state)
    m = fsrs.review(m, Rating.AGAIN, m.due!!)
    assertEquals(CardState.RELEARNING, m.state)
    assertEquals(1, m.lapses)
    assertEquals(3, m.reps)
  }
}
