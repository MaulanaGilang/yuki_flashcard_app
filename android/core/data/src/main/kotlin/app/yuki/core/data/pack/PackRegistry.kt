package app.yuki.core.data.pack

import android.content.Context
import android.content.res.AssetFileDescriptor
import app.yuki.core.data.db.CardEntity
import app.yuki.core.data.db.InstalledPackEntity
import app.yuki.core.data.db.ProgressDatabase
import app.yuki.core.model.CardState
import app.yuki.core.model.CardType
import app.yuki.core.model.Lang
import java.io.File
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PackManifest(val id: String, val lang: String, val version: Int, val title: String = id)

/** An opened, installed pack: its reader plus where its media lives. */
class InstalledPack(val entity: InstalledPackEntity, val reader: PackReader) {
  val lang: Lang get() = reader.lang
}

/**
 * Installs bundled packs from APK assets (`assets/packs/<id>/`), keeps one [PackReader] per pack
 * and seeds one card per word per card type. Content and progress stay in separate files:
 * re-installing a pack never touches review history (ARCHITECTURE §2.4).
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
      val assets = context.assets
      val bundled = assets.list(ASSET_ROOT).orEmpty()
      for (dir in bundled) {
        val manifest = assets.open("$ASSET_ROOT/$dir/manifest.json").use { json.decodeFromString(PackManifest.serializer(), it.readBytes().decodeToString()) }
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
      _packs.value.forEach { it.reader.close() }
      _packs.value = dao.all().map { InstalledPack(it, PackReader(it.id, Lang.fromCode(it.lang), it.path)) }
      ready = true
    }
  }

  private suspend fun seedCards(reader: PackReader) {
    val keys = reader.wordKeys()
    val cards = keys.map { k ->
      CardEntity(
        id = "${k.id}#${CardType.RECOGNITION}",
        wordId = k.id,
        packId = reader.packId,
        lang = reader.lang.code,
        type = CardType.RECOGNITION,
        state = CardState.NEW,
        step = null, stability = null, difficulty = null, due = null, lastReview = null,
        reps = 0, lapses = 0,
        newRank = k.level * 100_000 + k.freqRank.coerceIn(0, 99_999),
      )
    }
    val cardDao = db().cardDao()
    cards.chunked(500).forEach { cardDao.insertIgnore(it) }
    cardDao.markOrphans(reader.packId, keys.map { it.id })
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

  fun closeAll() {
    _packs.value.forEach { it.reader.close() }
    _packs.value = emptyList()
  }

  companion object {
    const val ASSET_ROOT = "packs"
    private val json = Json { ignoreUnknownKeys = true }
  }
}
