package app.yuki.core.data.pack

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteStatement
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.driver.bundled.SQLITE_OPEN_READONLY
import app.yuki.core.model.AssetRef
import app.yuki.core.model.Hanzi
import app.yuki.core.model.Lang
import app.yuki.core.model.Sense
import app.yuki.core.model.Sentence
import app.yuki.core.model.Word
import app.yuki.core.model.WordDetail
import java.io.Closeable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/** A level as listed in a pack, e.g. 3 / "HSK 3" with its word count. */
data class PackLevel(val level: Int, val label: String, val wordCount: Int)

/** Minimal word row used to seed cards: id plus its order for introducing new cards. */
data class WordKey(val id: String, val level: Int, val freqRank: Int)

/**
 * Read-only access to one content pack's `pack.db` (schema: ARCHITECTURE §2.4).
 * One connection, serialised behind a mutex, on the IO dispatcher.
 */
class PackReader(val packId: String, val lang: Lang, dbPath: String) : Closeable {
  private val connection: SQLiteConnection = BundledSQLiteDriver().open(dbPath, SQLITE_OPEN_READONLY)
  private val mutex = Mutex()

  private suspend fun <T> read(block: (SQLiteConnection) -> T): T =
    mutex.withLock { withContext(Dispatchers.IO) { block(connection) } }

  suspend fun meta(): Map<String, String> = read { c ->
    c.prepare("SELECT key, value FROM pack_meta").use { s ->
      buildMap { while (s.step()) put(s.getText(0), s.getText(1)) }
    }
  }

  suspend fun wordKeys(): List<WordKey> = read { c ->
    c.prepare("SELECT id, level, freq_rank FROM word").use { s ->
      buildList { while (s.step()) add(WordKey(s.getText(0), s.getLong(1).toInt(), s.getLong(2).toInt())) }
    }
  }

  suspend fun levels(): List<PackLevel> = read { c ->
    c.prepare("SELECT level, level_label, COUNT(*) FROM word GROUP BY level, level_label ORDER BY level").use { s ->
      buildList { while (s.step()) add(PackLevel(s.getLong(0).toInt(), s.getText(1), s.getLong(2).toInt())) }
    }
  }

  suspend fun words(ids: Collection<String>): Map<String, Word> = read { c ->
    val out = HashMap<String, Word>(ids.size)
    ids.chunked(400).forEach { chunk ->
      val marks = chunk.joinToString(",") { "?" }
      c.prepare("SELECT $WORD_COLUMNS FROM word WHERE id IN ($marks)").use { s ->
        chunk.forEachIndexed { i, id -> s.bindText(i + 1, id) }
        while (s.step()) readWord(s).let { out[it.id] = it }
      }
    }
    out
  }

  suspend fun wordsByLevel(level: Int): List<Word> = read { c ->
    c.prepare("SELECT $WORD_COLUMNS FROM word WHERE level = ? ORDER BY freq_rank").use { s ->
      s.bindLong(1, level.toLong())
      buildList { while (s.step()) add(readWord(s)) }
    }
  }

  /** Glosses for many words at once (first sense of each), for list rows. */
  suspend fun firstGlosses(ids: Collection<String>): Map<String, String> = read { c ->
    val out = HashMap<String, String>(ids.size)
    ids.chunked(400).forEach { chunk ->
      val marks = chunk.joinToString(",") { "?" }
      c.prepare("SELECT word_id, gloss FROM sense WHERE ord = 0 AND word_id IN ($marks)").use { s ->
        chunk.forEachIndexed { i, id -> s.bindText(i + 1, id) }
        while (s.step()) out[s.getText(0)] = s.getText(1)
      }
    }
    out
  }

  /**
   * Substring search over the normalised `search_key` (headword, toneless pinyin, numbered
   * pinyin, glosses). Exact headword / reading prefix matches rank first.
   */
  suspend fun search(query: String, limit: Int = 200): List<Word> {
    val q = normalizeQuery(query)
    if (q.isEmpty()) return emptyList()
    return read { c ->
      c.prepare(
        """SELECT $WORD_COLUMNS FROM word WHERE search_key LIKE ? ESCAPE '\'
           ORDER BY (headword = ?) DESC, (search_key LIKE ? ESCAPE '\') DESC, level, freq_rank LIMIT ?""",
      ).use { s ->
        val escaped = q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        s.bindText(1, "%$escaped%")
        s.bindText(2, query.trim())
        s.bindText(3, "$escaped%")
        s.bindLong(4, limit.toLong())
        buildList { while (s.step()) add(readWord(s)) }
      }
    }
  }

  suspend fun detail(wordId: String): WordDetail? = read { c ->
    val word = c.prepare("SELECT $WORD_COLUMNS FROM word WHERE id = ?").use { s ->
      s.bindText(1, wordId)
      if (s.step()) readWord(s) else null
    } ?: return@read null
    val senses = c.prepare("SELECT pos, gloss FROM sense WHERE word_id = ? ORDER BY ord").use { s ->
      s.bindText(1, wordId)
      buildList { while (s.step()) add(Sense(s.getText(0), s.getText(1))) }
    }
    val sentences = c.prepare(
      "SELECT id, text, reading, translation, audio, audio_human FROM sentence WHERE word_id = ? ORDER BY ord",
    ).use { s ->
      s.bindText(1, wordId)
      buildList {
        while (s.step()) {
          val reading = s.textOrNull(2)
          // ru/ar: `reading` is the sentence with stress or vowel marks. Show that as the text and let
          // the app transliterate it, rather than printing it as the Latin line.
          if (lang.needsTransliteration) {
            add(Sentence(s.getText(0), reading ?: s.getText(1), null, s.getText(3), s.textOrNull(4), s.getLong(5) == 1L))
          } else {
            add(Sentence(s.getText(0), s.getText(1), reading, s.getText(3), s.textOrNull(4), s.getLong(5) == 1L))
          }
        }
      }
    }
    var audio: AssetRef? = null
    var image: AssetRef? = null
    c.prepare("SELECT kind, path, human, source, license FROM asset WHERE word_id = ?").use { s ->
      s.bindText(1, wordId)
      while (s.step()) {
        val ref = AssetRef(s.getText(1), s.getLong(2) == 1L, s.getText(3), s.getText(4))
        when (s.getText(0)) {
          "word_audio" -> audio = ref
          "image" -> image = ref
        }
      }
    }
    // Stroke data for hanzi, and for kanji in Japanese packs (kana have no row, so they're skipped).
    val characters = if (lang.hasCharacterReading) {
      word.headword.codePoints().toArray().map { String(Character.toChars(it)) }.distinct().mapNotNull { ch -> readHanzi(c, ch) }
    } else {
      emptyList()
    }
    WordDetail(word, senses, sentences, audio, image, characters)
  }

  /** Where the pack's pictures and recordings come from: (source, licence, count), biggest first. */
  suspend fun assetCredits(): List<Triple<String, String, Int>> = read { c ->
    c.prepare("SELECT source, license, COUNT(*) FROM asset GROUP BY source, license ORDER BY COUNT(*) DESC").use { s ->
      buildList { while (s.step()) add(Triple(s.getText(0), s.getText(1), s.getLong(2).toInt())) }
    }
  }

  suspend fun hanzi(ch: String): Hanzi? = read { c -> readHanzi(c, ch) }

  private fun readHanzi(c: SQLiteConnection, ch: String): Hanzi? =
    c.prepare("SELECT ch, stroke_count, radical, strokes_json, medians_json FROM hanzi WHERE ch = ?").use { s ->
      s.bindText(1, ch)
      if (!s.step()) return@use null
      val strokes = json.parseToJsonElement(s.getText(3)).jsonArray.map { it.jsonPrimitive.content }
      val medians = json.parseToJsonElement(s.getText(4)).jsonArray.map { stroke ->
        stroke.jsonArray.map { pt ->
          val xy = pt.jsonArray
          xy[0].jsonPrimitive.content.toFloat() to xy[1].jsonPrimitive.content.toFloat()
        }
      }
      Hanzi(s.getText(0), s.getLong(1).toInt(), s.textOrNull(2), strokes, medians)
    }

  private fun readWord(s: SQLiteStatement): Word {
    val extra = s.textOrNull(8)?.let { runCatching { json.parseToJsonElement(it) as JsonObject }.getOrNull() }
    return Word(
      id = s.getText(0),
      lang = lang,
      headword = s.getText(1),
      // ru/ar packs put the stressed/vowelled form in `reading` (the headword already shows it) and
      // IPA in extra_json; the app's small reading line shows IPA, and the Latin line is made locally.
      reading = if (lang.needsTransliteration) extra?.get("ipa")?.jsonPrimitive?.content.orEmpty() else s.getText(2),
      level = s.getLong(3).toInt(),
      levelLabel = s.getText(4),
      freqRank = s.getLong(5).toInt(),
      pos = s.getText(6),
      syllables = (extra?.get("syllables") as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty(),
      tones = (extra?.get("tones") as? JsonArray)?.map { it.jsonPrimitive.int }.orEmpty(),
      measureWord = extra?.get("measure_word")?.jsonPrimitive?.content,
      gender = extra?.get("gender")?.jsonPrimitive?.content,
      grammar = (extra?.get("grammar") as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty(),
    )
  }

  override fun close() = connection.close()

  companion object {
    private const val WORD_COLUMNS = "id, headword, reading, level, level_label, freq_rank, pos, search_key, extra_json"
    private val json = Json { ignoreUnknownKeys = true }

    private fun SQLiteStatement.textOrNull(i: Int): String? = if (isNull(i)) null else getText(i)

    /**
     * The `search_key` normalisation contract (ARCHITECTURE §2.4), shared with the pipeline:
     * lowercase; drop combining marks on Latin letters (pinyin tones, Spanish accents, ü→u, ñ→n);
     * drop the Russian stress mark U+0301 everywhere but keep й and ё; drop Arabic vowel marks
     * (U+064B–U+0652, U+0670); hanzi unchanged.
     */
    fun normalizeQuery(q: String): String {
      val decomposed = java.text.Normalizer.normalize(q.trim().lowercase(), java.text.Normalizer.Form.NFD)
      val out = StringBuilder(decomposed.length)
      var base = ' '
      for (ch in decomposed) {
        if (Character.getType(ch) == Character.NON_SPACING_MARK.toInt()) {
          if (ch == '́' || base in 'a'..'z' || ch in 'ً'..'ْ' || ch == 'ٰ') continue
        } else {
          base = ch
        }
        out.append(ch)
      }
      return java.text.Normalizer.normalize(out, java.text.Normalizer.Form.NFC)
    }
  }
}
