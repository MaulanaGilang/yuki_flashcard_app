package app.yuki.feature.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yuki.core.data.media.AudioPlayer
import app.yuki.core.data.pack.PackRegistry
import app.yuki.core.data.repo.StudyRepository
import app.yuki.core.data.repo.StudySession
import app.yuki.core.designsystem.component.Art
import app.yuki.core.designsystem.component.formatInterval
import app.yuki.core.model.CardState
import app.yuki.core.model.FoilTier
import app.yuki.core.model.Lang
import app.yuki.core.model.Rating
import app.yuki.core.model.StudyCard
import app.yuki.core.model.WordDetail
import app.yuki.core.scheduler.Answer
import java.time.Clock
import java.time.Duration
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class CardUi(
  val card: StudyCard,
  val detail: WordDetail,
  val art: Art,
  val flipped: Boolean,
  val foil: FoilTier,
  val intervals: Map<Rating, String>,
  val position: Int,
  val total: Int,
  val canUndo: Boolean,
  val showReadingOnFront: Boolean,
)

data class SessionSummary(
  val lang: Lang,
  val cards: Int,
  val newLearned: Int,
  val accuracyPercent: Int,
  val minutes: Int,
  val newFoils: Int,
  val missed: List<String>,
)

sealed interface StudyUiState {
  data object Loading : StudyUiState
  data class Empty(val lang: Lang) : StudyUiState
  data class Studying(val ui: CardUi) : StudyUiState
  data class Finished(val summary: SessionSummary) : StudyUiState
}

sealed interface StudyEffect {
  data class Rated(val rating: Rating) : StudyEffect
}

class StudyViewModel(
  private val lang: Lang,
  private val repo: StudyRepository,
  private val packs: PackRegistry,
  private val audio: AudioPlayer,
  private val clock: Clock,
) : ViewModel() {
  private val _state = MutableStateFlow<StudyUiState>(StudyUiState.Loading)
  val state: StateFlow<StudyUiState> = _state
  private val effects = Channel<StudyEffect>(Channel.BUFFERED)
  val effect = effects.receiveAsFlow()

  private var session: StudySession? = null
  private val answers = ArrayDeque<Pair<Answer, Long>>()
  private val details = HashMap<String, WordDetail>()
  private var shownAt = clock.instant()
  private val startedAt = clock.instant()
  private var showReadingOnFront = false

  init {
    viewModelScope.launch {
      showReadingOnFront = repo.deckConfig(lang).showReadingOnFront
      val s = repo.startSession(lang)
      session = s
      if (s.total == 0) _state.value = StudyUiState.Empty(lang) else showCurrent()
    }
  }

  private suspend fun showCurrent() {
    val s = session ?: return
    val now = clock.instant()
    val card = s.engine.current(now)
    if (card == null) {
      _state.value = StudyUiState.Finished(summary())
      return
    }
    val detail = details[card.wordId] ?: repo.detail(card.wordId)?.also { details[card.wordId] = it }
    if (detail == null) {
      // Content missing for this card (e.g. orphaned word): skip it without scheduling.
      _state.value = StudyUiState.Finished(summary())
      return
    }
    val previews = s.engine.preview(now).orEmpty().mapValues { (_, due) -> formatInterval(Duration.between(now, due).seconds) }
    audio.preload(card.wordId, listOfNotNull(detail.wordAudio?.path) + detail.sentences.mapNotNull { it.audioPath })
    shownAt = now
    _state.value = StudyUiState.Studying(
      CardUi(
        card = card,
        detail = detail,
        art = artFor(card, detail),
        flipped = false,
        foil = FoilTier.of(card.memory),
        intervals = previews,
        position = s.engine.answeredCount + 1,
        total = s.engine.answeredCount + s.engine.remaining,
        canUndo = answers.isNotEmpty(),
        showReadingOnFront = showReadingOnFront,
      ),
    )
  }

  private fun artFor(card: StudyCard, detail: WordDetail): Art {
    val image = detail.image
    val pack = packs.packForWord(card.wordId)
    if (image != null && pack != null) packs.readMediaBytes(pack, image.path)?.let { return Art.Picture(it) }
    val keyword = detail.senses.firstOrNull()?.gloss?.substringBefore(';')?.substringBefore(',')?.trim().orEmpty()
    return Art.Typographic(keyword.ifEmpty { detail.word.headword })
  }

  fun flip() {
    val current = (_state.value as? StudyUiState.Studying)?.ui ?: return
    _state.value = StudyUiState.Studying(current.copy(flipped = !current.flipped))
  }

  fun rate(rating: Rating) {
    val s = session ?: return
    val current = (_state.value as? StudyUiState.Studying)?.ui ?: return
    if (!current.flipped) return
    viewModelScope.launch {
      val now = clock.instant()
      val answer = s.engine.answer(rating, now)
      val logId = repo.persist(s, answer, Duration.between(shownAt, now).toMillis())
      answers.addLast(answer to logId)
      effects.send(StudyEffect.Rated(rating))
      showCurrent()
    }
  }

  fun undo() {
    val s = session ?: return
    val (_, logId) = answers.removeLastOrNull() ?: return
    viewModelScope.launch {
      val undone = s.engine.undo() ?: return@launch
      repo.revert(undone, logId)
      showCurrent()
    }
  }

  fun playWord() {
    val ui = (_state.value as? StudyUiState.Studying)?.ui ?: return
    ui.detail.wordAudio?.let { audio.play(ui.card.wordId, it.path) }
  }

  fun playSentence(index: Int) {
    val ui = (_state.value as? StudyUiState.Studying)?.ui ?: return
    ui.detail.sentences.getOrNull(index)?.audioPath?.let { audio.play(ui.card.wordId, it) }
  }

  private fun summary(): SessionSummary {
    val list = answers.map { it.first }
    val distinctCards = list.map { it.card.id }.distinct().size
    val missedIds = list.filter { it.rating == Rating.AGAIN }.map { it.card.wordId }.distinct()
    val foils = list.groupBy { it.card.id }.count { (_, a) ->
      val first = FoilTier.of(a.first().before)
      val last = FoilTier.of(a.last().after)
      last.ordinal > first.ordinal && last.ordinal >= FoilTier.MASTERED.ordinal
    }
    return SessionSummary(
      lang = lang,
      cards = distinctCards,
      newLearned = list.filter { it.before.state == CardState.NEW }.map { it.card.id }.distinct().size,
      accuracyPercent = if (list.isEmpty()) 0 else list.count { it.rating != Rating.AGAIN } * 100 / list.size,
      minutes = Duration.between(startedAt, clock.instant()).toMinutes().toInt().coerceAtLeast(if (list.isEmpty()) 0 else 1),
      newFoils = foils,
      missed = missedIds.mapNotNull { details[it]?.word?.headword },
    )
  }
}
