package app.yuki.feature.today

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.yuki.core.data.repo.LineSummary
import app.yuki.core.data.repo.ProgressRepository
import app.yuki.core.designsystem.component.LineBullet
import app.yuki.core.designsystem.component.RouteStrip
import app.yuki.core.designsystem.component.Station
import app.yuki.core.designsystem.theme.Lines
import app.yuki.core.designsystem.theme.Yuki
import app.yuki.core.model.Lang
import app.yuki.core.designsystem.component.MascotGreeting
import app.yuki.core.designsystem.theme.Unbounded
import app.yuki.core.model.CoachLine
import app.yuki.core.model.DayStatus
import app.yuki.core.model.YukiCoach
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import app.yuki.core.scheduler.PaceGuard
import app.yuki.core.scheduler.PaceReason
import app.yuki.core.scheduler.Progression
import app.yuki.core.scheduler.StationState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class TodayUiState(val lines: List<LineSummary>, val streak: Int, val studiedToday: Boolean, val loading: Boolean) {
  /** Yuki's mood and line for the header (PRD S19). */
  val coach: CoachLine
    get() = YukiCoach.line(
      DayStatus(
        streak = streak,
        studiedToday = studiedToday,
        dueToday = lines.sumOf { it.dueToday + it.newToday },
        hour = java.time.LocalTime.now().hour,
        hasLines = lines.isNotEmpty(),
      ),
    )
}

class TodayViewModel(progress: ProgressRepository) : ViewModel() {
  val state: StateFlow<TodayUiState> =
    combine(progress.observeLines(), progress.observeStreak(), progress.observeStudiedToday()) { lines, streak, studied ->
      TodayUiState(lines, streak, studied, loading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState(emptyList(), 0, false, loading = true))
}

@Composable
fun TodayScreen(
  viewModel: TodayViewModel,
  contentPadding: PaddingValues,
  onStart: (Lang) -> Unit,
  onCheckpoint: (Lang, Int, String) -> Unit = { _, _, _ -> },
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val c = Yuki.colors
  LazyColumn(
    // Pad (not contentPadding) for the status bar so scrolled cards never slide under the clock.
    Modifier.fillMaxSize().background(c.neutral.ground).padding(top = contentPadding.calculateTopPadding()),
    contentPadding = PaddingValues(
      start = 16.dp, end = 16.dp,
      top = 20.dp,
      bottom = contentPadding.calculateBottomPadding() + 24.dp,
    ),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text("Yuki", style = MaterialTheme.typography.headlineLarge, color = c.neutral.ink)
          val date = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.ENGLISH))
          Text(date, style = MaterialTheme.typography.bodyMedium, color = c.neutral.ink2, modifier = Modifier.padding(top = 2.dp))
        }
        StreakChip(state.streak, state.studiedToday)
      }
      if (!state.loading) {
        val coach = state.coach
        MascotGreeting(coach.mood, coach.message, Modifier.padding(top = 8.dp, bottom = 4.dp))
      }
    }
    if (!state.loading && state.lines.isEmpty()) {
      item {
        Text(
          "No language packs are installed yet. A pack adds a line, its stations and all its cards.",
          style = MaterialTheme.typography.bodyLarge,
          color = c.neutral.ink2,
        )
      }
    }
    items(state.lines, key = { it.lang.code }) { line ->
      LineCard(line, onStart = { onStart(line.lang) }, onCheckpoint = { lvl -> onCheckpoint(line.lang, lvl.level, lvl.label) })
    }
  }
}

/** Flame + day count, like Duolingo's streak; grey until today's session is done. */
@Composable
private fun StreakChip(streak: Int, studiedToday: Boolean) {
  val c = Yuki.colors
  val lit = studiedToday && streak > 0
  Row(
    Modifier
      .background(if (lit) c.artTint else c.neutral.surface, RoundedCornerShape(50))
      .padding(horizontal = 14.dp, vertical = 8.dp)
      .semantics(mergeDescendants = true) { contentDescription = "$streak-day streak" + if (studiedToday) ", done today" else ", not studied yet today" },
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text("🔥", fontSize = 18.sp, modifier = Modifier.graphicsLayer { alpha = if (lit) 1f else 0.45f })
    Spacer(Modifier.width(6.dp))
    Text("$streak", style = MaterialTheme.typography.titleLarge.copy(fontFamily = Unbounded), color = if (lit) c.line.field else c.neutral.ink2)
  }
}

@Composable
private fun LineCard(line: LineSummary, onStart: () -> Unit, onCheckpoint: (app.yuki.core.data.repo.LevelProgress) -> Unit) {
  val c = Yuki.colors
  val color = Lines.of(line.lang).let { if (c.isDark) it.nightField else it.field }
  val current = line.currentLevel
  val currentIndex = line.levels.indexOf(current).coerceAtLeast(0)
  val work = line.dueToday + line.newToday
  Column(
    Modifier
      .fillMaxWidth()
      .background(c.neutral.surface, RoundedCornerShape(20.dp))
      .padding(16.dp),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      LineBullet(line.lang.badge, color, size = 32.dp)
      Spacer(Modifier.width(10.dp))
      Column(Modifier.weight(1f)) {
        Text(line.lang.englishName, style = MaterialTheme.typography.titleMedium, color = c.neutral.ink)
        Text(current?.let { "Now at ${it.label}" } ?: "Not started", style = MaterialTheme.typography.bodySmall, color = c.neutral.ink2)
      }
      Text("${line.dueToday} due · ${line.newToday} new", style = MaterialTheme.typography.labelLarge, color = c.neutral.ink2)
    }
    Spacer(Modifier.height(12.dp))
    RouteStrip(
      stations = line.levels.map { Station(it.label.replace("HSK ", "HSK"), it.masteredFraction) },
      currentIndex = currentIndex,
      color = color,
      contentDescription = current?.let { "${it.label}, ${it.mastered} of ${it.total} words mastered, current station" } ?: "No levels",
    )
    current?.let { lvl ->
      val next = line.levels.getOrNull(line.levels.indexOf(lvl) + 1)
      val arrived = line.justArrived
      val text = when {
        arrived != null && arrived != lvl && arrived.state == StationState.ARRIVED && lvl.started == 0 ->
          "Arrived at ${arrived.label}. ${lvl.label} words start with your next new cards."
        lvl.started < lvl.total -> "${lvl.label}: ${lvl.started} of ${lvl.total} words introduced, ${lvl.inReview} in review."
        else -> "${lvl.label}: ${lvl.inReview} of ${lvl.total} in review. You arrive at ${(lvl.total * Progression.ARRIVE_REVIEW_SHARE).toInt()}" +
          (next?.let { ", then ${it.label} opens up." } ?: ".")
      }
      Text(text, style = MaterialTheme.typography.bodySmall, color = c.neutral.ink2, modifier = Modifier.padding(top = 8.dp))
    }
    line.pace.reason?.let { reason ->
      val pct = line.pace.retention?.let { "${(it * 100).toInt()}%" } ?: "low"
      val msg = when (reason) {
        PaceReason.PAUSED -> "Recall this week is $pct. New words are paused until it's back above ${(PaceGuard.SLOW_BELOW * 100).toInt()}%."
        PaceReason.STRUGGLING -> "Recall this week is $pct. Yuki is halving new words until it's back above ${(PaceGuard.SLOW_BELOW * 100).toInt()}%."
        PaceReason.BACKLOG -> "Lots of reviews are due, so new words are halved today."
      }
      Text(
        msg,
        style = MaterialTheme.typography.bodySmall,
        color = c.neutral.ink,
        modifier = Modifier.padding(top = 8.dp).fillMaxWidth().background(c.artTint, RoundedCornerShape(10.dp)).padding(10.dp),
      )
    }
    Spacer(Modifier.height(12.dp))
    Button(
      onClick = onStart,
      enabled = work > 0,
      modifier = Modifier.fillMaxWidth().height(48.dp),
      shape = CircleShape,
      colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
    ) {
      val minutes = ((line.dueToday * 8 + line.newToday * 20) / 60).coerceAtLeast(1)
      Text(if (work > 0) "Start · ~$minutes min" else "All caught up", style = MaterialTheme.typography.titleSmall)
    }
    current?.takeIf { it.state == StationState.NOT_STARTED || it.state == StationState.RIDING }?.let { lvl ->
      androidx.compose.material3.TextButton(onClick = { onCheckpoint(lvl) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
        Text("Already know ${lvl.label}? Take the checkpoint", style = MaterialTheme.typography.labelLarge, color = color)
      }
    }
  }
}
