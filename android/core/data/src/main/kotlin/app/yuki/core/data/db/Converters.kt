package app.yuki.core.data.db

import androidx.room.TypeConverter
import java.time.Instant
import kotlinx.serialization.json.Json

class Converters {
  @TypeConverter fun instantToLong(value: Instant?): Long? = value?.toEpochMilli()

  @TypeConverter fun longToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

  @TypeConverter fun snapshotToJson(value: CardSnapshot): String = json.encodeToString(CardSnapshot.serializer(), value)

  @TypeConverter fun jsonToSnapshot(value: String): CardSnapshot = json.decodeFromString(CardSnapshot.serializer(), value)

  private companion object {
    val json = Json { ignoreUnknownKeys = true }
  }
}
