package app.yuki.core.data.backup

import android.content.Context
import android.net.Uri
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY
import androidx.sqlite.execSQL
import app.yuki.core.data.DatabaseHolder
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BackupManifest(
  val format: Int = 1,
  val schemaVersion: Int,
  val appVersion: String,
  val createdAt: String,
  val cards: Int,
  val reviews: Int,
  val lastReviewAt: String?,
  val sha256: String,
)

/** What an import would bring in, shown to the user before anything is replaced. */
data class BackupPreview(val manifest: BackupManifest, internal val snapshot: File)

sealed interface BackupError {
  data object NotABackup : BackupError
  data object Corrupted : BackupError
  data class NewerVersion(val backupSchema: Int, val appSchema: Int) : BackupError
}

class BackupException(val error: BackupError) : Exception(error.toString())

/**
 * `.yuki-backup` = zip of a consistent `VACUUM INTO` snapshot of progress.db plus a manifest
 * (ARCHITECTURE §2.9). Backups hold progress only; content packs are re-installable.
 */
class BackupManager(
  private val context: Context,
  private val holder: DatabaseHolder,
  private val appVersion: String,
) {
  private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

  fun suggestedFileName(now: Instant = Instant.now()): String {
    val t = now.atZone(java.time.ZoneId.systemDefault())
    return "yuki-%04d%02d%02d-%02d%02d.yuki-backup".format(t.year, t.monthValue, t.dayOfMonth, t.hour, t.minute)
  }

  suspend fun export(target: Uri) = withContext(Dispatchers.IO) {
    val snapshot = File(context.cacheDir, "snapshot.db").apply { delete() }
    BundledSQLiteDriver().open(holder.file.absolutePath, SQLITE_OPEN_READONLY).use { conn ->
      conn.execSQL("VACUUM INTO '${snapshot.absolutePath.replace("'", "''")}'")
    }
    val db = holder.db
    val manifest = BackupManifest(
      schemaVersion = SCHEMA_VERSION,
      appVersion = appVersion,
      createdAt = Instant.now().toString(),
      cards = db.cardDao().count(),
      reviews = db.reviewLogDao().count(),
      lastReviewAt = db.reviewLogDao().lastReviewAt()?.toString(),
      sha256 = sha256(snapshot),
    )
    context.contentResolver.openOutputStream(target, "wt")!!.use { out ->
      ZipOutputStream(out).use { zip ->
        zip.putNextEntry(ZipEntry(MANIFEST))
        zip.write(json.encodeToString(BackupManifest.serializer(), manifest).encodeToByteArray())
        zip.closeEntry()
        zip.putNextEntry(ZipEntry(SNAPSHOT))
        snapshot.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
      }
    }
    snapshot.delete()
    manifest
  }

  /** Unzips and verifies a backup without touching current progress. */
  suspend fun inspect(source: Uri): BackupPreview = withContext(Dispatchers.IO) {
    val dir = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
    var manifest: BackupManifest? = null
    val snapshot = File(dir, SNAPSHOT)
    context.contentResolver.openInputStream(source)!!.use { input ->
      ZipInputStream(input).use { zip ->
        generateSequence { zip.nextEntry }.forEach { entry ->
          when (entry.name) {
            MANIFEST -> manifest = runCatching { json.decodeFromString(BackupManifest.serializer(), zip.readBytes().decodeToString()) }.getOrNull()
            SNAPSHOT -> snapshot.outputStream().use { zip.copyTo(it) }
          }
        }
      }
    }
    val m = manifest ?: throw BackupException(BackupError.NotABackup)
    if (!snapshot.exists() || sha256(snapshot) != m.sha256) throw BackupException(BackupError.Corrupted)
    if (m.schemaVersion > SCHEMA_VERSION) throw BackupException(BackupError.NewerVersion(m.schemaVersion, SCHEMA_VERSION))
    val ok = BundledSQLiteDriver().open(snapshot.absolutePath, SQLITE_OPEN_READONLY).use { conn ->
      conn.prepare("PRAGMA integrity_check").use { s -> s.step() && s.getText(0) == "ok" }
    }
    if (!ok) throw BackupException(BackupError.Corrupted)
    BackupPreview(m, snapshot)
  }

  /** Replaces progress.db with the inspected snapshot (atomic rename), then reopens. */
  suspend fun restore(preview: BackupPreview) = withContext(Dispatchers.IO) {
    holder.replace {
      val target = holder.file
      val staged = File(target.parentFile, "${target.name}.restore")
      preview.snapshot.copyTo(staged, overwrite = true)
      File(target.path + "-wal").delete()
      File(target.path + "-shm").delete()
      if (!staged.renameTo(target)) {
        target.delete()
        check(staged.renameTo(target)) { "could not replace progress database" }
      }
    }
    preview.snapshot.parentFile?.deleteRecursively()
  }

  private fun sha256(file: File): String {
    val md = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
      val buf = ByteArray(64 * 1024)
      while (true) {
        val n = input.read(buf)
        if (n < 0) break
        md.update(buf, 0, n)
      }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
  }

  companion object {
    /** Must equal ProgressDatabase version. */
    const val SCHEMA_VERSION = 1
    private const val MANIFEST = "manifest.json"
    private const val SNAPSHOT = "progress.db"
  }
}
