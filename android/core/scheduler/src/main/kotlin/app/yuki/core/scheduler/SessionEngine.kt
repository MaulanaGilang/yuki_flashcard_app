package app.yuki.core.scheduler

import app.yuki.core.model.CardState
import app.yuki.core.model.Memory
import app.yuki.core.model.Rating
import app.yuki.core.model.StudyCard
import java.time.Duration
import java.time.Instant

/** What goes into one study session for one language. */
data class SessionPlan(
  val reviews: List<StudyCard>,
  val learning: List<StudyCard>,
  val new: List<StudyCard>,
) {
  val isEmpty: Boolean get() = reviews.isEmpty() && learning.isEmpty() && new.isEmpty()

  companion object {
    /**
     * Picks today's cards from every non-suspended card of a language.
     * Reviews: due before the end of the study day, oldest first, capped at [maxReviews].
     * New: the next cards by `newRank`, up to the remaining new-card allowance for today.
     */
    fun build(
      cards: List<StudyCard>,
      endOfDay: Instant,
      maxReviews: Int,
      newAllowance: Int,
    ): SessionPlan {
      val reviews = cards
        .filter { (it.memory.state == CardState.REVIEW || it.memory.state == CardState.RELEARNING) && it.memory.due!! < endOfDay }
        .sortedBy { it.memory.due }
        .take(maxReviews)
      val learning = cards
        .filter { it.memory.state == CardState.LEARNING && it.memory.due!! < endOfDay }
        .sortedBy { it.memory.due }
      val new = cards
        .filter { it.memory.state == CardState.NEW }
        .sortedBy { it.newRank }
        .take(newAllowance.coerceAtLeast(0))
      return SessionPlan(reviews, learning, new)
    }
  }
}

/** One answered card: enough to persist it and to undo it. */
data class Answer(
  val card: StudyCard,
  val rating: Rating,
  val before: Memory,
  val after: Memory,
  val answeredAt: Instant,
)

/**
 * Pure session state machine: decides the next card, applies FSRS on answers, re-queues
 * learning cards within the session and supports undo. Persisting answers is the caller's job.
 */
class SessionEngine(
  private val fsrs: Fsrs,
  plan: SessionPlan,
  private val endOfDay: Instant,
  private val learnAhead: Duration = Duration.ofMinutes(20),
) {
  private var main = ArrayDeque(interleave(plan.reviews, plan.new))
  private var pending = plan.learning.sortedBy { it.memory.due }.toMutableList()
  private var current: StudyCard? = null
  private var answered = 0
  private val history = ArrayDeque<Snapshot>()

  private data class Snapshot(
    val main: List<StudyCard>,
    val pending: List<StudyCard>,
    val current: StudyCard?,
    val answered: Int,
    val answer: Answer,
  )

  val answeredCount: Int get() = answered

  /** Cards still to show, counting each queued learning card once. */
  val remaining: Int get() = main.size + pending.size + if (current != null) 1 else 0

  /** The card to show now, or null when the session is finished. Stable until answered. */
  fun current(now: Instant): StudyCard? {
    current?.let { return it }
    val firstPending = pending.firstOrNull()
    current = when {
      firstPending != null && firstPending.memory.due!! <= now -> pending.removeAt(0)
      main.isNotEmpty() -> main.removeFirst()
      firstPending != null && firstPending.memory.due!! <= now.plus(learnAhead) -> pending.removeAt(0)
      else -> null
    }
    return current
  }

  fun preview(now: Instant): Map<Rating, Instant>? = current?.let { fsrs.preview(it.memory, now) }

  fun answer(rating: Rating, now: Instant): Answer {
    val card = checkNotNull(current) { "no current card" }
    val after = fsrs.review(card.memory, rating, now)
    val answer = Answer(card, rating, card.memory, after, now)
    history.addLast(Snapshot(main.toList(), pending.toList(), card, answered, answer))
    answered++
    current = null
    if ((after.state == CardState.LEARNING || after.state == CardState.RELEARNING) && after.due!! < endOfDay) {
      pending.add(card.copy(memory = after))
      pending.sortBy { it.memory.due }
    }
    return answer
  }

  /** Reverts the last answer and makes that card current again. Returns it, or null. */
  fun undo(): Answer? {
    val snap = history.removeLastOrNull() ?: return null
    main = ArrayDeque(snap.main)
    pending = snap.pending.toMutableList()
    current = snap.current
    answered = snap.answered
    return snap.answer
  }

  companion object {
    const val REVIEWS_PER_NEW = 4

    /** One new card after every [REVIEWS_PER_NEW] reviews; leftovers go at the end. */
    fun interleave(reviews: List<StudyCard>, new: List<StudyCard>): List<StudyCard> {
      if (reviews.isEmpty()) return new
      val out = ArrayList<StudyCard>(reviews.size + new.size)
      var n = 0
      reviews.forEachIndexed { i, card ->
        out.add(card)
        if ((i + 1) % REVIEWS_PER_NEW == 0 && n < new.size) out.add(new[n++])
      }
      while (n < new.size) out.add(new[n++])
      return out
    }
  }
}
