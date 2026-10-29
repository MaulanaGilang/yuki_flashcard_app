package app.yuki.core.data

import android.util.Log
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.yuki.core.data.pack.PackReader
import app.yuki.core.model.Lang
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Full-size pack timings (PRD S8: search ≤ 100 ms over 11,000 entries; card open must feel
 * instant). Builds a synthetic 8,000-word pack in the pack.db schema (ARCHITECTURE §2.4),
 * with and without word_id indexes on the child tables, and logs medians under tag YukiPerf.
 */
@RunWith(AndroidJUnit4::class)
class PackPerformanceTest {
  private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

  private fun buildPack(file: File, words: Int, childIndexes: Boolean) {
    file.delete()
    BundledSQLiteDriver().open(file.absolutePath).use { c ->
      c.execSQL("CREATE TABLE pack_meta(key TEXT PRIMARY KEY, value TEXT)")
      c.execSQL("CREATE TABLE word(id TEXT PRIMARY KEY, lang TEXT, headword TEXT, reading TEXT, search_key TEXT, level INTEGER, level_label TEXT, freq_rank INTEGER, pos TEXT, extra_json TEXT)")
      c.execSQL("CREATE TABLE sense(word_id TEXT, ord INTEGER, pos TEXT, gloss TEXT)")
      c.execSQL("CREATE TABLE sentence(id TEXT PRIMARY KEY, word_id TEXT, ord INTEGER, text TEXT, reading TEXT, translation TEXT, audio TEXT, audio_human INTEGER)")
      c.execSQL("CREATE TABLE asset(word_id TEXT, kind TEXT, path TEXT, human INTEGER, source TEXT, license TEXT, attribution TEXT)")
      c.execSQL("CREATE TABLE hanzi(ch TEXT PRIMARY KEY, stroke_count INTEGER, radical TEXT, strokes_json TEXT, medians_json TEXT)")
      c.execSQL("CREATE INDEX word_level ON word(level, freq_rank)")
      c.execSQL("CREATE INDEX word_search ON word(search_key)")
      if (childIndexes) {
        c.execSQL("CREATE INDEX sense_word ON sense(word_id, ord)")
        c.execSQL("CREATE INDEX sentence_word ON sentence(word_id, ord)")
        c.execSQL("CREATE INDEX asset_word ON asset(word_id)")
      }
      c.execSQL("BEGIN")
      val w = c.prepare("INSERT INTO word VALUES (?,?,?,?,?,?,?,?,?,?)")
      val s = c.prepare("INSERT INTO sense VALUES (?,?,?,?)")
      val t = c.prepare("INSERT INTO sentence VALUES (?,?,?,?,?,?,?,?)")
      val a = c.prepare("INSERT INTO asset VALUES (?,?,?,?,?,?,?)")
      for (i in 0 until words) {
        val id = "es:palabra$i::n"
        val head = "palabra$i"
        val gloss = "meaning number $i; another sense"
        w.reset(); w.bindText(1, id); w.bindText(2, "es"); w.bindText(3, head); w.bindText(4, "/pa'labra/")
        w.bindText(5, "$head pa'labra $gloss"); w.bindLong(6, (1 + i * 5 / words).toLong()); w.bindText(7, "A${1 + i * 5 / words}")
        w.bindLong(8, i.toLong()); w.bindText(9, "n"); w.bindText(10, """{"gender":"f.","grammar":["irregular"]}"""); w.step()
        for (o in 0 until 3) { s.reset(); s.bindText(1, id); s.bindLong(2, o.toLong()); s.bindText(3, "n"); s.bindText(4, "$gloss $o"); s.step() }
        for (o in 0 until 2) {
          t.reset(); t.bindText(1, "$id#s$o"); t.bindText(2, id); t.bindLong(3, o.toLong()); t.bindText(4, "Una frase con $head."); t.bindNull(5)
          t.bindText(6, "A sentence with word $i."); t.bindText(7, "audio/s$i-$o.opus"); t.bindLong(8, 0); t.step()
        }
        for (k in listOf("word_audio", "image")) {
          a.reset(); a.bindText(1, id); a.bindText(2, k); a.bindText(3, "$k/$i"); a.bindLong(4, 0); a.bindText(5, "test"); a.bindText(6, "CC0"); a.bindNull(7); a.step()
        }
      }
      w.close(); s.close(); t.close(); a.close()
      c.execSQL("COMMIT")
    }
  }

  private inline fun medianMs(runs: Int, block: () -> Unit): Double {
    block() // warm-up
    val times = (1..runs).map { val t0 = System.nanoTime(); block(); (System.nanoTime() - t0) / 1e6 }.sorted()
    return times[times.size / 2]
  }

  private fun measure(file: File): Map<String, Double> = runBlocking {
    PackReader("perf", Lang.ES, file.absolutePath).use { r ->
      val ids = (0 until WORDS step 97).map { "es:palabra$it::n" }
      mapOf(
        "search 'pal' (prefix)" to medianMs(15) { r.search("pal") },
        "search 'number 7' (gloss)" to medianMs(15) { r.search("number 7") },
        "detail (card open)" to medianMs(40) { r.detail(ids[(System.nanoTime() % ids.size).toInt().coerceAtLeast(0)]) },
        "wordsByLevel(3)" to medianMs(10) { r.wordsByLevel(3) },
        "firstGlosses(400)" to medianMs(10) { r.firstGlosses((0 until 400).map { "es:palabra${it * 20}::n" }) },
        "wordKeys (seeding)" to medianMs(5) { r.wordKeys() },
      )
    }
  }

  @Test
  fun fullSizePackTimings() {
    val plain = File(context.cacheDir, "perf-plain.db").also { buildPack(it, WORDS, childIndexes = false) }
    val indexed = File(context.cacheDir, "perf-indexed.db").also { buildPack(it, WORDS, childIndexes = true) }
    // Alternate the two packs and keep each query's best round, so background load on the
    // device hits both sides equally.
    fun best(rounds: List<Map<String, Double>>) = rounds.first().keys.associateWith { k -> rounds.minOf { it.getValue(k) } }
    val a = mutableListOf<Map<String, Double>>()
    val b = mutableListOf<Map<String, Double>>()
    repeat(3) { a += measure(plain); b += measure(indexed) }
    val without = best(a)
    val with = best(b)
    plain.delete(); indexed.delete()
    without.keys.forEach { k -> Log.i(TAG, "%-28s no child index %7.2f ms | with %7.2f ms".format(k, without.getValue(k), with.getValue(k))) }
    assertTrue("search over $WORDS words took ${with["search 'pal' (prefix)"]} ms", with.getValue("search 'pal' (prefix)") <= 100.0)
    assertTrue("card open took ${with["detail (card open)"]} ms", with.getValue("detail (card open)") <= 30.0)
  }

  private companion object {
    const val TAG = "YukiPerf"
    const val WORDS = 8_000
  }
}
