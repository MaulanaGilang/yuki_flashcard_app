package app.yuki.core.model.script

import app.yuki.core.model.Lang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptGuideTest {

  @Test
  fun cyrillicTransliterationKeepsStress() {
    assertEquals("yábloko", CyrillicGuide.transliterate("я́блоко"))
    assertEquals("sobáka", CyrillicGuide.transliterate("соба́ка"))
    assertEquals("segódnya", CyrillicGuide.transliterate("сего́дня"))
    assertEquals("Privét! Kak delá?", CyrillicGuide.transliterate("Приве́т! Как дела́?"))
    assertEquals("dom", CyrillicGuide.transliterate("дом"))
    assertEquals("yesli", CyrillicGuide.transliterate("если"))
  }

  @Test
  fun cyrillicBreakdownMarksStressDevoicingAndAkanye() {
    val units = CyrillicGuide.breakdown("молоко́")
    assertEquals(listOf("m", "o", "l", "o", "k", "ó"), units.map { it.latin })
    assertEquals("unstressed: sounds like a", units[1].note)
    assertEquals("stressed", units[5].note)
    val drug = CyrillicGuide.breakdown("друг")
    assertEquals("at the end: sounds like k", drug.last().note)
    // е after a consonant reads "e", not "ye".
    assertEquals("e", CyrillicGuide.breakdown("сего́дня")[1].latin)
    assertTrue(CyrillicGuide.hints("молоко́").any { "sounds like a" in it })
  }

  @Test
  fun arabicTransliterationOfVowelledWords() {
    assertEquals("kitāb", ArabicGuide.transliterate("كِتَاب"))
    assertEquals("madrasa", ArabicGuide.transliterate("مَدْرَسَة"))
    assertEquals("ash-shams", ArabicGuide.transliterate("اَلشَّمْس"))
    assertEquals("al-qamar", ArabicGuide.transliterate("اَلْقَمَر"))
    assertEquals("shukran", ArabicGuide.transliterate("شُكْرًا"))
    assertEquals("marḥaban", ArabicGuide.transliterate("مَرْحَبًا"))
    assertEquals("anā", ArabicGuide.transliterate("أَنَا"))
    assertEquals("as-salāmu ʿalaykum", ArabicGuide.transliterate("السَّلَامُ عَلَيْكُمْ"))
    assertEquals("ākulu tuffāḥatan kulla yawmin.", ArabicGuide.transliterate("آكُلُ تُفَّاحَةً كُلَّ يَوْمٍ."))
  }

  @Test
  fun arabicBreakdownGivesTheLongVowelToItsOwnLetter() {
    assertEquals(listOf("ki", "ta", "ā", "b"), ArabicGuide.breakdown("كِتَاب").map { it.latin })
    assertEquals("makes the vowel long", ArabicGuide.breakdown("كِتَاب")[2].note)
  }

  @Test
  fun arabicHints() {
    assertTrue(ArabicGuide.hints("اَلشَّمْس").any { "sun letter" in it })
    assertTrue(ArabicGuide.hints("اَلْقَمَر").any { "article" in it })
    assertTrue(ArabicGuide.hints("مَدْرَسَة").any { "-a" in it })
  }

  @Test
  fun latinRuleHints() {
    assertTrue(LatinRules.German.hints("Schule").any { it.startsWith("sch") })
    assertTrue(LatinRules.German.hints("Schule").none { it.startsWith("ch after e") })
    assertTrue(LatinRules.German.hints("ich").any { it.startsWith("ch after e") })
    assertTrue(LatinRules.French.hints("beau").any { it.startsWith("eau") })
    assertTrue(LatinRules.Spanish.hints("hola").any { it.startsWith("h ") })
    assertTrue(LatinRules.Spanish.hints("chico").none { it.startsWith("h ") })
  }

  @Test
  fun guidesPerLanguage() {
    assertEquals(33, CyrillicGuide.alphabet.size)
    assertNull(ScriptGuide.of(Lang.ZH))
    assertTrue(Lang.AR.rtl && Lang.AR.needsTransliteration)
    assertEquals(KanaGuide, ScriptGuide.of(Lang.JA))
    assertTrue(Lang.JA.hasCharacterReading && !Lang.JA.needsTransliteration)
  }

  @Test
  fun kanaToRomaji() {
    assertEquals("taberu", KanaGuide.transliterate("たべる"))
    assertEquals("gakkou", KanaGuide.transliterate("がっこう"))
    assertEquals("kitte", KanaGuide.transliterate("きって"))
    assertEquals("matcha", KanaGuide.transliterate("まっちゃ"))
    assertEquals("toukyou", KanaGuide.transliterate("とうきょう"))
    assertEquals("shashin", KanaGuide.transliterate("しゃしん"))
    assertEquals("kon'ya", KanaGuide.transliterate("こんや"))
    assertEquals("hon'ya", KanaGuide.transliterate("ほんや"))
    assertEquals("konnichiha", KanaGuide.transliterate("こんにちは"))
    assertEquals("koohii", KanaGuide.transliterate("コーヒー"))
    assertEquals("paatii", KanaGuide.transliterate("パーティー"))
    assertEquals("fairu", KanaGuide.transliterate("ファイル"))
    assertEquals("chiizu", KanaGuide.transliterate("チーズ"))
    assertEquals("watashi ha gakusei desu.", KanaGuide.transliterate("わたし は がくせい です。"))
  }

  @Test
  fun kanaBreakdown() {
    val units = KanaGuide.breakdown("きって")
    assertEquals(listOf("き", "っ", "て"), units.map { it.glyph })
    assertEquals("ki", units[0].latin)
    assertEquals(listOf("きょ", "う"), KanaGuide.breakdown("きょう").map { it.glyph })
    assertEquals(46, KanaGuide.alphabet.size)
    assertEquals("あ ア", KanaGuide.alphabet.first().glyph)
  }
}
