package app.yuki.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/** Light projection for library badges and level mastery. */
data class CardStatusRow(
  val wordId: String,
  val state: app.yuki.core.model.CardState,
  val stability: Double?,
  val suspended: Boolean,
  val markedKnown: Boolean,
)

@Dao
abstract class CardDao {
  @Insert(onConflict = OnConflictStrategy.IGNORE)
  abstract suspend fun insertIgnore(cards: List<CardEntity>)

  @Update abstract suspend fun update(card: CardEntity)

  @Query("SELECT * FROM card WHERE id = :id")
  abstract suspend fun get(id: String): CardEntity?

  @Query("SELECT * FROM card WHERE wordId = :wordId")
  abstract suspend fun forWord(wordId: String): List<CardEntity>

  // `types` holds CardType names: the card directions switched on for the language (PRD S6).

  @Query(
    """SELECT * FROM card WHERE lang = :lang AND suspended = 0 AND markedKnown = 0 AND orphaned = 0
       AND type IN (:types) AND state != 'NEW' AND due < :endOfDay ORDER BY due""",
  )
  abstract suspend fun dueBefore(lang: String, endOfDay: Instant, types: List<String>): List<CardEntity>

  /**
   * Next new cards in introduction order. Listening and production cards of a word only become
   * available once its recognition card has graduated to Review.
   */
  @Query(
    """SELECT * FROM card AS c WHERE c.lang = :lang AND c.suspended = 0 AND c.markedKnown = 0 AND c.orphaned = 0
       AND c.type IN (:types) AND c.state = 'NEW'
       AND (c.type = 'RECOGNITION' OR EXISTS (
         SELECT 1 FROM card AS r WHERE r.wordId = c.wordId AND r.type = 'RECOGNITION' AND r.state = 'REVIEW'))
       ORDER BY c.newRank, c.type DESC LIMIT :limit""",
  )
  abstract suspend fun nextNew(lang: String, limit: Int, types: List<String>): List<CardEntity>

  @Query(
    """SELECT COUNT(*) FROM card WHERE lang = :lang AND suspended = 0 AND markedKnown = 0 AND orphaned = 0
       AND type IN (:types) AND state != 'NEW' AND due < :endOfDay""",
  )
  abstract fun observeDueCount(lang: String, endOfDay: Instant, types: List<String>): Flow<Int>

  @Query(
    """SELECT COUNT(*) FROM card WHERE lang = :lang AND suspended = 0 AND markedKnown = 0 AND orphaned = 0
       AND type IN (:types) AND state != 'NEW' AND due < :endOfDay""",
  )
  abstract suspend fun dueCount(lang: String, endOfDay: Instant, types: List<String>): Int

  /** Due dates of every scheduled card, for the review forecast (PRD S12). */
  @Query("SELECT due FROM card WHERE suspended = 0 AND markedKnown = 0 AND orphaned = 0 AND state != 'NEW' AND due < :until")
  abstract suspend fun duesBefore(until: Instant): List<Instant>

  /** Onboarding / checkpoint: skip every still-new card below [belowRank] (newRank = level * 100 000 + freq). */
  @Query("UPDATE card SET markedKnown = 1 WHERE lang = :lang AND state = 'NEW' AND newRank >= 0 AND newRank < :belowRank")
  abstract suspend fun markKnownBelow(lang: String, belowRank: Int)

  /** Checkpoint pass: every still-new card of one level becomes known. */
  @Query("UPDATE card SET markedKnown = 1 WHERE lang = :lang AND state = 'NEW' AND newRank >= :fromRank AND newRank < :toRank")
  abstract suspend fun markKnownBetween(lang: String, fromRank: Int, toRank: Int)

  /** "Study now" (PRD S9) for a word already in review: due right away. */
  @Query("UPDATE card SET due = :now WHERE wordId = :wordId AND state != 'NEW'")
  abstract suspend fun makeDue(wordId: String, now: Instant)

  /** "Study now" for a new word: first in line among new cards. */
  @Query("UPDATE card SET newRank = -1, markedKnown = 0, suspended = 0 WHERE wordId = :wordId AND state = 'NEW' AND type = 'RECOGNITION'")
  abstract suspend fun moveToFrontOfNew(wordId: String)

  /** A pack imported from file takes over its language's existing cards (stable word ids keep progress). */
  @Query("UPDATE card SET packId = :packId, orphaned = 0 WHERE wordId IN (:wordIds)")
  abstract suspend fun adopt(packId: String, wordIds: List<String>)

  @Query("UPDATE card SET orphaned = 1 WHERE lang = :lang AND packId != :packId")
  abstract suspend fun orphanOthers(lang: String, packId: String)

  @Query("SELECT wordId, state, stability, suspended, markedKnown FROM card WHERE lang = :lang AND type = 'RECOGNITION' AND orphaned = 0")
  abstract fun observeStatuses(lang: String): Flow<List<CardStatusRow>>

  @Query("SELECT wordId, state, stability, suspended, markedKnown FROM card WHERE type = 'RECOGNITION' AND wordId IN (:ids)")
  abstract suspend fun statusesFor(ids: List<String>): List<CardStatusRow>

  suspend fun forWordsStatus(ids: List<String>): Map<String, CardStatusRow> =
    ids.chunked(500).flatMap { statusesFor(it) }.associateBy { it.wordId }

  @Query("UPDATE card SET orphaned = 1 WHERE packId = :packId AND wordId NOT IN (:keep)")
  abstract suspend fun markOrphans(packId: String, keep: List<String>)

  @Query("UPDATE card SET suspended = :suspended WHERE wordId = :wordId")
  abstract suspend fun setSuspended(wordId: String, suspended: Boolean)

  @Query("UPDATE card SET markedKnown = :known WHERE wordId = :wordId")
  abstract suspend fun setMarkedKnown(wordId: String, known: Boolean)

  @Query("SELECT COUNT(*) FROM card")
  abstract suspend fun count(): Int

  /** Cards being learned but not yet mastered (stability < 21 days), for the widget. */
  @Query(
    """SELECT * FROM card WHERE type = 'RECOGNITION' AND suspended = 0 AND orphaned = 0
       AND state IN ('LEARNING', 'RELEARNING', 'REVIEW') AND (stability IS NULL OR stability < 21)
       ORDER BY due LIMIT 60""",
  )
  abstract suspend fun learningForWidget(): List<CardEntity>
}

/** Recall on mature cards: scheduled reviews that started in Review state, and how many weren't "Again". */
data class RetentionCounts(val total: Int, val correct: Int)

data class CardRating(val cardId: String, val rating: Int)

data class StatRow(val lang: String, val rating: Int, val reviewedAt: Instant, val durationMs: Long, val beforeState: app.yuki.core.model.CardState)


@Dao
abstract class ReviewLogDao {
  @Query(
    """SELECT COUNT(*) AS total, COALESCE(SUM(CASE WHEN rating > 1 THEN 1 ELSE 0 END), 0) AS correct
       FROM review_log WHERE lang = :lang AND reviewedAt >= :since AND beforeState = 'REVIEW' AND kind = 'SCHEDULED'""",
  )
  abstract suspend fun retention(lang: String, since: Instant): RetentionCounts

  @Query("SELECT cardId, rating FROM review_log WHERE lang = :lang AND reviewedAt >= :since AND beforeState = 'REVIEW' AND kind = 'SCHEDULED'")
  abstract fun observeMatureRatings(lang: String, since: Instant): Flow<List<CardRating>>

  @Insert abstract suspend fun insert(log: ReviewLogEntity): Long

  @Query("DELETE FROM review_log WHERE id = :id")
  abstract suspend fun delete(id: Long)

  @Query("SELECT COUNT(*) FROM review_log WHERE lang = :lang AND reviewedAt >= :since AND beforeState = 'NEW' AND kind = 'SCHEDULED'")
  abstract fun observeIntroducedSince(lang: String, since: Instant): Flow<Int>

  @Query("SELECT COUNT(*) FROM review_log WHERE lang = :lang AND reviewedAt >= :since AND beforeState = 'NEW' AND kind = 'SCHEDULED'")
  abstract suspend fun introducedSince(lang: String, since: Instant): Int

  @Query("SELECT COUNT(*) FROM review_log WHERE reviewedAt >= :since AND kind = 'SCHEDULED'")
  abstract fun observeCountSince(since: Instant): Flow<Int>

  @Query("SELECT COUNT(*) FROM review_log WHERE reviewedAt >= :since AND kind = 'SCHEDULED'")
  abstract suspend fun countSince(since: Instant): Int

  @Query("SELECT reviewedAt FROM review_log WHERE kind = 'SCHEDULED' ORDER BY reviewedAt DESC LIMIT 5000")
  abstract suspend fun recentTimes(): List<Instant>

  @Query("SELECT reviewedAt FROM review_log WHERE kind = 'SCHEDULED' ORDER BY reviewedAt DESC LIMIT 5000")
  abstract fun observeRecentTimes(): Flow<List<Instant>>

  /** Library filter "rated Again in the last 7 days" (PRD S8). */
  @Query("SELECT DISTINCT cardId FROM review_log WHERE lang = :lang AND reviewedAt >= :since AND rating = 1")
  abstract suspend fun cardsRatedAgainSince(lang: String, since: Instant): List<String>

  /** Cards reviewed since [since]; their siblings are buried for the rest of the day (PRD S6). */
  @Query("SELECT DISTINCT cardId FROM review_log WHERE lang = :lang AND reviewedAt >= :since AND kind = 'SCHEDULED'")
  abstract suspend fun cardsReviewedSince(lang: String, since: Instant): List<String>

  /** Every scheduled review since [since], light rows for stats (PRD S12). */
  @Query("SELECT lang, rating, reviewedAt, durationMs, beforeState FROM review_log WHERE reviewedAt >= :since AND kind = 'SCHEDULED'")
  abstract suspend fun statRowsSince(since: Instant): List<StatRow>

  @Query("SELECT MIN(reviewedAt) FROM review_log WHERE kind = 'SCHEDULED'")
  abstract suspend fun firstReviewAt(): Instant?

  @Query("SELECT * FROM review_log WHERE cardId = :cardId ORDER BY reviewedAt")
  abstract suspend fun forCard(cardId: String): List<ReviewLogEntity>

  @Query("SELECT COUNT(*) FROM review_log")
  abstract suspend fun count(): Int

  @Query("SELECT MAX(reviewedAt) FROM review_log")
  abstract suspend fun lastReviewAt(): Instant?
}

@Dao
abstract class StudyDao(private val db: ProgressDatabase) {
  /** One rating = one transaction: log row first, then the card. Returns the log id (for undo). */
  @Transaction
  open suspend fun applyAnswer(log: ReviewLogEntity, card: CardEntity): Long {
    val id = db.reviewLogDao().insert(log)
    db.cardDao().update(card)
    return id
  }

  @Transaction
  open suspend fun revertAnswer(logId: Long, card: CardEntity) {
    db.reviewLogDao().delete(logId)
    db.cardDao().update(card)
  }
}

@Dao
interface DeckConfigDao {
  @Query("SELECT * FROM deck_config WHERE lang = :lang")
  suspend fun get(lang: String): DeckConfigEntity?

  @Query("SELECT * FROM deck_config WHERE lang = :lang")
  fun observe(lang: String): Flow<DeckConfigEntity?>

  @Upsert suspend fun upsert(config: DeckConfigEntity)
}

@Dao
interface SettingsDao {
  @Query("SELECT value FROM app_settings WHERE `key` = :key")
  suspend fun get(key: String): String?

  @Upsert suspend fun put(setting: AppSettingEntity)
}

@Dao
interface PackDao {
  @Query("SELECT * FROM installed_pack ORDER BY lang")
  suspend fun all(): List<InstalledPackEntity>

  @Query("SELECT * FROM installed_pack ORDER BY lang")
  fun observeAll(): Flow<List<InstalledPackEntity>>

  @Upsert suspend fun upsert(pack: InstalledPackEntity)

  @Query("DELETE FROM installed_pack WHERE lang = :lang AND id != :keep")
  suspend fun deleteOthers(lang: String, keep: String)
}
