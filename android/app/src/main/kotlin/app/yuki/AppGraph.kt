package app.yuki

import android.content.Context
import app.yuki.core.data.DatabaseHolder
import app.yuki.core.data.backup.BackupManager
import app.yuki.core.data.media.AudioPlayer
import app.yuki.core.data.pack.PackRegistry
import app.yuki.core.data.repo.ProgressRepository
import app.yuki.core.data.repo.StudyRepository
import app.yuki.core.scheduler.StudyDay
import java.time.Clock
import java.time.ZoneId

/**
 * Manual dependency graph (ADR-9: replaces Hilt — the graph is small, and skipping annotation
 * processing keeps builds fast on the 8 GB laptop).
 */
class AppGraph(context: Context) {
  val clock: Clock = Clock.systemUTC()
  val studyDay: () -> StudyDay = { StudyDay(ZoneId.systemDefault(), startHour = 4) }
  val database = DatabaseHolder(context)
  val packs = PackRegistry(context) { database.db }
  val audio = AudioPlayer(context, packs)
  val study = StudyRepository(database, packs, clock, studyDay)
  val progress = ProgressRepository(database, packs, study, clock, studyDay)
  val backup = BackupManager(context, database, BuildConfig.VERSION_NAME)

  /** App-lifetime scope for fire-and-forget work such as widget refreshes. */
  val appScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
}
