package app.yuki.core.data

import android.content.Context
import app.yuki.core.data.db.ProgressDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Owns the single [ProgressDatabase] instance. Restoring a backup swaps the database file, so
 * callers always go through [db] and re-subscribe when [version] changes.
 */
class DatabaseHolder(private val context: Context) {
  @Volatile private var current: ProgressDatabase = ProgressDatabase.open(context)
  private val _version = MutableStateFlow(0)
  val version: StateFlow<Int> = _version

  val db: ProgressDatabase get() = current

  val file get() = context.getDatabasePath(ProgressDatabase.FILE_NAME)

  /** Closes the database, runs [swap] on the closed files, then reopens. */
  @Synchronized
  fun replace(swap: () -> Unit) {
    current.close()
    try {
      swap()
    } finally {
      current = ProgressDatabase.open(context)
      _version.value++
    }
  }
}
