package app.yuki.core.data

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.yuki.core.data.backup.BackupManager
import app.yuki.core.data.db.CardEntity
import app.yuki.core.data.db.CardSnapshot
import app.yuki.core.data.db.ReviewKind
import app.yuki.core.data.db.ReviewLogEntity
import app.yuki.core.model.CardState
import app.yuki.core.model.CardType
import app.yuki.core.model.Memory
import app.yuki.core.model.Rating
import app.yuki.core.scheduler.Fsrs
import java.io.File
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** PRD S15 release gate: export → wipe → import must restore cards and review log exactly. */
@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {

  @Test
  fun exportWipeRestoreIsLossless() = runTest {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    context.deleteDatabase("progress.db")
    val holder = DatabaseHolder(context)
    val fsrs = Fsrs(enableFuzzing = false)
    val t0 = Instant.parse("2026-10-01T08:00:00Z")

    // Seed 50 cards; review 20 of them a few times through the real transaction path.
    val cards = (1..50).map { i ->
      CardEntity(
        id = "zh:w$i#RECOGNITION", wordId = "zh:w$i", packId = "zh-test", lang = "zh", type = CardType.RECOGNITION,
        state = CardState.NEW, step = null, stability = null, difficulty = null, due = null, lastReview = null,
        reps = 0, lapses = 0, newRank = i,
      )
    }
    holder.db.cardDao().insertIgnore(cards)
    cards.take(20).forEachIndexed { i, card ->
      var memory = Memory.NEW
      var entity = card
      listOf(Rating.GOOD, Rating.GOOD, if (i % 3 == 0) Rating.AGAIN else Rating.EASY).forEachIndexed { r, rating ->
        val now = t0.plusSeconds(86_400L * r * 3 + i * 60L)
        val after = fsrs.review(memory, rating, now)
        val log = ReviewLogEntity(
          cardId = card.id, lang = "zh", rating = rating.value, reviewedAt = now, durationMs = 4_000,
          beforeState = memory.state, before = CardSnapshot.of(memory), after = CardSnapshot.of(after),
          sessionId = "s$r", kind = ReviewKind.SCHEDULED,
        )
        entity = entity.withMemory(after)
        holder.db.studyDao().applyAnswer(log, entity)
        memory = after
      }
    }
    holder.db.cardDao().setSuspended("zh:w42", true)

    val cardsBefore = cards.map { holder.db.cardDao().get(it.id) }
    val logsBefore = cards.flatMap { holder.db.reviewLogDao().forCard(it.id) }
    assertEquals(60, logsBefore.size)

    val backup = BackupManager(context, holder, "test")
    val file = File(context.cacheDir, "roundtrip.yuki-backup").apply { delete() }
    val manifest = backup.export(Uri.fromFile(file))
    assertEquals(50, manifest.cards)
    assertEquals(60, manifest.reviews)

    // Wipe progress completely.
    holder.replace { context.deleteDatabase("progress.db") }
    assertEquals(0, holder.db.cardDao().count())

    val preview = backup.inspect(Uri.fromFile(file))
    assertEquals(60, preview.manifest.reviews)
    backup.restore(preview)

    assertEquals(cardsBefore, cards.map { holder.db.cardDao().get(it.id) })
    assertEquals(logsBefore, cards.flatMap { holder.db.reviewLogDao().forCard(it.id) })
    holder.db.close()
  }
}
