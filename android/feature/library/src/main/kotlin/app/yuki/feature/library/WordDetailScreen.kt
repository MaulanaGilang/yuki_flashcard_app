package app.yuki.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.yuki.core.data.db.ReviewLogEntity
import app.yuki.core.data.media.AudioPlayer
import app.yuki.core.data.repo.ProgressRepository
import app.yuki.core.data.repo.StudyRepository
import app.yuki.core.designsystem.component.AudioButton
import app.yuki.core.designsystem.component.CardDivider
import app.yuki.core.designsystem.component.GrammarChip
import app.yuki.core.designsystem.component.Headword
import app.yuki.core.designsystem.component.SectionLabel
import app.yuki.core.designsystem.component.HowToWrite
import app.yuki.core.designsystem.component.toneText
import app.yuki.core.designsystem.component.HowToReadPanel
import app.yuki.core.designsystem.component.ScriptText
import app.yuki.core.designsystem.component.TransliterationLine
import app.yuki.core.model.script.ScriptGuide
import app.yuki.core.designsystem.theme.Yuki
import app.yuki.core.designsystem.theme.YukiType
import app.yuki.core.model.FoilTier
import app.yuki.core.model.Lang
import app.yuki.core.model.WordDetail
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class WordDetailUiState(
  val detail: WordDetail? = null,
  val history: List<ReviewLogEntity> = emptyList(),
  val suspended: Boolean = false,
  val markedKnown: Boolean = false,
  val dueText: String? = null,
  val foil: FoilTier = FoilTier.NEW,
)

class WordDetailViewModel(
  private val wordId: String,
  private val study: StudyRepository,
  private val progress: ProgressRepository,
  private val audio: AudioPlayer,
) : ViewModel() {
  private val _state = MutableStateFlow(WordDetailUiState())
  val state: StateFlow<WordDetailUiState> = _state

  init {
    refresh()
  }

  private fun refresh() = viewModelScope.launch {
    val detail = study.detail(wordId)
    val card = progress.cardsForWord(wordId).firstOrNull()
    val due = card?.due?.atZone(ZoneId.systemDefault())?.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))
    _state.value = WordDetailUiState(
      detail = detail,
      history = progress.historyForWord(wordId),
      suspended = card?.suspended == true,
      markedKnown = card?.markedKnown == true,
      dueText = due,
      foil = card?.let { FoilTier.of(it.memory) } ?: FoilTier.NEW,
    )
    detail?.let { d -> audio.preload(wordId, listOfNotNull(d.wordAudio?.path) + d.sentences.mapNotNull { it.audioPath }) }
  }

  fun toggleSuspended() = viewModelScope.launch {
    progress.setSuspended(wordId, !_state.value.suspended)
    refresh()
  }

  fun toggleKnown() = viewModelScope.launch {
    progress.setMarkedKnown(wordId, !_state.value.markedKnown)
    refresh()
  }

  fun play(path: String) = audio.play(wordId, path)
}

@Composable
fun WordDetailScreen(viewModel: WordDetailViewModel, onBack: () -> Unit) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val c = Yuki.colors
  val detail = state.detail
  Column(
    Modifier
      .fillMaxSize()
      .background(c.neutral.ground)
      .windowInsetsPadding(WindowInsets.safeDrawing)
      .verticalScroll(rememberScrollState())
      .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Back" }) { Text("←", fontSize = 22.sp, color = c.neutral.ink) }
    if (detail == null) return@Column
    val word = detail.word
    val isZh = word.lang == Lang.ZH
    Column(
      Modifier.fillMaxWidth().background(c.neutral.surface, RoundedCornerShape(24.dp)).padding(20.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      SectionLabel("${word.levelLabel} · ${word.pos} · ${state.foil.name.lowercase()}")
      Headword(word.headword, word.lang)
      if (isZh && word.syllables.isNotEmpty()) Text(toneText(word.syllables, word.tones, separator = " "), style = YukiType.reading)
      else {
        if (word.lang.needsTransliteration) TransliterationLine(word.headword, word.lang)
        Text(word.reading, style = YukiType.reading, color = c.neutral.ink2)
      }
      detail.wordAudio?.let { a ->
        Spacer(Modifier.height(8.dp))
        AudioButton({ viewModel.play(a.path) }, enabled = true, contentDescription = "Play pronunciation")
        Text(if (a.human) "Recorded by a native speaker" else "Neural voice", style = MaterialTheme.typography.bodySmall, color = c.neutral.ink2)
      }
    }
    Column(Modifier.fillMaxWidth().background(c.neutral.surface, RoundedCornerShape(24.dp)).padding(20.dp)) {
      SectionLabel("Meaning")
      detail.senses.forEach { s -> Text("${s.pos}  ${s.gloss}".trim(), style = MaterialTheme.typography.bodyLarge, color = c.neutral.ink, modifier = Modifier.padding(top = 4.dp)) }
      val chips = word.chips
      if (chips.isNotEmpty()) FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { chips.forEach { GrammarChip(it) } }
      if (detail.sentences.isNotEmpty()) {
        CardDivider()
        SectionLabel("Examples")
        detail.sentences.forEach { s ->
          Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
              ScriptText(s.text, word.lang)
              val reading = s.reading ?: if (word.lang.needsTransliteration) ScriptGuide.of(word.lang)?.transliterate(s.text) else null
              reading?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.neutral.ink2) }
              Text(s.translation, style = MaterialTheme.typography.bodyMedium, color = c.neutral.ink2)
            }
            s.audioPath?.let { p -> AudioButton({ viewModel.play(p) }, enabled = true, contentDescription = "Play sentence", small = true) }
          }
        }
      }
    }
    val guide = ScriptGuide.of(word.lang)
    if (guide != null && (word.lang.needsTransliteration || guide.hints(word.headword).isNotEmpty())) {
      Column(Modifier.fillMaxWidth().background(c.neutral.surface, RoundedCornerShape(24.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionLabel("How to read")
        HowToReadPanel(word.headword, word.lang)
      }
    }
    if (isZh && detail.characters.isNotEmpty()) {
      Column(Modifier.fillMaxWidth().background(c.neutral.surface, RoundedCornerShape(24.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        HowToWrite(detail.characters)
      }
    }
    Column(Modifier.fillMaxWidth().background(c.neutral.surface, RoundedCornerShape(24.dp)).padding(20.dp)) {
      SectionLabel("Your history")
      if (state.history.isEmpty()) {
        Text("Not studied yet.", style = MaterialTheme.typography.bodyMedium, color = c.neutral.ink2, modifier = Modifier.padding(top = 4.dp))
      } else {
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          state.history.takeLast(30).forEach { log ->
            val color = when (log.rating) { 1 -> c.again; 2 -> c.neutral.ink2; 3 -> c.line.field; else -> c.line.deep }
            Box(Modifier.size(12.dp).background(color, CircleShape))
          }
        }
        Text(
          "${state.history.size} reviews" + (state.dueText?.let { " · next on $it" } ?: ""),
          style = MaterialTheme.typography.bodyMedium, color = c.neutral.ink2, modifier = Modifier.padding(top = 6.dp),
        )
      }
      Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = viewModel::toggleSuspended, shape = CircleShape) { Text(if (state.suspended) "Unsuspend" else "Suspend") }
        OutlinedButton(onClick = viewModel::toggleKnown, shape = CircleShape) { Text(if (state.markedKnown) "Unmark known" else "Mark as known") }
      }
    }
  }
}
