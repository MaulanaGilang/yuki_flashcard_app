package app.yuki.core.data.repo

import app.yuki.core.data.DatabaseHolder
import app.yuki.core.data.db.CardEntity
import app.yuki.core.data.db.CardSnapshot
import app.yuki.core.data.db.DeckConfigEntity
import app.yuki.core.data.db.ReviewKind
import app.yuki.core.data.db.ReviewLogEntity
import app.yuki.core.data.pack.PackRegistry
import app.yuki.core.model.CardType
import app.yuki.core.model.DeckConfig
import app.yuki.core.model.Lang
import app.yuki.core.model.Rating
import app.yuki.core.model.StudyCard
import app.yuki.core.model.WordDetail
import app.yuki.core.scheduler.Answer
import app.yuki.core.scheduler.Fsrs
import app.yuki.core.scheduler.Pace
import app.yuki.core.scheduler.PaceGuard
import app.yuki.core.scheduler.SessionEngine
import app.yuki.core.scheduler.SessionPlan
import app.yuki.core.scheduler.StudyDay
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** One checkpoint question: pick the meaning of [word] among [options]. */
data class QuizQuestion(val word: app.yuki.core.model.Word, val options: List<String>, val answer: Int)

/** A running session: the pure engine plus what is needed to persist its answers. */
class StudySession(val id: String, val lang: Lang, val engine: SessionEngine, val total: Int)

class StudyRepository(
  private val holder: DatabaseHolder,
  private val packs: PackRegistry,
  private val clock: Clock,
  private val studyDay: () -> StudyDay,
) {
  suspend fun deckConfig(lang: Lang): DeckConfig {
    val e = holder.db.deckConfigDao().get(lang.code) ?: DeckConfigEntity(lang.code)
    return DeckConfig(
      lang = lang,
      newPerDay = e.newPerDay,
      maxReviews = e.maxReviews,
      desiredRetention = e.desiredRetention,
      learningStepsSeconds = e.learningSteps.split(',').filter { it.isNotBlank() }.map { it.trim().toLong() },
      relearningStepsSeconds = e.relearningSteps.split(',').filter { it.isNotBlank() }.map { it.trim().toLong() },
      showReadingOnFront = e.showReadingOnFront,
      autoplayFront = e.autoplayFront,
      listening = e.listening,
      production = e.production,
    )
  }

  private val _configChanges = kotlinx.coroutines.flow.MutableStateFlow(0)

  /** Bumps whenever a deck's settings change (card types change due counts). */
  val configChanges: kotlinx.coroutines.flow.StateFlow<Int> = _configChanges

  suspend fun saveDeckConfig(config: DeckConfig) {
    _configChanges.value++
    val dao = holder.db.deckConfigDao()
    val current = dao.get(config.lang.code) ?: DeckConfigEntity(config.lang.code)
    dao.upsert(
      current.copy(
        newPerDay = config.newPerDay,
        maxReviews = config.maxReviews,
        desiredRetention = config.desiredRetention,
        showReadingOnFront = config.showReadingOnFront,
        autoplayFront = config.autoplayFront,
        listening = config.listening,
        production = config.production,
      ),
    )
  }

  fun fsrsFor(config: DeckConfig) = Fsrs(
    desiredRetention = config.desiredRetention,
    learningSteps = config.learningStepsSeconds.map(Duration::ofSeconds),
    relearningSteps = config.relearningStepsSeconds.map(Duration::ofSeconds),
  )

  /** Today's new-card allowance after the pace guard (PRD S18). */
  suspend fun pace(lang: Lang, dueToday: Int? = null): Pace {
    val now = clock.instant()
    val day = studyDay()
    val config = deckConfig(lang)
    val db = holder.db
    val week = db.reviewLogDao().retention(lang.code, now.minus(Duration.ofDays(7)))
    val due = dueToday ?: db.cardDao().dueCount(lang.code, day.end(now), config.typeNames)
    return PaceGuard.adjust(config.newPerDay, week.total, week.correct, due, config.maxReviews)
  }

  suspend fun startSession(lang: Lang): StudySession {
    packs.awaitReady()
    val now = clock.instant()
    val day = studyDay()
    val end = day.end(now)
    val config = deckConfig(lang)
    val db = holder.db
    val introduced = db.reviewLogDao().introducedSince(lang.code, day.start(now))
    val allowance = (pace(lang).newCards - introduced).coerceAtLeast(0)
    val types = config.typeNames
    // Over-fetch new cards: some are buried siblings of words already on today's plan.
    val candidates = db.cardDao().dueBefore(lang.code, end, types) + db.cardDao().nextNew(lang.code, allowance * 3 + 10, types)
    val reviewedToday = db.reviewLogDao().cardsReviewedSince(lang.code, day.start(now)).toSet()
    val plan = SessionPlan.build(candidates.map { it.toStudyCard() }, end, config.maxReviews, allowance, reviewedToday)
    val engine = SessionEngine(fsrsFor(config), plan, end)
    return StudySession(UUID.randomUUID().toString(), lang, engine, plan.reviews.size + plan.learning.size + plan.new.size)
  }

  /** Persists one answer (log + card, one transaction). Returns the log id for undo. */
  suspend fun persist(session: StudySession, answer: Answer, durationMs: Long): Long {
    val db = holder.db
    val entity = db.cardDao().get(answer.card.id) ?: error("card ${answer.card.id} missing")
    val log = ReviewLogEntity(
      cardId = answer.card.id,
      lang = session.lang.code,
      rating = answer.rating.value,
      reviewedAt = answer.answeredAt,
      durationMs = durationMs,
      beforeState = answer.before.state,
      before = CardSnapshot.of(answer.before),
      after = CardSnapshot.of(answer.after),
      sessionId = session.id,
      kind = ReviewKind.SCHEDULED,
    )
    return db.studyDao().applyAnswer(log, entity.withMemory(answer.after))
  }

  /**
   * Logs one answer of the "review these" extra round (PRD S7). The card's memory is left as it
   * is: the round never affects scheduling, and stats skip `EXTRA_ROUND` rows.
   */
  suspend fun logExtra(session: StudySession, card: StudyCard, rating: Rating, at: Instant, durationMs: Long) {
    val snapshot = CardSnapshot.of(card.memory)
    holder.db.reviewLogDao().insert(
      ReviewLogEntity(
        cardId = card.id, lang = session.lang.code, rating = rating.value, reviewedAt = at, durationMs = durationMs,
        beforeState = card.memory.state, before = snapshot, after = snapshot, sessionId = session.id, kind = ReviewKind.EXTRA_ROUND,
      ),
    )
  }

  /**
   * A checkpoint quiz for one level (PRD §3.5): [count] random words, each with its meaning and
   * three meanings of other words from the same level.
   */
  suspend fun checkpoint(lang: Lang, level: Int, count: Int = 20, random: kotlin.random.Random = kotlin.random.Random.Default): List<QuizQuestion> {
    packs.awaitReady()
    val reader = packs.pack(lang)?.reader ?: return emptyList()
    val words = reader.wordsByLevel(level)
    val glosses = reader.firstGlosses(words.map { it.id }).mapValues { (_, g) -> g.substringBefore(';').trim() }
    val usable = words.filter { !glosses[it.id].isNullOrBlank() }
    val distinctGlosses = usable.mapNotNull { glosses[it.id] }.distinct()
    if (distinctGlosses.size < 4) return emptyList()
    return usable.shuffled(random).distinctBy { glosses[it.id] }.take(count).map { w ->
      val right = glosses.getValue(w.id)
      val options = (distinctGlosses.filter { it != right }.shuffled(random).take(3) + right).shuffled(random)
      QuizQuestion(w, options, options.indexOf(right))
    }
  }

  /** Onboarding (PRD S10): every still-new word below [level] is marked known. */
  suspend fun markKnownBelowLevel(lang: Lang, level: Int) = holder.db.cardDao().markKnownBelow(lang.code, level * 100_000)

  /** Checkpoint pass (PRD §3.5): the rest of [level] is marked known. */
  suspend fun markLevelKnown(lang: Lang, level: Int) = holder.db.cardDao().markKnownBetween(lang.code, level * 100_000, (level + 1) * 100_000)

  suspend fun revert(answer: Answer, logId: Long) {
    val db = holder.db
    val entity = db.cardDao().get(answer.card.id) ?: return
    db.studyDao().revertAnswer(logId, entity.withMemory(answer.before))
  }

  suspend fun detail(wordId: String): WordDetail? {
    packs.awaitReady()
    return packs.packForWord(wordId)?.reader?.detail(wordId)
  }

  /**
   * Word of the day for the widget: one of the words you're learning but haven't mastered,
   * stable for the whole study day; the next new word when nothing is in progress yet.
   */
  suspend fun wordOfTheDay(): WordDetail? {
    val db = holder.db
    val day = studyDay().dateOf(clock.instant())
    val cards = db.cardDao().learningForWidget()
    val card = if (cards.isNotEmpty()) cards[(day.toEpochDay() % cards.size).toInt()] else {
      packs.packs.value.firstNotNullOfOrNull { db.cardDao().nextNew(it.lang.code, 1, listOf(CardType.RECOGNITION.name)).firstOrNull() }
    } ?: return null
    return detail(card.wordId)
  }

  suspend fun details(wordIds: Collection<String>): Map<String, WordDetail> =
    wordIds.mapNotNull { id -> detail(id)?.let { id to it } }.toMap()
}

internal val DeckConfig.typeNames: List<String> get() = cardTypes.map { it.name }

internal fun CardEntity.toStudyCard() = StudyCard(id, wordId, Lang.fromCode(lang), type, memory, newRank)
