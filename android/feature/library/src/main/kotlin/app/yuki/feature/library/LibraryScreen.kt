package app.yuki.feature.library

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.yuki.core.data.pack.PackLevel
import app.yuki.core.data.pack.PackRegistry
import app.yuki.core.data.repo.LibraryRow
import app.yuki.core.data.repo.ProgressRepository
import app.yuki.core.designsystem.component.LineBullet
import app.yuki.core.designsystem.component.StatusBadge
import app.yuki.core.designsystem.component.toneText
import app.yuki.core.designsystem.theme.Lines
import app.yuki.core.designsystem.theme.scriptFamily
import app.yuki.core.designsystem.theme.Yuki
import app.yuki.core.model.Lang
import app.yuki.core.model.WordStatus
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class LibraryUiState(
  val langs: List<Lang> = emptyList(),
  val lang: Lang? = null,
  val levels: List<PackLevel> = emptyList(),
  val level: Int? = null,
  val query: String = "",
  val statusFilter: WordStatus? = null,
  val history: HistoryFilter? = null,
  val rows: List<LibraryRow> = emptyList(),
  /** Word ids picked with a long press, for bulk actions (PRD S9). */
  val selected: Set<String> = emptySet(),
)

/** Library filters from your review history (PRD S8). */
enum class HistoryFilter(val label: String) { AGAIN_7D("Again in last 7 days"), TODAY("Studied today") }

@OptIn(FlowPreview::class)
class LibraryViewModel(private val progress: ProgressRepository, packs: PackRegistry) : ViewModel() {
  private val _state = MutableStateFlow(LibraryUiState())
  val state: StateFlow<LibraryUiState> = _state
  private val queries = MutableStateFlow("")

  init {
    packs.packs.onEach { installed ->
      val langs = installed.map { it.lang }.sortedBy { it.ordinal }
      val lang = _state.value.lang?.takeIf { it in langs } ?: langs.firstOrNull()
      _state.value = _state.value.copy(langs = langs)
      if (lang != null) selectLang(lang)
    }.launchIn(viewModelScope)
    queries.debounce(150).onEach { refresh() }.launchIn(viewModelScope)
  }

  fun selectLang(lang: Lang) = viewModelScope.launch {
    val levels = progress.levels(lang)
    _state.value = _state.value.copy(lang = lang, levels = levels, level = levels.firstOrNull()?.level)
    refresh()
  }

  fun selectLevel(level: Int) {
    _state.value = _state.value.copy(level = level)
    viewModelScope.launch { refresh() }
  }

  fun setStatusFilter(status: WordStatus?) {
    _state.value = _state.value.copy(statusFilter = status)
    viewModelScope.launch { refresh() }
  }

  fun setHistory(filter: HistoryFilter?) {
    _state.value = _state.value.copy(history = filter)
    viewModelScope.launch { refresh() }
  }

  fun toggleSelected(wordId: String) {
    val s = _state.value.selected
    _state.value = _state.value.copy(selected = if (wordId in s) s - wordId else s + wordId)
  }

  fun selectAllShown() {
    _state.value = _state.value.copy(selected = _state.value.rows.map { it.word.id }.toSet())
  }

  fun clearSelection() {
    _state.value = _state.value.copy(selected = emptySet())
  }

  /** Bulk actions on the selected words; the list refreshes and the selection clears. */
  fun bulk(action: BulkAction) = viewModelScope.launch {
    val ids = _state.value.selected
    ids.forEach { id ->
      when (action) {
        BulkAction.SUSPEND -> progress.setSuspended(id, true)
        BulkAction.UNSUSPEND -> progress.setSuspended(id, false)
        BulkAction.MARK_KNOWN -> progress.setMarkedKnown(id, true)
        BulkAction.RESET -> progress.resetWord(id)
        BulkAction.STUDY_NOW -> progress.studyNow(id)
      }
    }
    _state.value = _state.value.copy(selected = emptySet())
    refresh()
  }

  fun setQuery(q: String) {
    _state.value = _state.value.copy(query = q)
    queries.value = q
  }

  private suspend fun refresh() {
    val s = _state.value
    val lang = s.lang ?: return
    val rows = if (s.query.isNotBlank()) progress.search(lang, s.query) else s.level?.let { progress.browse(lang, it) }.orEmpty()
    val history = when (s.history) {
      null -> null
      HistoryFilter.AGAIN_7D -> progress.wordsRatedAgainLastWeek(lang)
      HistoryFilter.TODAY -> progress.wordsStudiedToday(lang)
    }
    _state.value = _state.value.copy(
      rows = rows.filter { (s.statusFilter == null || it.status == s.statusFilter) && (history == null || it.word.id in history) },
    )
  }
}

@Composable
fun LibraryScreen(viewModel: LibraryViewModel, contentPadding: PaddingValues, onOpenWord: (String) -> Unit, onOpenGuide: (Lang) -> Unit) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val c = Yuki.colors
  LazyColumn(
    Modifier.fillMaxSize().background(c.neutral.ground).padding(top = contentPadding.calculateTopPadding()),
    contentPadding = PaddingValues(top = 20.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp),
  ) {
    item {
      Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Library", style = MaterialTheme.typography.headlineLarge, color = c.neutral.ink)
        if (state.langs.size > 1) {
          Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.langs.forEach { lang ->
              FilterChip(
                selected = lang == state.lang,
                onClick = { viewModel.selectLang(lang) },
                label = { Text(lang.englishName) },
                leadingIcon = { LineBullet(lang.badge, Lines.of(lang).field, size = 20.dp) },
              )
            }
          }
        }
        state.lang?.takeIf { it != Lang.EN }?.let { lang ->
          AssistChip(onClick = { onOpenGuide(lang) }, label = { Text(if (lang == Lang.ZH) "Pinyin & tones guide" else "How to read ${lang.englishName}") })
        }
        OutlinedTextField(
          value = state.query,
          onValueChange = viewModel::setQuery,
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
          placeholder = { Text(if (state.lang == Lang.ZH) "Search 苹果, pingguo or apple" else "Search a word or its meaning") },
          shape = CircleShape,
        )
        if (state.query.isBlank()) {
          Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.levels.forEach { l ->
              FilterChip(selected = l.level == state.level, onClick = { viewModel.selectLevel(l.level) }, label = { Text("${l.label} · ${l.wordCount}") })
            }
          }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          FilterChip(selected = state.statusFilter == null, onClick = { viewModel.setStatusFilter(null) }, label = { Text("All") })
          WordStatus.entries.forEach { s ->
            FilterChip(selected = state.statusFilter == s, onClick = { viewModel.setStatusFilter(s) }, label = { Text(s.label()) })
          }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          HistoryFilter.entries.forEach { h ->
            FilterChip(selected = state.history == h, onClick = { viewModel.setHistory(if (state.history == h) null else h) }, label = { Text(h.label) })
          }
        }
        if (state.selected.isNotEmpty()) BulkBar(state, viewModel)
        else if (state.rows.isNotEmpty()) Text("Long-press a word to select several.", style = MaterialTheme.typography.bodySmall, color = c.neutral.ink2)
      }
    }
    items(state.rows, key = { it.word.id }) { row ->
      val selecting = state.selected.isNotEmpty()
      WordRow(
        row,
        selected = row.word.id in state.selected,
        onClick = { if (selecting) viewModel.toggleSelected(row.word.id) else onOpenWord(row.word.id) },
        onLongClick = { viewModel.toggleSelected(row.word.id) },
      )
      HorizontalDivider(Modifier.padding(start = 16.dp), color = c.neutral.outline)
    }
  }
}

enum class BulkAction(val label: String) { STUDY_NOW("Study now"), SUSPEND("Suspend"), UNSUSPEND("Unsuspend"), MARK_KNOWN("Mark known"), RESET("Reset") }

@Composable
private fun BulkBar(state: LibraryUiState, viewModel: LibraryViewModel) {
  val c = Yuki.colors
  var confirmReset by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
  Column(
    Modifier.fillMaxWidth().background(c.artTint, androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).padding(12.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text("${state.selected.size} selected", style = MaterialTheme.typography.titleSmall, color = c.neutral.ink, modifier = Modifier.weight(1f))
      androidx.compose.material3.TextButton(onClick = viewModel::selectAllShown) { Text("All ${state.rows.size}") }
      androidx.compose.material3.TextButton(onClick = viewModel::clearSelection) { Text("Cancel") }
    }
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      BulkAction.entries.forEach { a ->
        AssistChip(onClick = { if (a == BulkAction.RESET) confirmReset = true else viewModel.bulk(a) }, label = { Text(a.label) })
      }
    }
  }
  if (confirmReset) {
    androidx.compose.material3.AlertDialog(
      onDismissRequest = { confirmReset = false },
      title = { Text("Reset ${state.selected.size} words?") },
      text = { Text("They go back to new, as if you'd never studied them.") },
      confirmButton = { androidx.compose.material3.TextButton(onClick = { confirmReset = false; viewModel.bulk(BulkAction.RESET) }) { Text("Reset") } },
      dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
    )
  }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun WordRow(row: LibraryRow, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
  val c = Yuki.colors
  val w = row.word
  Row(
    Modifier
      .fillMaxWidth()
      .background(if (selected) c.artTint else androidx.compose.ui.graphics.Color.Transparent)
      .combinedClickable(onClickLabel = "Open", onLongClickLabel = if (selected) "Unselect" else "Select", onLongClick = onLongClick, onClick = onClick)
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      w.headword,
      style = if (w.lang == Lang.ZH || w.lang.rtl) MaterialTheme.typography.titleLarge.copy(fontFamily = scriptFamily(w.lang)) else MaterialTheme.typography.titleMedium,
      color = c.neutral.ink,
    )
    Spacer(Modifier.width(12.dp))
    Column(Modifier.weight(1f)) {
      if (w.lang == Lang.ZH && w.syllables.isNotEmpty()) Text(toneText(w.syllables, w.tones), style = MaterialTheme.typography.bodyMedium)
      else Text(w.reading, style = MaterialTheme.typography.bodyMedium, color = c.neutral.ink2)
      Text(row.gloss, style = MaterialTheme.typography.bodySmall, color = c.neutral.ink2, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.width(8.dp))
    val (container, content) = when (row.status) {
      WordStatus.NEW -> c.neutral.surface2 to c.neutral.ink2
      WordStatus.LEARNING -> c.artTint to c.neutral.ink
      WordStatus.REVIEW -> c.line.field to androidx.compose.ui.graphics.Color.White
      WordStatus.MASTERED -> app.yuki.core.designsystem.theme.Semantic.Gold to c.neutral.ink.let { androidx.compose.ui.graphics.Color(0xFF14161B) }
      WordStatus.SUSPENDED -> c.neutral.surface2 to c.neutral.ink2
    }
    StatusBadge(row.status.label(), container, content)
  }
}

fun WordStatus.label() = when (this) {
  WordStatus.NEW -> "New"
  WordStatus.LEARNING -> "Learning"
  WordStatus.REVIEW -> "Review"
  WordStatus.MASTERED -> "Mastered"
  WordStatus.SUSPENDED -> "Suspended"
}
