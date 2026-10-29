package app.yuki.core.scheduler

/** Where you are on a level ("station"), see PRD S18. */
enum class StationState { NOT_STARTED, RIDING, ARRIVED, MASTERED }

/** Per-level counts used to decide the station state. */
data class LevelStats(
  val total: Int,
  /** Words seen at least once, or marked as known. */
  val introduced: Int,
  /** Words whose card has graduated to the Review state. */
  val inReview: Int,
  /** Words with stability ≥ 21 days. */
  val mastered: Int,
  /** Scheduled reviews of this level's mature cards in the last 30 days. */
  val recentReviews: Int,
  val recentCorrect: Int,
) {
  val recentRetention: Double? get() = if (recentReviews >= Progression.MIN_REVIEWS) recentCorrect.toDouble() / recentReviews else null
}

/**
 * When is a level "done"? Yuki never blocks you: new words always continue in level order.
 * These rules decide what the route shows and when the next station is announced.
 *
 * - RIDING: some of the level's words have been introduced.
 * - ARRIVED: every word introduced, ≥ 80 % already in Review, and recall on this level's
 *   reviews over 30 days ≥ 85 % (when there are enough reviews to tell).
 * - MASTERED: ≥ 90 % of the level's words are remembered for 3+ weeks (stability ≥ 21 days).
 */
object Progression {
  const val ARRIVE_REVIEW_SHARE = 0.80
  const val ARRIVE_RETENTION = 0.85
  const val MASTER_SHARE = 0.90
  const val MIN_REVIEWS = 20

  fun state(s: LevelStats): StationState {
    if (s.total == 0 || s.introduced == 0) return StationState.NOT_STARTED
    if (s.mastered >= MASTER_SHARE * s.total) return StationState.MASTERED
    val allIntroduced = s.introduced >= s.total
    val enoughInReview = s.inReview >= ARRIVE_REVIEW_SHARE * s.total
    val recallOk = s.recentRetention?.let { it >= ARRIVE_RETENTION } ?: true
    return if (allIntroduced && enoughInReview && recallOk) StationState.ARRIVED else StationState.RIDING
  }
}

enum class PaceReason { STRUGGLING, PAUSED, BACKLOG }

/** Today's new-card allowance after the pace guard. [reason] is null when nothing was held back. */
data class Pace(val newCards: Int, val reason: PaceReason?, val retention: Double?)

/**
 * Pace guard (PRD S18): moving on is only useful while earlier words still stick. When recall on
 * mature cards over the last 7 days drops, Yuki introduces fewer new words until it recovers.
 */
object PaceGuard {
  const val MIN_REVIEWS = 30
  const val SLOW_BELOW = 0.80
  const val PAUSE_BELOW = 0.70

  fun adjust(baseNew: Int, reviews7d: Int, correct7d: Int, dueToday: Int, maxReviews: Int): Pace {
    val retention = if (reviews7d >= MIN_REVIEWS) correct7d.toDouble() / reviews7d else null
    return when {
      retention != null && retention < PAUSE_BELOW -> Pace(0, PaceReason.PAUSED, retention)
      retention != null && retention < SLOW_BELOW -> Pace(baseNew / 2, PaceReason.STRUGGLING, retention)
      dueToday > maxReviews -> Pace(baseNew / 2, PaceReason.BACKLOG, retention)
      else -> Pace(baseNew, null, retention)
    }
  }
}
