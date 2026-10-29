package app.yuki.core.data.pack

import android.content.Context
import android.content.res.AssetFileDescriptor
import app.yuki.core.data.db.AppSettingEntity
import app.yuki.core.data.db.CardEntity
import app.yuki.core.data.db.InstalledPackEntity
import app.yuki.core.data.db.ProgressDatabase
import app.yuki.core.model.CardState
import app.yuki.core.model.CardType
import app.yuki.core.model.Lang
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.time.Instant
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PackSource(val name: String, val url: String? = null, val license: String)

@Serializable
data class PackManifest(
  val id: String,
  val lang: String,
  val version: Int,
  val title: String = id,
  /** Relative path → SHA-256 of every file in the pack (pipeline `package` step). */
  val files: Map<String, String> = emptyMap(),
  /** Upstream datasets and their licences (pipeline `package` step), for the credits screen. */
  val sources: List<PackSource> = emptyList(),
)

/** An opened, installed pack: its reader plus where its media lives. */
class InstalledPack(val entity: InstalledPackEntity, val reader: PackReader) {
  val lang: Lang get() = reader.lang
}

sealed interface ImportError {
  data object NotAPack : ImportError
  data object UnsafeArchive : ImportError
  data class Damaged(val file: String) : ImportError
  data class UnknownLanguage(val code: String) : ImportError
}

class PackImportException(val error: ImportError) : Exception(error.toString())

/**
 * Installs bundled packs from APK assets (`assets/packs/<id>/`) and packs imported from a
 * `.kpack` file, keeps one [PackReader] per language and seeds one card per word per card
 * type. Content and progress stay in separate files: re-installing a pack never touches review
 * history (ARCHITECTURE §2.4). An imported pack replaces the bundled one for its language.
 */
class PackRegistry(private val context: Context, private val db: () -> ProgressDatabase) {
  private val mutex = Mutex()
  private val _packs = MutableStateFlow<List<InstalledPack>>(emptyList())
  val packs: StateFlow<List<InstalledPack>> = _packs

  fun pack(lang: Lang): InstalledPack? = _packs.value.firstOrNull { it.lang == lang }

  fun packForWord(wordId: String): InstalledPack? = pack(Lang.fromCode(wordId.substringBefore(':')))

  @Volatile private var ready = false

  /** Suspends until bundled packs are installed and open. Safe to call from anywhere, any time. */
  suspend fun awaitReady() {
    if (!ready) ensureInstalled()
  }

  /** Installs/updates bundled packs and opens their readers. Runs once per process. */
  suspend fun ensureInstalled() = mutex.withLock {
    if (ready) return@withLock
    withContext(Dispatchers.IO) {
      val dao = db().packDao()
      val installed = dao.all().associateBy { it.id }
      val imported = installed.values.filter { it.assetDir == null }.map { it.lang }.toSet()
      val assets = context.assets
      val bundled = assets.list(ASSET_ROOT).orEmpty()
      for (dir in bundled) {
        val manifest = assets.open("$ASSET_ROOT/$dir/manifest.json").use { json.decodeFromString(PackManifest.serializer(), it.readBytes().decodeToString()) }
        if (manifest.lang in imported) continue
        val target = File(context.filesDir, "packs/${manifest.id}/pack.db")
        val existing = installed[manifest.id]
        if (existing == null || existing.version != manifest.version || !target.exists()) {
          target.parentFile!!.mkdirs()
          val tmp = File(target.parentFile, "pack.db.tmp")
          assets.open("$ASSET_ROOT/$dir/pack.db").use { input -> tmp.outputStream().use { input.copyTo(it) } }
          check(tmp.renameTo(target) || (target.delete() && tmp.renameTo(target))) { "could not install ${manifest.id}" }
          val entity = InstalledPackEntity(manifest.id, manifest.version, manifest.lang, target.absolutePath, "$ASSET_ROOT/$dir", Instant.now())
          dao.upsert(entity)
          PackReader(manifest.id, Lang.fromCode(manifest.lang), target.absolutePath).use { seedCards(it) }
        }
      }
      reopen()
      // Seeding rules changed (listening/production cards added): seed existing packs once.
      val settings = db().settingsDao()
      if ((settings.get(SEED_VERSION_KEY)?.toIntOrNull() ?: 1) < SEED_VERSION) {
        _packs.value.forEach { seedCards(it.reader) }
        settings.put(AppSettingEntity(SEED_VERSION_KEY, SEED_VERSION.toString()))
      }
      ready = true
    }
  }

  private suspend fun reopen() {
    _packs.value.forEach { it.reader.close() }
    val all = db().packDao().all()
    // One pack per language; an imported pack wins over a bundled one.
    val chosen = all.groupBy { it.lang }.map { (_, rows) -> rows.firstOrNull { it.assetDir == null } ?: rows.first() }
    _packs.value = chosen.map { InstalledPack(it, PackReader(it.id, Lang.fromCode(it.lang), it.path)) }
  }

  private suspend fun seedCards(reader: PackReader) {
    val keys = reader.wordKeys()
    val cards = keys.flatMap { k ->
      SEEDED_TYPES.map { type ->
        CardEntity(
          id = "${k.id}#$type",
          wordId = k.id,
          packId = reader.packId,
          lang = reader.lang.code,
          type = type,
          state = CardState.NEW,
          step = null, stability = null, difficulty = null, due = null, lastReview = null,
          reps = 0, lapses = 0,
          newRank = k.level * 100_000 + k.freqRank.coerceIn(0, 99_999),
        )
      }
    }
    val cardDao = db().cardDao()
    cards.chunked(500).forEach { cardDao.insertIgnore(it) }
    keys.map { it.id }.chunked(500).forEach { cardDao.adopt(reader.packId, it) }
    cardDao.markOrphans(reader.packId, keys.map { it.id })
    cardDao.orphanOthers(reader.lang.code, reader.packId)
  }

  /**
   * Installs a `.kpack` (zip of a pipeline pack folder). Every entry is checked against zip-slip
   * and every file against the manifest's SHA-256 before anything replaces the current pack.
   */
  suspend fun import(input: InputStream): PackManifest = withContext(Dispatchers.IO) {
    val root = File(context.filesDir, "packs").apply { mkdirs() }
    val stage = File(root, ".import").apply { deleteRecursively(); mkdirs() }
    try {
      unzipSafely(input, stage)
      val manifestFile = File(stage, "manifest.json")
      val manifest = runCatching { json.decodeFromString(PackManifest.serializer(), manifestFile.readText()) }.getOrNull()
      if (manifest == null || !File(stage, "pack.db").exists() || manifest.files.isEmpty()) throw PackImportException(ImportError.NotAPack)
      val lang = Lang.entries.firstOrNull { it.code == manifest.lang } ?: throw PackImportException(ImportError.UnknownLanguage(manifest.lang))
      if (!manifest.id.matches(Regex("[A-Za-z0-9._-]+"))) throw PackImportException(ImportError.UnsafeArchive)
      for ((path, sha) in manifest.files) {
        val f = File(stage, path)
        if (!f.isInside(stage) || !f.isFile || sha256(f) != sha.lowercase()) throw PackImportException(ImportError.Damaged(path))
      }
      runCatching { PackReader(manifest.id, lang, File(stage, "pack.db").absolutePath).use { it.levels() } }
        .getOrElse { throw PackImportException(ImportError.Damaged("pack.db")) }

      mutex.withLock {
        _packs.value.forEach { it.reader.close() }
        _packs.value = emptyList()
        val target = File(root, manifest.id)
        target.deleteRecursively()
        check(stage.renameTo(target)) { "could not install ${manifest.id}" }
        val dao = db().packDao()
        dao.deleteOthers(lang.code, manifest.id)
        val entity = InstalledPackEntity(manifest.id, manifest.version, lang.code, File(target, "pack.db").absolutePath, null, Instant.now())
        dao.upsert(entity)
        PackReader(manifest.id, lang, entity.path).use { seedCards(it) }
        reopen()
        ready = true
      }
      manifest
    } finally {
      stage.deleteRecursively()
    }
  }

  private fun unzipSafely(input: InputStream, stage: File) {
    var total = 0L
    ZipInputStream(input).use { zip ->
      generateSequence { zip.nextEntry }.forEach { entry ->
        val name = entry.name
        if (name.startsWith("/") || name.contains('\\') || name.split('/').any { it == ".." }) throw PackImportException(ImportError.UnsafeArchive)
        val out = File(stage, name)
        if (!out.isInside(stage)) throw PackImportException(ImportError.UnsafeArchive)
        if (entry.isDirectory) {
          out.mkdirs()
        } else {
          out.parentFile!!.mkdirs()
          out.outputStream().use { sink ->
            val buf = ByteArray(64 * 1024)
            while (true) {
              val n = zip.read(buf)
              if (n < 0) break
              total += n
              if (total > MAX_PACK_BYTES) throw PackImportException(ImportError.UnsafeArchive)
              sink.write(buf, 0, n)
            }
          }
        }
      }
    }
  }

  /** Opens a media file from a pack, whether bundled in assets or installed on disk. */
  fun openMedia(pack: InstalledPack, relativePath: String): AssetFileDescriptor? = runCatching {
    val assetDir = pack.entity.assetDir
    if (assetDir != null) {
      context.assets.openFd("$assetDir/$relativePath")
    } else {
      val file = File(File(pack.entity.path).parentFile, relativePath)
      AssetFileDescriptor(android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY), 0, file.length())
    }
  }.getOrNull()

  /** Image bytes for Compose (images are small WebP files). */
  fun readMediaBytes(pack: InstalledPack, relativePath: String): ByteArray? = runCatching {
    val assetDir = pack.entity.assetDir
    if (assetDir != null) context.assets.open("$assetDir/$relativePath").use { it.readBytes() }
    else File(File(pack.entity.path).parentFile, relativePath).readBytes()
  }.getOrNull()

  /** The installed pack's manifest (credits, version), or null if it can't be read. */
  fun manifest(pack: InstalledPack): PackManifest? = readMediaBytes(pack, "manifest.json")?.let { bytes ->
    runCatching { json.decodeFromString(PackManifest.serializer(), bytes.decodeToString()) }.getOrNull()
  }

  fun closeAll() {
    _packs.value.forEach { it.reader.close() }
    _packs.value = emptyList()
  }

  companion object {
    const val ASSET_ROOT = "packs"
    private const val SEED_VERSION_KEY = "seed_version"
    private const val SEED_VERSION = 2
    private const val MAX_PACK_BYTES = 2L * 1024 * 1024 * 1024
    private val SEEDED_TYPES = listOf(CardType.RECOGNITION, CardType.LISTENING, CardType.PRODUCTION)
    private val json = Json { ignoreUnknownKeys = true }

    private fun File.isInside(dir: File): Boolean = canonicalPath.startsWith(dir.canonicalPath + File.separator)

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
  }
}
