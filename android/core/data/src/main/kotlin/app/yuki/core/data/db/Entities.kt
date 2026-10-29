package app.yuki.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import app.yuki.core.model.CardState
import app.yuki.core.model.CardType
import app.yuki.core.model.Memory
import java.time.Instant
import kotlinx.serialization.Serializable

@Entity(
  tableName = "card",
  indices = [
    Index("lang", "suspended", "due"),
    Index("lang", "state", "newRank"),
    Index("wordId"),
  ],
)
data class CardEntity(
  @PrimaryKey val id: String,
  val wordId: String,
  val packId: String,
  val lang: String,
  val type: CardType,
  val state: CardState,
  val step: Int?,
  val stability: Double?,
  val difficulty: Double?,
  val due: Instant?,
  val lastReview: Instant?,
  val reps: Int,
  val lapses: Int,
  val suspended: Boolean = false,
  val markedKnown: Boolean = false,
  val orphaned: Boolean = false,
  val newRank: Int,
) {
  val memory: Memory get() = Memory(state, step, stability, difficulty, due, lastReview, reps, lapses)

  fun withMemory(m: Memory) = copy(
    state = m.state, step = m.step, stability = m.stability, difficulty = m.difficulty,
    due = m.due, lastReview = m.lastReview, reps = m.reps, lapses = m.lapses,
  )
}

/** Full copy of a card's memory, stored in the review log so any rating can be undone exactly. */
@Serializable
data class CardSnapshot(
  val state: CardState,
  val step: Int?,
  val stability: Double?,
  val difficulty: Double?,
  val dueMillis: Long?,
  val lastReviewMillis: Long?,
  val reps: Int,
  val lapses: Int,
) {
  fun toMemory() = Memory(state, step, stability, difficulty, dueMillis?.let(Instant::ofEpochMilli), lastReviewMillis?.let(Instant::ofEpochMilli), reps, lapses)

  companion object {
    fun of(m: Memory) = CardSnapshot(m.state, m.step, m.stability, m.difficulty, m.due?.toEpochMilli(), m.lastReview?.toEpochMilli(), m.reps, m.lapses)
  }
}

enum class ReviewKind { SCHEDULED, EXTRA_ROUND, MANUAL_RESET }

@Entity(tableName = "review_log", indices = [Index("cardId", "reviewedAt"), Index("reviewedAt"), Index("lang", "reviewedAt")])
data class ReviewLogEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val cardId: String,
  val lang: String,
  val rating: Int,
  val reviewedAt: Instant,
  val durationMs: Long,
  val beforeState: CardState,
  val before: CardSnapshot,
  val after: CardSnapshot,
  val sessionId: String,
  val kind: ReviewKind,
)

@Entity(tableName = "deck_config")
data class DeckConfigEntity(
  @PrimaryKey val lang: String,
  val newPerDay: Int = 10,
  val maxReviews: Int = 200,
  val desiredRetention: Double = 0.90,
  val learningSteps: String = "60,600",
  val relearningSteps: String = "600",
  val showReadingOnFront: Boolean = false,
  val autoplayFront: Boolean = false,
  val fsrsParams: String? = null,
  /** Extra card directions (PRD S6), off by default. Added in schema 2. */
  @ColumnInfo(defaultValue = "0") val listening: Boolean = false,
  @ColumnInfo(defaultValue = "0") val production: Boolean = false,
)

@Entity(tableName = "app_settings")
data class AppSettingEntity(@PrimaryKey val key: String, val value: String)

@Entity(tableName = "installed_pack")
data class InstalledPackEntity(
  @PrimaryKey val id: String,
  val version: Int,
  val lang: String,
  val path: String,
  /** Asset folder for bundled packs (media read from APK assets), null for imported packs. */
  val assetDir: String?,
  val installedAt: Instant,
)
