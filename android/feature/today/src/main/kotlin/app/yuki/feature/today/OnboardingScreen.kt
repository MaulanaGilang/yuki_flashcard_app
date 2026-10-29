package app.yuki.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.yuki.core.data.pack.PackLevel
import app.yuki.core.data.pack.PackRegistry
import app.yuki.core.data.repo.StudyRepository
import app.yuki.core.data.settings.AppSettings
import app.yuki.core.designsystem.component.LineBullet
import app.yuki.core.designsystem.component.MascotGreeting
import app.yuki.core.designsystem.component.SectionLabel
import app.yuki.core.designsystem.theme.Lines
import app.yuki.core.designsystem.theme.Yuki
import app.yuki.core.model.Lang
import app.yuki.core.model.Mood
import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OnboardingStep { WELCOME, LANGUAGES, LEVELS, GOAL, REMINDER }

data class OnboardingState(
  val step: OnboardingStep = OnboardingStep.WELCOME,
  val available: List<Pair<Lang, List<PackLevel>>> = emptyList(),
  val chosen: Set<Lang> = emptySet(),
  /** Starting level per language; everything below it is marked known. */
  val startLevel: Map<Lang, Int> = emptyMap(),
  val minutes: Int = 10,
  val reminder: LocalTime? = LocalTime.of(19, 0),
  val saving: Boolean = false,
)

/** First run (PRD S10): languages, starting level, daily goal and reminder. */
class OnboardingViewModel(
  private val packs: PackRegistry,
  private val study: StudyRepository,
  private val settings: AppSettings,
) : ViewModel() {
  private val _state = MutableStateFlow(OnboardingState())
  val state: StateFlow<OnboardingState> = _state

  init {
    viewModelScope.launch {
      packs.awaitReady()
      val available = packs.packs.value.sortedBy { it.lang.ordinal }.map { it.lang to it.reader.levels() }
      _state.update { s ->
        s.copy(
          available = available,
          chosen = setOfNotNull(available.firstOrNull()?.first),
          startLevel = available.associate { (lang, levels) -> lang to (levels.firstOrNull()?.level ?: 1) },
        )
      }
    }
  }

  fun go(step: OnboardingStep) = _state.update { it.copy(step = step) }

  fun toggle(lang: Lang) = _state.update { s -> s.copy(chosen = if (lang in s.chosen) s.chosen - lang else s.chosen + lang) }

  fun setLevel(lang: Lang, level: Int) = _state.update { it.copy(startLevel = it.startLevel + (lang to level)) }

  fun setMinutes(m: Int) = _state.update { it.copy(minutes = m) }

  fun setReminder(t: LocalTime?) = _state.update { it.copy(reminder = t) }

  /** Saves everything; [onDone] gets the reminder time so the app can schedule it. */
  fun finish(onDone: (LocalTime?) -> Unit) {
    val s = _state.value
    if (s.saving) return
    _state.update { it.copy(saving = true) }
    viewModelScope.launch {
      val newPerDay = minutesToNewCards(s.minutes) / s.chosen.size.coerceAtLeast(1)
      for (lang in s.chosen) {
        study.markKnownBelowLevel(lang, s.startLevel[lang] ?: 1)
        study.saveDeckConfig(study.deckConfig(lang).copy(newPerDay = newPerDay.coerceAtLeast(3)))
      }
      settings.setActiveLangs(s.chosen)
      settings.setReminderTime(s.reminder)
      settings.setOnboarded()
      onDone(s.reminder)
    }
  }

  companion object {
    /** About one new card per minute of daily study, with reviews growing behind them. */
    fun minutesToNewCards(minutes: Int) = minutes
  }
}

@Composable
fun OnboardingScreen(viewModel: OnboardingViewModel, onFinished: (LocalTime?) -> Unit) {
  val s by viewModel.state.collectAsStateWithLifecycle()
  val c = Yuki.colors
  Column(
    Modifier
      .fillMaxSize()
      .background(c.neutral.ground)
      .windowInsetsPadding(WindowInsets.safeDrawing)
      .padding(20.dp),
  ) {
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      when (s.step) {
        OnboardingStep.WELCOME -> {
          Spacer(Modifier.height(40.dp))
          MascotGreeting(Mood.WAVE, "Hi, I'm Yuki! I'll help you learn words a little every day, and remember them for years.")
          Text("Let's set up your study plan. It takes a minute, and you can change all of it later in Settings.", style = MaterialTheme.typography.bodyLarge, color = c.neutral.ink2)
        }
        OnboardingStep.LANGUAGES -> {
          Title("Which languages?")
          s.available.forEach { (lang, _) ->
            Row(
              Modifier
                .fillMaxWidth()
                .background(c.neutral.surface, RoundedCornerShape(16.dp))
                .clickable(role = Role.Checkbox) { viewModel.toggle(lang) }
                .padding(horizontal = 14.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              LineBullet(lang.badge, Lines.of(lang).field, size = 28.dp)
              Spacer(Modifier.width(10.dp))
              Text(lang.englishName, style = MaterialTheme.typography.titleMedium, color = c.neutral.ink, modifier = Modifier.weight(1f))
              Checkbox(checked = lang in s.chosen, onCheckedChange = null)
            }
          }
        }
        OnboardingStep.LEVELS -> {
          Title("Where do you start?")
          Text("Words below your starting level are marked as known, so you don't see them as new cards. You can still find them in the Library.", style = MaterialTheme.typography.bodyMedium, color = c.neutral.ink2)
          s.available.filter { it.first in s.chosen }.forEach { (lang, levels) ->
            Column(Modifier.fillMaxWidth().background(c.neutral.surface, RoundedCornerShape(16.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              SectionLabel(lang.englishName)
              FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                levels.forEachIndexed { i, lvl ->
                  FilterChip(
                    selected = s.startLevel[lang] == lvl.level,
                    onClick = { viewModel.setLevel(lang, lvl.level) },
                    label = { Text(if (i == 0) "${lvl.label} · from scratch" else lvl.label) },
                  )
                }
              }
            }
          }
          Text("Not sure? Start low. Each level also has a checkpoint test on Today to skip ahead.", style = MaterialTheme.typography.bodySmall, color = c.neutral.ink2)
        }
        OnboardingStep.GOAL -> {
          Title("Daily goal")
          Text("About one new word per minute. Reviews of earlier words come on top as your deck grows.", style = MaterialTheme.typography.bodyMedium, color = c.neutral.ink2)
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(5, 10, 15, 20).forEach { m ->
              FilterChip(selected = s.minutes == m, onClick = { viewModel.setMinutes(m) }, label = { Text("$m min") })
            }
          }
          val per = OnboardingViewModel.minutesToNewCards(s.minutes) / s.chosen.size.coerceAtLeast(1)
          Text("That's ${per.coerceAtLeast(3)} new cards a day" + if (s.chosen.size > 1) " per language." else ".", style = MaterialTheme.typography.bodyLarge, color = c.neutral.ink)
        }
        OnboardingStep.REMINDER -> {
          Title("A daily nudge?")
          Text("Yuki reminds you only on days you haven't studied yet and cards are waiting.", style = MaterialTheme.typography.bodyMedium, color = c.neutral.ink2)
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(LocalTime.of(7, 30), LocalTime.of(12, 0), LocalTime.of(19, 0), LocalTime.of(21, 0)).forEach { t ->
              FilterChip(selected = s.reminder == t, onClick = { viewModel.setReminder(t) }, label = { Text(t.toString()) })
            }
            FilterChip(selected = s.reminder == null, onClick = { viewModel.setReminder(null) }, label = { Text("No reminder") })
          }
          Text("On OPPO / ColorOS, allow Yuki to run in the background (Settings › Battery › Yuki) so reminders arrive on time.", style = MaterialTheme.typography.bodySmall, color = c.neutral.ink2)
        }
      }
    }
    val steps = OnboardingStep.entries
    val i = steps.indexOf(s.step)
    Row(verticalAlignment = Alignment.CenterVertically) {
      if (i > 0) TextButton(onClick = { viewModel.go(steps[i - 1]) }) { Text("Back") }
      Spacer(Modifier.weight(1f))
      val last = s.step == OnboardingStep.REMINDER
      Button(
        onClick = { if (last) viewModel.finish(onFinished) else viewModel.go(steps[i + 1]) },
        enabled = !s.saving && (s.step != OnboardingStep.LANGUAGES || s.chosen.isNotEmpty()) && (s.available.isNotEmpty() || s.step == OnboardingStep.WELCOME),
        shape = CircleShape,
        modifier = Modifier.height(52.dp),
        colors = ButtonDefaults.buttonColors(containerColor = c.line.field, contentColor = Color.White),
      ) { Text(if (last) "Start learning" else if (i == 0) "Let's go" else "Next", style = MaterialTheme.typography.titleMedium) }
    }
  }
}

@Composable
private fun Title(text: String) {
  Spacer(Modifier.height(16.dp))
  Text(text, style = MaterialTheme.typography.headlineMedium, color = Yuki.colors.neutral.ink)
}
