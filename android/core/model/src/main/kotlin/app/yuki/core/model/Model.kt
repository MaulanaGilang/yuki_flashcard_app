package app.yuki.core.model

import java.time.Instant

enum class Script { HAN, LATIN, CYRILLIC, ARABIC }

/** A study language. Each one is a "line" in the design system. */
enum class Lang(
  val code: String,
  val lineName: String,
  val badge: String,
  val englishName: String,
  val script: Script,
  val rtl: Boolean = false,
) {
  ZH("zh", "Persimmon", "中", "Mandarin", Script.HAN),
  ES("es", "Bougainvillea", "ES", "Spanish", Script.LATIN),
  RU("ru", "Cobalt", "RU", "Russian", Script.CYRILLIC),
  EN("en", "Jade", "EN", "English", Script.LATIN),
  DE("de", "Ochre", "DE", "German", Script.LATIN),
  FR("fr", "Iris", "FR", "French", Script.LATIN),
  AR("ar", "Olive", "ع", "Arabic", Script.ARABIC, rtl = true);

  /** Needs a transliteration line to be readable by a beginner. */
  val needsTransliteration: Boolean get() = script == Script.CYRILLIC || script == Script.ARABIC

  companion object {
    fun fromCode(code: String): Lang = entries.first { it.code == code }
  }
}

enum class Rating(val value: Int) {
  AGAIN(1), HARD(2), GOOD(3), EASY(4);

  companion object {
    fun fromValue(value: Int): Rating = entries.first { it.value == value }
  }
}

enum class CardState { NEW, LEARNING, REVIEW, RELEARNING }

enum class CardType { RECOGNITION, LISTENING, PRODUCTION, WRITING }

/** FSRS memory state of one card. `due == null` only for NEW cards. */
data class Memory(
  val state: CardState,
  val step: Int?,
  val stability: Double?,
  val difficulty: Double?,
  val due: Instant?,
  val lastReview: Instant?,
  val reps: Int,
  val lapses: Int,
) {
  companion object {
    val NEW = Memory(CardState.NEW, step = null, stability = null, difficulty = null, due = null, lastReview = null, reps = 0, lapses = 0)
  }
}

/** Mastery finish shown on the collectible card (DESIGN-SYSTEM §2.5). */
enum class FoilTier {
  NEW, LEARNING, REVIEW, MASTERED, GOLD;

  companion object {
    const val MASTERED_DAYS = 21.0
    const val GOLD_DAYS = 180.0

    fun of(memory: Memory): FoilTier = when (memory.state) {
      CardState.NEW -> NEW
      CardState.LEARNING, CardState.RELEARNING -> LEARNING
      CardState.REVIEW -> {
        val s = memory.stability ?: 0.0
        when {
          s >= GOLD_DAYS -> GOLD
          s >= MASTERED_DAYS -> MASTERED
          else -> REVIEW
        }
      }
    }
  }
}

/** Library status badge for a word. */
enum class WordStatus { NEW, LEARNING, REVIEW, MASTERED, SUSPENDED }

data class StudyCard(
  val id: String,
  val wordId: String,
  val lang: Lang,
  val type: CardType,
  val memory: Memory,
  val newRank: Int,
)

data class Word(
  val id: String,
  val lang: Lang,
  val headword: String,
  val reading: String,
  val level: Int,
  val levelLabel: String,
  val freqRank: Int,
  val pos: String,
  /** zh only: toned pinyin per character, e.g. ["píng", "guǒ"]. */
  val syllables: List<String> = emptyList(),
  /** zh only: tone per character, 1–4, 5 = neutral. */
  val tones: List<Int> = emptyList(),
  val measureWord: String? = null,
  val gender: String? = null,
  /** Other grammar notes as short chips: aspect pair, plural, "irregular", "uncountable" (PRD §2.2). */
  val grammar: List<String> = emptyList(),
) {
  /** Every grammar chip for the card back, in display order. */
  val chips: List<String> get() = listOfNotNull(measureWord?.let { "量 $it" }, gender) + grammar
}

data class Sense(val pos: String, val gloss: String)

data class Sentence(
  val id: String,
  val text: String,
  val reading: String?,
  val translation: String,
  val audioPath: String?,
  val audioHuman: Boolean,
)

data class AssetRef(val path: String, val human: Boolean, val source: String, val license: String)

data class Hanzi(
  val ch: String,
  val strokeCount: Int,
  val radical: String?,
  /** SVG path data per stroke, Make Me a Hanzi coordinates (1024 box, y up). */
  val strokes: List<String>,
  /** Centre line per stroke as (x, y) points in the same coordinates. */
  val medians: List<List<Pair<Float, Float>>>,
)

data class WordDetail(
  val word: Word,
  val senses: List<Sense>,
  val sentences: List<Sentence>,
  val wordAudio: AssetRef?,
  val image: AssetRef?,
  val characters: List<Hanzi>,
) {
  val gloss: String get() = senses.joinToString("; ") { it.gloss }
}

data class DeckConfig(
  val lang: Lang,
  val newPerDay: Int = 10,
  val maxReviews: Int = 200,
  val desiredRetention: Double = 0.90,
  val learningStepsSeconds: List<Long> = listOf(60, 600),
  val relearningStepsSeconds: List<Long> = listOf(600),
  val showReadingOnFront: Boolean = false,
  val autoplayFront: Boolean = false,
  val listening: Boolean = false,
  val production: Boolean = false,
) {
  /** Card directions studied in this language (PRD S6); recognition is always on. */
  val cardTypes: List<CardType>
    get() = listOfNotNull(CardType.RECOGNITION, CardType.LISTENING.takeIf { listening }, CardType.PRODUCTION.takeIf { production })
}
