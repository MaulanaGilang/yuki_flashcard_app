package app.yuki.core.model.script

import app.yuki.core.model.Lang

/** One letter of an alphabet, for the alphabet guide. */
data class Letter(
  val glyph: String,
  val name: String,
  val latin: String,
  /** How it sounds, described for an English speaker. */
  val sound: String,
  /** Warning when the letter looks like a Latin one but sounds different. */
  val lookAlike: String? = null,
)

/** One piece of a word read aloud: the written unit and how it's pronounced. */
data class ReadingUnit(val glyph: String, val latin: String, val note: String? = null)

/** A spelling-to-sound rule (Latin-script languages), with the pattern it detects in a word. */
data class SpellingRule(val spelling: String, val sounds: String, val example: String, internal val pattern: Regex)

/**
 * "How to read" support (PRD S17). Non-Latin scripts get a transliteration and a letter-by-letter
 * breakdown; Latin-script languages get the spelling rules that differ from English.
 */
interface ScriptGuide {
  val title: String
  val intro: String
  val alphabet: List<Letter>
  val rules: List<SpellingRule> get() = emptyList()

  /** Latin-letter reading of [text]; empty for Latin-script languages. */
  fun transliterate(text: String): String = ""

  /** Letter-by-letter reading of a single word. */
  fun breakdown(word: String): List<ReadingUnit> = emptyList()

  /** Reading tips that apply to this particular word. */
  fun hints(word: String): List<String> =
    rules.filter { it.pattern.containsMatchIn(word.lowercase()) }.map { "${it.spelling} sounds like ${it.sounds} (${it.example})" }

  companion object {
    fun of(lang: Lang): ScriptGuide? = when (lang) {
      Lang.RU -> CyrillicGuide
      Lang.AR -> ArabicGuide
      Lang.DE -> LatinRules.German
      Lang.FR -> LatinRules.French
      Lang.ES -> LatinRules.Spanish
      Lang.JA -> KanaGuide
      Lang.ZH, Lang.EN -> null
    }
  }
}
