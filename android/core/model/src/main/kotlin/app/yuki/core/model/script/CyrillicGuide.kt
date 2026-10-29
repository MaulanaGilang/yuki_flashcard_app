package app.yuki.core.model.script

/** Russian Cyrillic: alphabet, transliteration (stress kept) and per-word reading hints. */
object CyrillicGuide : ScriptGuide {
  private const val STRESS = '́'

  override val title = "Reading Cyrillic"
  override val intro =
    "Russian has 33 letters. Some look like Latin letters but sound different. In italics and handwriting, т looks like m, и like u, п like n and д like g."

  override val alphabet = listOf(
    Letter("А а", "a", "a", "a as in father"),
    Letter("Б б", "be", "b", "b as in bed"),
    Letter("В в", "ve", "v", "v as in van", "Looks like B, sounds like V"),
    Letter("Г г", "ge", "g", "g as in go"),
    Letter("Д д", "de", "d", "d as in dog", "Doesn't look like D; in italics it looks like g"),
    Letter("Е е", "ye", "ye", "ye as in yes", "Looks like E, but starts with a y sound"),
    Letter("Ё ё", "yo", "yo", "yo as in yogurt; always stressed"),
    Letter("Ж ж", "zhe", "zh", "s as in measure"),
    Letter("З з", "ze", "z", "z as in zoo", "Looks like the number 3"),
    Letter("И и", "i", "i", "ee as in see", "Looks like a backwards N; in italics like u"),
    Letter("Й й", "i kratkoye", "y", "y as in boy (a short и)"),
    Letter("К к", "ka", "k", "k as in kite"),
    Letter("Л л", "el", "l", "l as in lamp", "Looks like Greek Λ"),
    Letter("М м", "em", "m", "m as in map"),
    Letter("Н н", "en", "n", "n as in no", "Looks like H, sounds like N"),
    Letter("О о", "o", "o", "o as in more when stressed; like a when not"),
    Letter("П п", "pe", "p", "p as in pen", "Looks like Greek π; in italics like n"),
    Letter("Р р", "er", "r", "rolled r", "Looks like P, sounds like R"),
    Letter("С с", "es", "s", "s as in sun", "Looks like C, sounds like S"),
    Letter("Т т", "te", "t", "t as in top", "In italics it looks like m"),
    Letter("У у", "u", "u", "oo as in food", "Looks like y, sounds like oo"),
    Letter("Ф ф", "ef", "f", "f as in fun"),
    Letter("Х х", "kha", "kh", "ch as in Scottish loch", "Looks like X, sounds like kh"),
    Letter("Ц ц", "tse", "ts", "ts as in cats"),
    Letter("Ч ч", "che", "ch", "ch as in chair"),
    Letter("Ш ш", "sha", "sh", "sh as in shop"),
    Letter("Щ щ", "shcha", "shch", "a long, soft sh, as in fresh sheep"),
    Letter("Ъ ъ", "hard sign", "\"", "no sound; separates a consonant from the next vowel"),
    Letter("Ы ы", "y", "y", "like i in bit, said further back in the mouth"),
    Letter("Ь ь", "soft sign", "'", "no sound; makes the consonant before it soft"),
    Letter("Э э", "e", "e", "e as in met"),
    Letter("Ю ю", "yu", "yu", "you"),
    Letter("Я я", "ya", "ya", "ya as in yard"),
  )

  private val latin = mapOf(
    'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ё' to "yo", 'ж' to "zh",
    'з' to "z", 'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o",
    'п' to "p", 'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "kh", 'ц' to "ts",
    'ч' to "ch", 'ш' to "sh", 'щ' to "shch", 'ъ' to "", 'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya",
  )
  private val vowels = "аеёиоуыэюя"
  private val stressedLatin = mapOf('a' to "á", 'e' to "é", 'i' to "í", 'o' to "ó", 'u' to "ú", 'y' to "ý")
  private val devoicing = mapOf('б' to "p", 'в' to "f", 'г' to "k", 'д' to "t", 'ж' to "sh", 'з' to "s")

  /** я́блоко → yábloko. Е reads "ye" at the start of a word and after a vowel or ъ/ь. */
  override fun transliterate(text: String): String {
    val out = StringBuilder()
    for ((i, raw) in text.withIndex()) {
      if (raw == STRESS) continue
      out.append(pieceAt(text, i) ?: raw.toString())
    }
    return out.toString()
  }

  /** Latin reading of the letter at [i], using the letter before it for context; null if not Cyrillic. */
  private fun pieceAt(text: String, i: Int): String? {
    val raw = text[i]
    val lower = raw.lowercaseChar()
    val mapped = latin[lower] ?: return null
    var p = i - 1
    while (p >= 0 && text[p] == STRESS) p--
    val prev = if (p >= 0) text[p] else ' '
    var piece = if (lower == 'е' && (!prev.isLetter() || prev.lowercaseChar() in vowels || prev.lowercaseChar() in "ъь")) "ye" else mapped
    if (text.getOrNull(i + 1) == STRESS) piece = stress(piece)
    if (raw.isUpperCase() && piece.isNotEmpty()) piece = piece.replaceFirstChar { it.uppercaseChar() }
    return piece
  }

  private fun stress(piece: String): String {
    val idx = piece.indexOfLast { it in stressedLatin }
    return if (idx < 0) piece else piece.substring(0, idx) + stressedLatin.getValue(piece[idx]) + piece.substring(idx + 1)
  }

  override fun breakdown(word: String): List<ReadingUnit> {
    val units = mutableListOf<ReadingUnit>()
    val hasStress = STRESS in word
    val letters = word.filter { it != STRESS }
    var letterIndex = 0
    for ((i, raw) in word.withIndex()) {
      if (raw == STRESS) continue
      val lower = raw.lowercaseChar()
      val mapped = latin[lower] ?: continue
      val stressed = word.getOrNull(i + 1) == STRESS
      val glyph = if (stressed) "$raw$STRESS" else raw.toString()
      val isLast = letterIndex == letters.length - 1
      val note = when {
        stressed -> "stressed"
        lower == 'о' && hasStress -> "unstressed: sounds like a"
        lower == 'ь' -> "softens the letter before"
        lower == 'ъ' -> "silent separator"
        isLast && lower in devoicing -> "at the end: sounds like ${devoicing[lower]}"
        else -> null
      }
      units += ReadingUnit(glyph, pieceAt(word, i).orEmpty().ifEmpty { "–" }, note)
      letterIndex++
    }
    return units
  }

  override fun hints(word: String): List<String> {
    val hints = mutableListOf<String>()
    val hasStress = STRESS in word
    val plain = word.filter { it != STRESS }.lowercase()
    if (hasStress) {
      val unstressedO = word.withIndex().any { (i, c) -> c.lowercaseChar() == 'о' && word.getOrNull(i + 1) != STRESS }
      if (unstressedO) hints += "О without stress sounds like a: молоко́ is said ma-la-KO."
      hints += "The accent mark shows the stressed syllable. Russian books don't print it."
    }
    devoicing[plain.lastOrNull()]?.let { hints += "${plain.last()} at the end of a word sounds like $it." }
    if ('ь' in plain) hints += "ь has no sound; it makes the consonant before it soft."
    return hints
  }
}
