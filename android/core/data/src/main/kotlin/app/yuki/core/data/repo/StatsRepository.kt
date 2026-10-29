package app.yuki.core.data.repo

import app.yuki.core.data.DatabaseHolder
import app.yuki.core.model.CardState
import app.yuki.core.model.Lang
import app.yuki.core.scheduler.StudyDay
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Everything on the stats screen (PRD S12), computed locally from the review log. */
data class Stats(
  val today: LocalDate,
  /** Scheduled reviews per study day, last 53 weeks. */
  val daily: Map<LocalDate, Int>,
  val currentStreak: Int,
  val longestStreak: Int,
  val totalReviews: Int,
  val totalMinutes: Int,
  val studyDays: Int,
  /** Recall on mature cards (started in Review), last 30 days; null under 10 reviews. */
  val retention30: Double?,
  val matureReviews30: Int,
  /** Reviews due per day for the next 30 days; overdue cards count on day 0. */
  val forecast: List<Int>,
  val reviewsByLang: Map<Lang, Int>,
)

class StatsRepository(
  private val holder: DatabaseHolder,
  private val clock: Clock,
  private val studyDay: () -> StudyDay,
) {
  suspend fun load(): Stats = withContext(Dispatchers.Default) {
    val now = clock.instant()
    val day = studyDay()
    val today = day.dateOf(now)
    val db = holder.db
    val rows = db.reviewLogDao().statRowsSince(now.minus(Duration.ofDays(371)))
    val daily = rows.groupingBy { day.dateOf(it.reviewedAt) }.eachCount()

    val dates = daily.keys.sorted()
    var longest = 0
    var run = 0
    var prev: LocalDate? = null
    for (d in dates) {
      run = if (prev != null && prev.plusDays(1) == d) run + 1 else 1
      longest = maxOf(longest, run)
      prev = d
    }
    var cursor = if (today in daily) today else today.minusDays(1)
    var current = 0
    while (cursor in daily) {
      current++
      cursor = cursor.minusDays(1)
    }

    val since30 = now.minus(Duration.ofDays(30))
    val mature = rows.filter { it.reviewedAt >= since30 && it.beforeState == CardState.REVIEW }
    val forecast = IntArray(30)
    val horizon = day.end(now).plus(Duration.ofDays(29))
    for (due in db.cardDao().duesBefore(horizon)) {
      val offset = (day.dateOf(due).toEpochDay() - today.toEpochDay()).coerceAtLeast(0)
      if (offset < 30) forecast[offset.toInt()]++
    }

    Stats(
      today = today,
      daily = daily,
      currentStreak = current,
      longestStreak = maxOf(longest, current),
      totalReviews = rows.size,
      totalMinutes = (rows.sumOf { it.durationMs.coerceAtMost(MAX_COUNTED_MS) } / 60_000).toInt(),
      studyDays = daily.size,
      retention30 = if (mature.size < 10) null else mature.count { it.rating > 1 }.toDouble() / mature.size,
      matureReviews30 = mature.size,
      forecast = forecast.toList(),
      reviewsByLang = rows.groupingBy { it.lang }.eachCount().mapKeys { Lang.fromCode(it.key) },
    )
  }

  private companion object {
    /** A card left open for ages counts as one minute, so time studied stays honest. */
    const val MAX_COUNTED_MS = 60_000L
  }
}
