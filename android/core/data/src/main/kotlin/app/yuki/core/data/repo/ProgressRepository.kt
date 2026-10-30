package app.yuki.core.data.repo

import app.yuki.core.data.DatabaseHolder
import app.yuki.core.data.db.CardStatusRow
import app.yuki.core.data.pack.PackRegistry
import app.yuki.core.data.settings.AppSettings
import app.yuki.core.model.CardState
import app.yuki.core.model.FoilTier
import app.yuki.core.model.Lang
import app.yuki.core.model.Word
import app.yuki.core.model.WordStatus
import app.yuki.core.scheduler.LevelStats
import app.yuki.core.scheduler.Pace
import app.yuki.core.scheduler.Progression
import app.yuki.core.scheduler.StationState
import app.yuki.core.scheduler.StudyDay
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

data class LevelProgress(
  val level: Int,
  val label: String,
  val total: Int,
  val started: Int,
  val inReview: Int,
  val mastered: Int,
  val state: StationState,
  val recentRetention: Double?,
) {
  val masteredFraction: Float get() = if (total == 0) 0f else mastered.toFloat() / total
}

data class LineSummary(
  val lang: Lang,
  val levels: List<LevelProgress>,
  val dueToday: Int,
  val newToday: Int,
  val pace: Pace,
) {
  /** The station you're "at": the first level you haven't arrived at yet. */
  val currentLevel: LevelProgress?
    get() = levels.firstOrNull { it.state == StationState.RIDING || it.state == StationState.NOT_STARTED } ?: levels.lastOrNull()

  /** The level you most recently arrived at, if its successor hasn't been reached yet. */
  val justArrived: LevelProgress?
    get() = levels.lastOrNull { it.state == StationState.ARRIVED || it.state == StationState.MASTERED }
}

data class LibraryRow(val word: Word, val gloss: String, val status: WordStatus)

/** Read models for Today and Library: progress rows joined with pack content in memory. */
@OptIn(ExperimentalCoroutinesApi::class)
class ProgressRepository(
  private val holder: DatabaseHolder,
  private val packs: PackRegistry,
  private val study: StudyRepository,
  private val clock: Clock,
  private val studyDay: () -> StudyDay,
  private val settings: AppSettings,
) {
  /** Lines on Today: installed packs, limited to the languages picked in onboarding. */
  fun observeLines(): Flow<List<LineSummary>> = combine(packs.packs, holder.version, settings.changes, study.configChanges) { installed, _, _, _ -> installed }
    .flatMapLatest { all ->
      val active = settings.activeLangs()
      val installed = if (active == null) all else all.filter { it.lang in active }
      if (installed.isEmpty()) return@flatMapLatest flowOf(emptyList())
      val now = clock.instant()
      val day = studyDay()
      val flows = installed.map { pack ->
        val lang = pack.lang
        val keys = pack.reader.wordKeys().associate { it.id to it.level }
        val labels = pack.reader.levels()
        val db = holder.db
        val types = study.deckConfig(lang).typeNames
        combine(
          db.cardDao().observeStatuses(lang.code),
          db.cardDao().observeDueCount(lang.code, day.end(now), types),
          db.reviewLogDao().observeIntroducedSince(lang.code, day.start(now)),
          db.reviewLogDao().observeMatureRatings(lang.code, now.minus(java.time.Duration.ofDays(30))),
        ) { statuses, due, introduced, ratings ->
          val byLevel = statuses.groupBy { keys[it.wordId] ?: 0 }
          val ratingsByLevel = ratings.groupBy { keys[it.cardId.substringBefore('#')] ?: 0 }
          val levels = labels.map { l ->
            val rows = byLevel[l.level].orEmpty()
            val recent = ratingsByLevel[l.level].orEmpty()
            val stats = LevelStats(
              total = l.wordCount,
              introduced = rows.count { it.state != CardState.NEW || it.markedKnown },
              inReview = rows.count { it.state == CardState.REVIEW || it.markedKnown },
              mastered = rows.count { it.isMastered() || it.markedKnown },
              recentReviews = recent.size,
              recentCorrect = recent.count { it.rating > 1 },
            )
            LevelProgress(
              level = l.level, label = l.label, total = l.wordCount,
              started = stats.introduced, inReview = stats.inReview, mastered = stats.mastered,
              state = Progression.state(stats), recentRetention = stats.recentRetention,
            )
          }
          val pace = study.pace(lang, due)
          val unstarted = levels.sumOf { it.total - it.started }
          LineSummary(lang, levels, due, (pace.newCards - introduced).coerceIn(0, unstarted), pace)
        }
      }
      combine(flows) { lines -> lines.sortedBy { it.lang.ordinal } }
    }

  /** Consecutive study days ending today (or yesterday, if today has no reviews yet). */
  fun observeStreak(): Flow<Int> = combine(holder.version, packs.packs) { _, _ -> }.flatMapLatest {
    holder.db.reviewLogDao().observeRecentTimes().map { times -> streak(times, studyDay(), clock.instant()) }
  }

  /** True once any scheduled review has happened in today's study day. */
  fun observeStudiedToday(): Flow<Boolean> = combine(holder.version, packs.packs) { _, _ -> }.flatMapLatest {
    holder.db.reviewLogDao().observeCountSince(studyDay().start(clock.instant())).map { it > 0 }
  }

  /** One-shot snapshot for the streak widget (PRD S14/S19). */
  suspend fun dayStatus(): app.yuki.core.model.DayStatus {
    packs.awaitReady()
    val now = clock.instant()
    val day = studyDay()
    val db = holder.db
    val due = packs.packs.value.sumOf { p ->
      val pace = study.pace(p.lang)
      val introduced = db.reviewLogDao().introducedSince(p.lang.code, day.start(now))
      db.cardDao().dueCount(p.lang.code, day.end(now), study.deckConfig(p.lang).typeNames) + (pace.newCards - introduced).coerceAtLeast(0)
    }
    return app.yuki.core.model.DayStatus(
      streak = streak(db.reviewLogDao().recentTimes(), day, now),
      studiedToday = db.reviewLogDao().countSince(day.start(now)) > 0,
      dueToday = due,
      hour = now.atZone(java.time.ZoneId.systemDefault()).hour,
      hasLines = packs.packs.value.isNotEmpty(),
    )
  }

  suspend fun browse(lang: Lang, level: Int): List<LibraryRow> {
    packs.awaitReady()
    val pack = packs.pack(lang) ?: return emptyList()
    return rows(pack.reader.wordsByLevel(level), lang)
  }

  suspend fun search(lang: Lang, query: String): List<LibraryRow> {
    packs.awaitReady()
    val pack = packs.pack(lang) ?: return emptyList()
    return rows(pack.reader.search(query), lang)
  }

  /** Any review ever logged (an existing user skips onboarding). */
  suspend fun hasHistory(): Boolean = holder.db.reviewLogDao().count() > 0

  suspend fun levels(lang: Lang) = packs.pack(lang)?.reader?.levels().orEmpty()

  suspend fun setSuspended(wordId: String, suspended: Boolean) = holder.db.cardDao().setSuspended(wordId, suspended)

  suspend fun setMarkedKnown(wordId: String, known: Boolean) = holder.db.cardDao().setMarkedKnown(wordId, known)

  suspend fun cardsForWord(wordId: String) = holder.db.cardDao().forWord(wordId)

  /** The word's main (recognition) card, which drives its status, foil and due date. */
  suspend fun mainCard(wordId: String) = cardsForWord(wordId).firstOrNull { it.type == app.yuki.core.model.CardType.RECOGNITION }

  /**
   * "Reset progress" (PRD S9): every card of the word goes back to new. A MANUAL_RESET log row
   * per card keeps the history honest; stats only count SCHEDULED rows.
   */
  suspend fun resetWord(wordId: String) {
    val db = holder.db
    val now = clock.instant()
    db.cardDao().forWord(wordId).forEach { card ->
      db.studyDao().applyAnswer(
        app.yuki.core.data.db.ReviewLogEntity(
          cardId = card.id, lang = card.lang, rating = 0, reviewedAt = now, durationMs = 0,
          beforeState = card.state, before = app.yuki.core.data.db.CardSnapshot.of(card.memory),
          after = app.yuki.core.data.db.CardSnapshot.of(app.yuki.core.model.Memory.NEW),
          sessionId = "manual-reset", kind = app.yuki.core.data.db.ReviewKind.MANUAL_RESET,
        ),
        card.withMemory(app.yuki.core.model.Memory.NEW).copy(markedKnown = false),
      )
    }
  }

  /** "Study now" (PRD S9): due today if already learned; otherwise the next new card. */
  suspend fun studyNow(wordId: String) {
    val dao = holder.db.cardDao()
    dao.makeDue(wordId, clock.instant())
    dao.moveToFrontOfNew(wordId)
  }

  /** Word ids for the Library's history filters (PRD S8). */
  suspend fun wordsRatedAgainLastWeek(lang: Lang): Set<String> =
    holder.db.reviewLogDao().cardsRatedAgainSince(lang.code, clock.instant().minus(java.time.Duration.ofDays(7))).map { it.substringBefore('#') }.toSet()

  suspend fun wordsStudiedToday(lang: Lang): Set<String> =
    holder.db.reviewLogDao().cardsReviewedSince(lang.code, studyDay().start(clock.instant())).map { it.substringBefore('#') }.toSet()

  suspend fun historyForWord(wordId: String) =
    holder.db.cardDao().forWord(wordId).flatMap { holder.db.reviewLogDao().forCard(it.id) }.sortedBy { it.reviewedAt }

  private suspend fun rows(words: List<Word>, lang: Lang): List<LibraryRow> {
    val pack = packs.pack(lang) ?: return emptyList()
    val glosses = pack.reader.firstGlosses(words.map { it.id })
    val statuses = holder.db.cardDao().forWordsStatus(words.map { it.id })
    return words.map { w -> LibraryRow(w, glosses[w.id].orEmpty(), statuses[w.id].toStatus()) }
  }

  companion object {
    fun streak(times: List<Instant>, day: StudyDay, now: Instant): Int {
      val dates = times.map(day::dateOf).toSortedSet().reversed()
      val today = day.dateOf(now)
      var cursor: LocalDate = if (today in dates) today else today.minusDays(1)
      var count = 0
      while (cursor in dates) {
        count++
        cursor = cursor.minusDays(1)
      }
      return count
    }
  }
}

private fun CardStatusRow.isMastered() = state == CardState.REVIEW && (stability ?: 0.0) >= FoilTier.MASTERED_DAYS

internal fun CardStatusRow?.toStatus(): WordStatus = when {
  this == null -> WordStatus.NEW
  suspended -> WordStatus.SUSPENDED
  isMastered() -> WordStatus.MASTERED
  state == CardState.REVIEW -> WordStatus.REVIEW
  state == CardState.NEW && !markedKnown -> WordStatus.NEW
  state == CardState.NEW -> WordStatus.MASTERED
  else -> WordStatus.LEARNING
}
