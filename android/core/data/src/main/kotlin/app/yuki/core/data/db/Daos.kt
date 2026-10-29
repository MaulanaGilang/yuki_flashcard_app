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

  @Query(
    """SELECT * FROM card WHERE lang = :lang AND suspended = 0 AND markedKnown = 0 AND orphaned = 0
       AND state != 'NEW' AND due < :endOfDay ORDER BY due""",
  )
  abstract suspend fun dueBefore(lang: String, endOfDay: Instant): List<CardEntity>

  @Query(
    """SELECT * FROM card WHERE lang = :lang AND suspended = 0 AND markedKnown = 0 AND orphaned = 0
       AND state = 'NEW' ORDER BY newRank LIMIT :limit""",
  )
  abstract suspend fun nextNew(lang: String, limit: Int): List<CardEntity>

  @Query(
    """SELECT COUNT(*) FROM card WHERE lang = :lang AND suspended = 0 AND markedKnown = 0 AND orphaned = 0
       AND state != 'NEW' AND due < :endOfDay""",
  )
  abstract fun observeDueCount(lang: String, endOfDay: Instant): Flow<Int>

  @Query(
    """SELECT COUNT(*) FROM card WHERE lang = :lang AND suspended = 0 AND markedKnown = 0 AND orphaned = 0
       AND state != 'NEW' AND due < :endOfDay""",
  )
  abstract suspend fun dueCount(lang: String, endOfDay: Instant): Int

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
}
