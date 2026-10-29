package app.yuki.core.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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
  version = 1,
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

    fun open(context: Context): ProgressDatabase =
      Room.databaseBuilder(context.applicationContext, ProgressDatabase::class.java, FILE_NAME)
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
  }
}
