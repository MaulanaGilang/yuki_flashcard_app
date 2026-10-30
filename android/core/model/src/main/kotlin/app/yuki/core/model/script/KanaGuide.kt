package app.yuki.core.model.script

/**
 * Japanese kana (PRD S17): the hiragana and katakana chart, and Hepburn romaji for a kana
 * reading. Kanji have no fixed sound, so romaji is made from the word's kana reading, never from
 * the headword.
 */
object KanaGuide : ScriptGuide {
  override val title = "Hiragana & katakana"
  override val intro =
    "Japanese mixes three scripts. Kanji carry meaning; hiragana spell grammar endings and native words; katakana spell " +
      "foreign words. Each kana is one sound, so once you know the 46 basic ones you can read any kana. A small っ doubles " +
      "the next consonant, ー makes a vowel long, and ゃ ゅ ょ blend with the kana before them (き + ゃ = kya)."

  private val gojuon = listOf(
    "あ" to "a", "い" to "i", "う" to "u", "え" to "e", "お" to "o",
    "か" to "ka", "き" to "ki", "く" to "ku", "け" to "ke", "こ" to "ko",
    "さ" to "sa", "し" to "shi", "す" to "su", "せ" to "se", "そ" to "so",
    "た" to "ta", "ち" to "chi", "つ" to "tsu", "て" to "te", "と" to "to",
    "な" to "na", "に" to "ni", "ぬ" to "nu", "ね" to "ne", "の" to "no",
    "は" to "ha", "ひ" to "hi", "ふ" to "fu", "へ" to "he", "ほ" to "ho",
    "ま" to "ma", "み" to "mi", "む" to "mu", "め" to "me", "も" to "mo",
    "や" to "ya", "ゆ" to "yu", "よ" to "yo",
    "ら" to "ra", "り" to "ri", "る" to "ru", "れ" to "re", "ろ" to "ro",
    "わ" to "wa", "を" to "o", "ん" to "n",
  )

  private val sounds = mapOf(
    "a" to "a as in father", "i" to "ee as in see", "u" to "oo as in food, with flat lips", "e" to "e as in get",
    "o" to "o as in more, short", "shi" to "she", "chi" to "chee", "tsu" to "ts as in cats + oo", "fu" to "between f and h, + oo",
    "ra" to "a light flap, between r, l and d", "ri" to "a light flap + ee", "ru" to "a light flap + oo",
    "re" to "a light flap + e", "ro" to "a light flap + o", "n" to "n, held as its own beat",
  )

  override val alphabet: List<Letter> = gojuon.map { (hira, latin) ->
    Letter(
      glyph = "$hira ${toKatakana(hira)}",
      name = latin,
      latin = latin,
      sound = sounds[latin] ?: "${latin.dropLast(1)} + ${sounds[latin.takeLast(1)] ?: latin.takeLast(1)}",
      lookAlike = if (hira == "を") "Written wo, read o; only used as a particle." else null,
    )
  }

  /** Two-kana combinations (consonant + small ya/yu/yo, and katakana extensions for loanwords). */
  private val pairs: Map<String, String> = buildMap {
    val yoon = mapOf(
      "き" to "ky", "ぎ" to "gy", "し" to "sh", "じ" to "j", "ち" to "ch", "ぢ" to "j", "に" to "ny", "ひ" to "hy",
      "び" to "by", "ぴ" to "py", "み" to "my", "り" to "ry",
    )
    for ((k, c) in yoon) {
      put(k + "ゃ", c + "a"); put(k + "ゅ", c + "u"); put(k + "ょ", c + "o")
    }
    put("しぇ", "she"); put("じぇ", "je"); put("ちぇ", "che")
    put("ふぁ", "fa"); put("ふぃ", "fi"); put("ふぇ", "fe"); put("ふぉ", "fo")
    put("てぃ", "ti"); put("でぃ", "di"); put("とぅ", "tu"); put("どぅ", "du")
    put("うぃ", "wi"); put("うぇ", "we"); put("うぉ", "wo")
    put("ゔぁ", "va"); put("ゔぃ", "vi"); put("ゔぇ", "ve"); put("ゔぉ", "vo")
  }

  private val singles: Map<String, String> = buildMap {
    gojuon.forEach { (k, v) -> put(k, v) }
    putAll(
      mapOf(
        "が" to "ga", "ぎ" to "gi", "ぐ" to "gu", "げ" to "ge", "ご" to "go",
        "ざ" to "za", "じ" to "ji", "ず" to "zu", "ぜ" to "ze", "ぞ" to "zo",
        "だ" to "da", "ぢ" to "ji", "づ" to "zu", "で" to "de", "ど" to "do",
        "ば" to "ba", "び" to "bi", "ぶ" to "bu", "べ" to "be", "ぼ" to "bo",
        "ぱ" to "pa", "ぴ" to "pi", "ぷ" to "pu", "ぺ" to "pe", "ぽ" to "po",
        "ゔ" to "vu", "ゐ" to "i", "ゑ" to "e",
        "ぁ" to "a", "ぃ" to "i", "ぅ" to "u", "ぇ" to "e", "ぉ" to "o", "ゃ" to "ya", "ゅ" to "yu", "ょ" to "yo", "ゎ" to "wa",
        "、" to ", ", "。" to ". ", "！" to "! ", "？" to "? ", "「" to "\"", "」" to "\"", "・" to " ", "　" to " ",
      ),
    )
  }

  /** Hepburn romaji for a kana reading, e.g. たべる -> taberu, がっこう -> gakkou, コーヒー -> koohii. */
  override fun transliterate(text: String): String {
    val kana = toHiragana(text)
    val out = StringBuilder()
    var i = 0
    var doubleNext = false
    while (i < kana.length) {
      val two = if (i + 1 < kana.length) kana.substring(i, i + 2) else null
      val (latin, used) = when {
        two != null && pairs.containsKey(two) -> pairs.getValue(two) to 2
        else -> {
          val c = kana.substring(i, i + 1)
          when (c) {
            "っ" -> { doubleNext = true; i++; continue }
            "ー" -> (out.lastOrNull { it in "aeiou" }?.toString() ?: "") to 1
            "ん" -> {
              val next = kana.getOrNull(i + 1)?.toString()?.let { singles[it] ?: pairs.keys.firstOrNull { k -> k.startsWith(it) }?.let { k -> pairs[k] } }
              (if (next != null && (next.first() in "aeiouy")) "n'" else "n") to 1
            }
            else -> (singles[c] ?: c) to 1
          }
        }
      }
      if (doubleNext) {
        doubleNext = false
        when {
          latin.startsWith("ch") -> out.append('t')
          latin.firstOrNull()?.let { it.isLetter() && it !in "aeiou" } == true -> out.append(latin.first())
        }
      }
      out.append(latin)
      i += used
    }
    return out.toString().trim()
  }

  /** Kana by kana, for "How to read this word". [word] is the kana reading. */
  override fun breakdown(word: String): List<ReadingUnit> {
    val kana = toHiragana(word)
    val units = mutableListOf<ReadingUnit>()
    var i = 0
    while (i < kana.length) {
      val two = if (i + 1 < kana.length) kana.substring(i, i + 2) else null
      val len = if (two != null && pairs.containsKey(two)) 2 else 1
      val original = word.substring(i, i + len)
      val unit = kana.substring(i, i + len)
      val note = when (unit) {
        "っ" -> "small tsu: pause, doubling the next consonant"
        "ー" -> "long mark: hold the vowel for an extra beat"
        "ん" -> "its own beat"
        else -> null
      }
      units += ReadingUnit(original, if (unit == "っ" || unit == "ー") "·" else transliterate(unit), note)
      i += len
    }
    return units
  }

  fun toHiragana(text: String): String = buildString {
    for (ch in text) append(if (ch in 'ァ'..'ヶ') ch - 0x60 else ch)
  }

  fun toKatakana(text: String): String = buildString {
    for (ch in text) append(if (ch in 'ぁ'..'ゖ') ch + 0x60 else ch)
  }
}
