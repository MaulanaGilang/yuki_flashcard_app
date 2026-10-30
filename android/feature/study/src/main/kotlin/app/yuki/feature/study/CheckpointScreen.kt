package app.yuki.feature.study

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.yuki.core.data.repo.QuizQuestion
import app.yuki.core.data.repo.StudyRepository
import app.yuki.core.designsystem.component.Headword
import app.yuki.core.designsystem.component.YukiMascot
import app.yuki.core.designsystem.theme.Unbounded
import app.yuki.core.designsystem.theme.Yuki
import app.yuki.core.designsystem.theme.YukiType
import app.yuki.core.model.Lang
import app.yuki.core.model.Mood
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface CheckpointState {
  data object Loading : CheckpointState
  data object Unavailable : CheckpointState
  data class Asking(val question: QuizQuestion, val index: Int, val total: Int, val picked: Int?, val correct: Int) : CheckpointState
  data class Done(val correct: Int, val total: Int, val passed: Boolean) : CheckpointState
}

/**
 * Checkpoint test (PRD §3.5): 20 words from one level, multiple choice. 85 % or better marks
 * the rest of that level as known so you can skip ahead. Answers never touch scheduling.
 */
class CheckpointViewModel(
  val lang: Lang,
  private val level: Int,
  private val repo: StudyRepository,
) : ViewModel() {
  private val _state = MutableStateFlow<CheckpointState>(CheckpointState.Loading)
  val state: StateFlow<CheckpointState> = _state
  private var questions: List<QuizQuestion> = emptyList()
  private var correct = 0

  init {
    viewModelScope.launch {
      questions = repo.checkpoint(lang, level)
      _state.value = if (questions.size < MIN_QUESTIONS) CheckpointState.Unavailable else ask(0)
    }
  }

  private fun ask(i: Int) = CheckpointState.Asking(questions[i], i, questions.size, null, correct)

  fun pick(option: Int) {
    val s = _state.value as? CheckpointState.Asking ?: return
    if (s.picked != null) return
    if (option == s.question.answer) correct++
    _state.value = s.copy(picked = option, correct = correct)
    viewModelScope.launch {
      delay(if (option == s.question.answer) 600 else 1400)
      val next = s.index + 1
      if (next < questions.size) {
        _state.value = ask(next)
      } else {
        val passed = correct * 100 >= PASS_PERCENT * questions.size
        if (passed) repo.markLevelKnown(lang, level)
        _state.value = CheckpointState.Done(correct, questions.size, passed)
      }
    }
  }

  companion object {
    const val PASS_PERCENT = 85
    private const val MIN_QUESTIONS = 5
  }
}

@Composable
fun CheckpointScreen(viewModel: CheckpointViewModel, levelLabel: String, onClose: () -> Unit) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val c = Yuki.colors
  // Drenched in the line colour like a study session: a level test is a big moment (DESIGN-SYSTEM §1).
  Column(
    Modifier.fillMaxSize().background(c.field).windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 20.dp, vertical = 8.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      IconButton(onClick = onClose, modifier = Modifier.semantics { contentDescription = "Close checkpoint" }) {
        Text("✕", color = Color.White, fontSize = 16.sp)
      }
      Text("CHECKPOINT · $levelLabel".uppercase(), style = YukiType.station, color = Color.White.copy(alpha = 0.85f), modifier = Modifier.weight(1f))
      (state as? CheckpointState.Asking)?.let { Text("${it.index + 1}/${it.total}", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.85f)) }
    }
    when (val s = state) {
      CheckpointState.Loading -> Unit
      CheckpointState.Unavailable -> Message(viewModel.lang, Mood.SLEEPY, "Not enough words in $levelLabel for a checkpoint yet.", "Back", onClose)
      is CheckpointState.Asking -> Question(s, onPick = viewModel::pick)
      is CheckpointState.Done -> Message(
        viewModel.lang,
        if (s.passed) Mood.CELEBRATE else Mood.WORRIED,
        if (s.passed) "${s.correct}/${s.total}! The rest of $levelLabel is marked as known, so your new cards move on to the next level."
        else "${s.correct}/${s.total}. You need ${CheckpointViewModel.PASS_PERCENT}% to skip $levelLabel. Keep studying it, and try again any time.",
        "Done",
        onClose,
      )
    }
  }
}

@Composable
private fun Question(s: CheckpointState.Asking, onPick: (Int) -> Unit) {
  val c = Yuki.colors
  Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Box(Modifier.fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = 0.3f), CircleShape)) {
      Box(Modifier.fillMaxWidth((s.index.toFloat() / s.total).coerceAtLeast(0.02f)).height(4.dp).background(Color.White, CircleShape))
    }
    Spacer(Modifier.weight(0.6f))
    // The word sits on a white card, like the front of a study card.
    Column(
      Modifier.fillMaxWidth().background(c.neutral.surface, RoundedCornerShape(24.dp)).padding(vertical = 28.dp, horizontal = 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Headword(s.question.word.headword, s.question.word.lang)
      Text("What does it mean?", style = MaterialTheme.typography.bodyLarge, color = c.neutral.ink2)
    }
    Spacer(Modifier.weight(0.4f))
    s.question.options.forEachIndexed { i, option ->
      val isAnswer = i == s.question.answer
      val revealed = s.picked != null
      val container = when {
        revealed && isAnswer -> c.good
        revealed && i == s.picked -> c.again
        else -> c.neutral.surface
      }
      val content = if (revealed && (isAnswer || i == s.picked)) Color.White else c.neutral.ink
      OutlinedButton(
        onClick = { onPick(i) },
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (revealed && (isAnswer || i == s.picked)) Color.White else c.neutral.outline),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = container, contentColor = content),
      ) { Text(option, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center) }
    }
    Spacer(Modifier.height(12.dp))
  }
}

@Composable
private fun Message(lang: Lang, mood: Mood, text: String, button: String, onClick: () -> Unit) {
  val c = Yuki.colors
  Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
    YukiMascot(mood, size = 168.dp, outfit = lang)
    Spacer(Modifier.height(16.dp))
    Text(text, style = MaterialTheme.typography.bodyLarge, color = Color.White, textAlign = TextAlign.Center)
    Spacer(Modifier.height(24.dp))
    Button(
      onClick = onClick,
      shape = CircleShape,
      modifier = Modifier.width(200.dp).height(48.dp),
      colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = c.line.field),
    ) { Text(button, fontFamily = Unbounded) }
  }
}
