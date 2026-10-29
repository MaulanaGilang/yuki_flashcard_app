package app.yuki.core.model.script

/**
 * Modern Standard Arabic: alphabet, vowel marks, and transliteration of fully vowelled
 * (with tashkeel) text. Packs store Arabic with all vowel marks so it can be read aloud.
 */
object ArabicGuide : ScriptGuide {
  override val title = "Reading Arabic"
  override val intro =
    "Arabic is read right to left. Letters join and change shape inside a word. Short vowels are small marks above or below the letter; learner texts always show them."

  private const val FATHA = 'َ'
  private const val KASRA = 'ِ'
  private const val DAMMA = 'ُ'
  private const val SUKUN = 'ْ'
  private const val SHADDA = 'ّ'
  private const val TANWIN_A = 'ً'
  private const val TANWIN_U = 'ٌ'
  private const val TANWIN_I = 'ٍ'
  private const val DAGGER_ALIF = 'ٰ'
  private val marks = setOf(FATHA, KASRA, DAMMA, SUKUN, SHADDA, TANWIN_A, TANWIN_U, TANWIN_I, DAGGER_ALIF)

  private val consonants = mapOf(
    'ب' to "b", 'ت' to "t", 'ث' to "th", 'ج' to "j", 'ح' to "ḥ", 'خ' to "kh", 'د' to "d", 'ذ' to "dh",
    'ر' to "r", 'ز' to "z", 'س' to "s", 'ش' to "sh", 'ص' to "ṣ", 'ض' to "ḍ", 'ط' to "ṭ", 'ظ' to "ẓ",
    'ع' to "ʿ", 'غ' to "gh", 'ف' to "f", 'ق' to "q", 'ك' to "k", 'ل' to "l", 'م' to "m", 'ن' to "n",
    'ه' to "h", 'و' to "w", 'ي' to "y", 'ء' to "ʾ", 'أ' to "ʾ", 'إ' to "ʾ", 'ؤ' to "ʾ", 'ئ' to "ʾ",
  )
  private val sunLetters = setOf('ت', 'ث', 'د', 'ذ', 'ر', 'ز', 'س', 'ش', 'ص', 'ض', 'ط', 'ظ', 'ل', 'ن')
  private val alifs = setOf('ا', 'ٱ')

  override val alphabet = listOf(
    Letter("ا", "alif", "ā", "long a (also carries hamza)"),
    Letter("ب", "bāʾ", "b", "b as in bed"),
    Letter("ت", "tāʾ", "t", "t as in top"),
    Letter("ث", "thāʾ", "th", "th as in think"),
    Letter("ج", "jīm", "j", "j as in jam"),
    Letter("ح", "ḥāʾ", "ḥ", "a breathy h from deep in the throat"),
    Letter("خ", "khāʾ", "kh", "ch as in Scottish loch"),
    Letter("د", "dāl", "d", "d as in dog", "Doesn't join to the next letter"),
    Letter("ذ", "dhāl", "dh", "th as in this", "Doesn't join to the next letter"),
    Letter("ر", "rāʾ", "r", "rolled r", "Doesn't join to the next letter"),
    Letter("ز", "zāy", "z", "z as in zoo", "Doesn't join to the next letter"),
    Letter("س", "sīn", "s", "s as in sun"),
    Letter("ش", "shīn", "sh", "sh as in shop"),
    Letter("ص", "ṣād", "ṣ", "a heavy s, mouth rounded"),
    Letter("ض", "ḍād", "ḍ", "a heavy d"),
    Letter("ط", "ṭāʾ", "ṭ", "a heavy t"),
    Letter("ظ", "ẓāʾ", "ẓ", "a heavy th as in this"),
    Letter("ع", "ʿayn", "ʿ", "a squeezed sound from the throat; no English equivalent"),
    Letter("غ", "ghayn", "gh", "a gargled r, like French r"),
    Letter("ف", "fāʾ", "f", "f as in fun"),
    Letter("ق", "qāf", "q", "k said from deep in the throat"),
    Letter("ك", "kāf", "k", "k as in kite"),
    Letter("ل", "lām", "l", "l as in lamp"),
    Letter("م", "mīm", "m", "m as in map"),
    Letter("ن", "nūn", "n", "n as in no"),
    Letter("ه", "hāʾ", "h", "h as in hat"),
    Letter("و", "wāw", "w / ū", "w as in wet, or long oo", "Doesn't join to the next letter"),
    Letter("ي", "yāʾ", "y / ī", "y as in yes, or long ee"),
    Letter("ء", "hamza", "ʾ", "a catch in the throat, as in uh-oh"),
    Letter("ة", "tāʾ marbūṭa", "a", "the -a ending of many feminine words"),
    Letter("ى", "alif maqṣūra", "ā", "long a at the end of a word"),
    Letter("ـَ", "fatḥa", "a", "short a, written above"),
    Letter("ـِ", "kasra", "i", "short i, written below"),
    Letter("ـُ", "ḍamma", "u", "short u, written above"),
    Letter("ـْ", "sukūn", "–", "no vowel after this letter"),
    Letter("ـّ", "shadda", "×2", "the letter is doubled"),
    Letter("ـً ـٍ ـٌ", "tanwīn", "-an -in -un", "adds n to the ending"),
  )

  override fun transliterate(text: String): String = text.split(' ').joinToString(" ") { word(it).joinToString("") { u -> u.latin } }

  /**
   * Tiles for "how to read": unlike [transliterate], a lengthening letter (ا و ي) shows its own
   * long vowel and the letter before keeps its short one, so each tile says what its letter does.
   */
  override fun breakdown(word: String): List<ReadingUnit> {
    val units = word(word.trim()).toMutableList()
    val shortOf = mapOf('ā' to "a", 'ī' to "i", 'ū' to "u")
    for (i in 1 until units.size) {
      val prev = units[i - 1]
      val long = prev.latin.lastOrNull()
      if (units[i].latin.isEmpty() && long in shortOf && units[i].glyph.first() in "اىوي") {
        units[i - 1] = prev.copy(latin = prev.latin.dropLast(1) + shortOf.getValue(long!!))
        units[i] = units[i].copy(latin = long.toString(), note = "makes the vowel long")
      }
    }
    return units
  }

  override fun hints(word: String): List<String> {
    val hints = mutableListOf<String>()
    val bare = word.trim()
    val letters = bare.filterNot { it in marks }
    if (letters.length > 2 && letters[0] in alifs && letters[1] == 'ل') {
      hints += if (letters[2] in sunLetters) "ال (al-) blends into a sun letter: you hear that letter twice, not the l."
      else "ال is the article al- (the)."
    }
    if (SHADDA in bare) hints += "ـّ (shadda) doubles the letter: hold it a little longer."
    if (bare.any { it == TANWIN_A || it == TANWIN_U || it == TANWIN_I }) hints += "The doubled vowel mark at the end adds -n (tanwīn)."
    if ('ة' in bare) hints += "ة at the end is read -a."
    return hints
  }

  /** Splits a vowelled word into units (letter + its marks) with their Latin reading. */
  private fun word(w: String): List<ReadingUnit> {
    val units = mutableListOf<ReadingUnit>()
    var i = 0
    var skipShaddaAt = -1
    // Definite article: ال / اَل / ٱل
    if (w.length > 2 && w[0] in alifs) {
      var j = 1
      while (j < w.length && w[j] in marks) j++
      if (j < w.length && w[j] == 'ل') {
        var k = j + 1
        while (k < w.length && w[k] in marks) k++
        val next = w.getOrNull(k)
        if (next != null && next in consonants) {
          val sun = next in sunLetters && next != 'ل'
          val latin = if (sun) "a${consonants.getValue(next)}-" else "al-"
          units += ReadingUnit(w.substring(0, k), latin, if (sun) "the l blends into the next letter" else "the")
          if (sun) skipShaddaAt = k
          i = k
        }
      }
    }
    while (i < w.length) {
      val c = w[i]
      var j = i + 1
      while (j < w.length && w[j] in marks) j++
      val unitMarks = w.substring(i + 1, j)
      val glyph = w.substring(i, j)
      val prevLatin = units.lastOrNull()?.latin.orEmpty()
      val latin: String = when {
        c in alifs && unitMarks.isEmpty() -> when {
          prevLatin.endsWith("an") && units.lastOrNull()?.glyph?.contains(TANWIN_A) == true -> ""
          prevLatin.endsWith("a") -> { replaceLastVowel(units, "ā"); "" }
          units.isEmpty() -> "a"
          else -> "ā"
        }
        c in alifs -> vowelOf(unitMarks, c).ifEmpty { "a" }
        c == 'آ' -> if (units.isEmpty()) "ā" else "ʾā"
        // ة is read -a at a pause; with a case ending inside a sentence it's -at-: tuffāḥatan.
        c == 'ة' && vowelOf(unitMarks, c).isNotEmpty() -> (if (prevLatin.endsWith("a")) "t" else "at") + vowelOf(unitMarks, c)
        c == 'ة' -> if (prevLatin.endsWith("a")) "" else "a"
        c == 'ى' -> if (prevLatin.endsWith("a")) { replaceLastVowel(units, "ā"); "" } else "ā"
        c == 'و' && unitMarks.isEmpty() && prevLatin.endsWith("u") -> { replaceLastVowel(units, "ū"); "" }
        c == 'ي' && unitMarks.isEmpty() && prevLatin.endsWith("i") -> { replaceLastVowel(units, "ī"); "" }
        c in consonants -> {
          val base = if ((c == 'أ' || c == 'إ') && units.isEmpty()) "" else consonants.getValue(c)
          val doubled = if (SHADDA in unitMarks && i != skipShaddaAt) base + base else base
          doubled + vowelOf(unitMarks, c)
        }
        else -> c.toString()
      }
      units += ReadingUnit(glyph, latin, noteFor(c, unitMarks))
      i = j
    }
    return units.filterNot { it.latin.isEmpty() && it.glyph.isBlank() }
  }

  private fun vowelOf(m: String, c: Char): String = when {
    TANWIN_A in m -> "an"
    TANWIN_I in m -> "in"
    TANWIN_U in m -> "un"
    DAGGER_ALIF in m -> "ā"
    FATHA in m -> "a"
    KASRA in m -> "i"
    DAMMA in m -> "u"
    c == 'إ' -> "i"
    c == 'أ' -> "a"
    else -> ""
  }

  private fun noteFor(c: Char, m: String): String? = when {
    SHADDA in m -> "doubled"
    c == 'ة' -> "feminine ending"
    c in alifs && m.isEmpty() -> "long vowel"
    c == 'ع' -> "throat sound"
    else -> null
  }

  /** Turns the previous unit's short vowel into a long one (kitab → kitāb). */
  private fun replaceLastVowel(units: MutableList<ReadingUnit>, long: String) {
    val last = units.removeAt(units.lastIndex)
    units += last.copy(latin = last.latin.dropLast(1) + long)
  }
}
