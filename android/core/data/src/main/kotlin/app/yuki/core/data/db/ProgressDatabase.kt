package app.yuki.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

@Database(
  entities = [
    CardEntity::class,
    ReviewLogEntity::class,
    DeckConfigEntity::class,
    AppSettingEntity::class,
    InstalledPackEntity::class,
  ],
  version = 2,
  exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class ProgressDatabase : RoomDatabase() {
  abstract fun cardDao(): CardDao
  abstract fun reviewLogDao(): ReviewLogDao
  abstract fun studyDao(): StudyDao
  abstract fun deckConfigDao(): DeckConfigDao
  abstract fun settingsDao(): SettingsDao
  abstract fun packDao(): PackDao

  companion object {
    const val FILE_NAME = "progress.db"

    /** v2: listening / production card switches per language (PRD S6). */
    val MIGRATION_1_2 = object : Migration(1, 2) {
      override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE deck_config ADD COLUMN listening INTEGER NOT NULL DEFAULT 0")
        connection.execSQL("ALTER TABLE deck_config ADD COLUMN production INTEGER NOT NULL DEFAULT 0")
      }
    }

    fun open(context: Context): ProgressDatabase =
      Room.databaseBuilder(context.applicationContext, ProgressDatabase::class.java, FILE_NAME)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .addMigrations(MIGRATION_1_2)
        .build()
  }
}
