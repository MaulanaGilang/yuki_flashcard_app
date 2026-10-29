package app.yuki.core.data.settings

import app.yuki.core.data.DatabaseHolder
import app.yuki.core.data.db.AppSettingEntity
import app.yuki.core.model.Lang
import java.time.Instant
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * App-wide preferences, stored in the progress database's `app_settings` table so they travel
 * with a backup. Per-language study settings live in `deck_config` instead.
 */
class AppSettings(private val holder: DatabaseHolder) {
  private val _changes = MutableStateFlow(0)

  /** Bumps on every write, so read models can re-query. */
  val changes: StateFlow<Int> = _changes

  private suspend fun get(key: String): String? = holder.db.settingsDao().get(key)

  private suspend fun put(key: String, value: String?) {
    holder.db.settingsDao().put(AppSettingEntity(key, value.orEmpty()))
    _changes.value++
  }

  /** Onboarding finished (PRD S10). */
  suspend fun onboarded(): Boolean = get(ONBOARDED) == "1"

  suspend fun setOnboarded() = put(ONBOARDED, "1")

  /** Languages shown on Today, in line order. Null = every installed language. */
  suspend fun activeLangs(): Set<Lang>? = get(ACTIVE_LANGS)?.takeIf { it.isNotBlank() }
    ?.split(',')?.mapNotNull { code -> Lang.entries.firstOrNull { it.code == code } }?.toSet()

  suspend fun setActiveLangs(langs: Set<Lang>) = put(ACTIVE_LANGS, langs.sortedBy { it.ordinal }.joinToString(",") { it.code })

  /** Daily reminder time (PRD S13), or null when reminders are off. */
  suspend fun reminderTime(): LocalTime? = get(REMINDER)?.takeIf { it.isNotBlank() }?.let { runCatching { LocalTime.parse(it) }.getOrNull() }

  suspend fun setReminderTime(time: LocalTime?) = put(REMINDER, time?.toString())

  /** Folder (SAF tree URI) for the automatic weekly backup, or null when it's off. */
  suspend fun autoBackupFolder(): String? = get(AUTO_BACKUP_FOLDER)?.takeIf { it.isNotBlank() }

  suspend fun setAutoBackupFolder(uri: String?) = put(AUTO_BACKUP_FOLDER, uri)

  suspend fun lastAutoBackup(): Instant? = get(LAST_AUTO_BACKUP)?.takeIf { it.isNotBlank() }?.let { runCatching { Instant.parse(it) }.getOrNull() }

  suspend fun setLastAutoBackup(at: Instant) = put(LAST_AUTO_BACKUP, at.toString())

  private companion object {
    const val ONBOARDED = "onboarded"
    const val ACTIVE_LANGS = "active_langs"
    const val REMINDER = "reminder_time"
    const val AUTO_BACKUP_FOLDER = "auto_backup_folder"
    const val LAST_AUTO_BACKUP = "last_auto_backup"
  }
}
