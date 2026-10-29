package app.yuki.feature.today

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.yuki.core.data.repo.LineSummary
import app.yuki.core.data.repo.ProgressRepository
import app.yuki.core.data.repo.Stats
import app.yuki.core.data.repo.StatsRepository
import app.yuki.core.designsystem.component.LineBullet
import app.yuki.core.designsystem.component.SectionLabel
import app.yuki.core.designsystem.theme.Lines
import app.yuki.core.designsystem.theme.Unbounded
import app.yuki.core.designsystem.theme.Yuki
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StatsUiState(val stats: Stats?, val lines: List<LineSummary>)

class StatsViewModel(private val repo: StatsRepository, progress: ProgressRepository) : ViewModel() {
  private val stats = MutableStateFlow<Stats?>(null)
  val state: StateFlow<StatsUiState> = combine(stats, progress.observeLines()) { s, lines -> StatsUiState(s, lines) }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState(null, emptyList()))

  init {
    viewModelScope.launch { stats.value = repo.load() }
  }
}

private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

/** Stats (PRD S12): year heatmap, streaks, true retention, forecast and level mastery. */
@Composable
fun StatsScreen(viewModel: StatsViewModel, contentPadding: PaddingValues) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val c = Yuki.colors
  Column(
    Modifier
      .fillMaxSize()
      .background(c.neutral.ground)
      .padding(top = contentPadding.calculateTopPadding())
      .verticalScroll(rememberScrollState())
      .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = contentPadding.calculateBottomPadding() + 24.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text("Stats", style = MaterialTheme.typography.headlineLarge, color = c.neutral.ink)
    val s = state.stats ?: return@Column
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Tile("Streak", "${s.currentStreak}", if (s.currentStreak == 1) "day" else "days", Modifier.weight(1f))
      Tile("Longest", "${s.longestStreak}", if (s.longestStreak == 1) "day" else "days", Modifier.weight(1f))
      Tile("Time", if (s.totalMinutes >= 120) "${s.totalMinutes / 60}" else "${s.totalMinutes}", if (s.totalMinutes >= 120) "hours" else "min", Modifier.weight(1f))
    }
    Panel {
      SectionLabel("Daily reviews · last 12 months")
      Text("${s.totalReviews} reviews on ${s.studyDays} " + if (s.studyDays == 1) "day" else "days", style = MaterialTheme.typography.bodyMedium, color = c.neutral.ink2)
      Heatmap(s)
    }
    Panel {
      SectionLabel("True retention · last 30 days")
      val r = s.retention30
      Row(verticalAlignment = Alignment.Bottom) {
        Text(r?.let { "${(it * 100).toInt()}%" } ?: "Soon", fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = if (r == null) 24.sp else 40.sp, color = if (r == null) c.neutral.ink2 else c.neutral.ink)
        Spacer(Modifier.width(10.dp))
        Text(
          if (r == null) "Shows after 10 reviews of mature cards." else "of ${s.matureReviews30} reviews of cards already in Review were remembered. Aim for about 90%.",
          style = MaterialTheme.typography.bodyMedium, color = c.neutral.ink2, modifier = Modifier.padding(bottom = 6.dp),
        )
      }
    }
    Panel {
      SectionLabel("Reviews due · next 30 days")
      Forecast(s.forecast, s.today)
    }
    state.lines.forEach { line -> Panel { Mastery(line) } }
  }
}

@Composable
private fun Panel(content: @Composable () -> Unit) {
  Column(
    Modifier.fillMaxWidth().background(Yuki.colors.neutral.surface, RoundedCornerShape(20.dp)).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) { content() }
}

@Composable
private fun Tile(label: String, value: String, unit: String, modifier: Modifier) {
  val c = Yuki.colors
  Column(
    modifier
      .background(c.neutral.surface, RoundedCornerShape(20.dp))
      .padding(14.dp)
      .semantics(mergeDescendants = true) { contentDescription = "$label: $value $unit" },
  ) {
    Text(label, style = MaterialTheme.typography.labelMedium, color = c.neutral.ink2)
    Text(value, fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 26.sp, color = c.neutral.ink)
    Text(unit, style = MaterialTheme.typography.bodySmall, color = c.neutral.ink2)
  }
}

/** Sequential steps of the line colour; step 0 is the empty-day grey. */
@Composable
private fun heatSteps(): List<Color> {
  val c = Yuki.colors
  return listOf(c.neutral.surface2, c.line.field.copy(alpha = 0.25f), c.line.field.copy(alpha = 0.5f), c.line.field.copy(alpha = 0.75f), c.line.field)
}

private fun bucket(count: Int, max: Int): Int = when {
  count <= 0 -> 0
  max <= 4 -> count.coerceIn(1, 4)
  else -> (1 + (count - 1) * 4 / max).coerceIn(1, 4)
}

/** GitHub-style year: 53 week columns × 7 days, Monday on top. Tap a day to read its count. */
@Composable
private fun Heatmap(s: Stats) {
  val c = Yuki.colors
  val steps = heatSteps()
  val weeks = 53
  val lastMonday = s.today.with(DayOfWeek.MONDAY)
  val firstMonday = lastMonday.minusWeeks((weeks - 1).toLong())
  val max = s.daily.values.maxOrNull() ?: 0
  var picked by remember { mutableStateOf<LocalDate?>(null) }
  Canvas(
    Modifier
      .fillMaxWidth()
      .aspectRatio(weeks / 7f)
      .semantics { contentDescription = "Heatmap of daily reviews over the last year. ${s.studyDays} study days, busiest day ${max} reviews." }
      .pointerInput(s) {
        detectTapGestures { p ->
          val cell = size.width / weeks.toFloat()
          val col = (p.x / cell).toInt().coerceIn(0, weeks - 1)
          val row = (p.y / (size.height / 7f)).toInt().coerceIn(0, 6)
          val d = firstMonday.plusWeeks(col.toLong()).plusDays(row.toLong())
          picked = if (d.isAfter(s.today)) null else d
        }
      },
  ) {
    val cell = size.width / weeks
    val rowH = size.height / 7f
    val side = minOf(cell, rowH) - 1.dp.toPx()
    for (w in 0 until weeks) {
      for (d in 0 until 7) {
        val date = firstMonday.plusWeeks(w.toLong()).plusDays(d.toLong())
        if (date.isAfter(s.today)) continue
        val color = steps[bucket(s.daily[date] ?: 0, max)]
        drawRoundRect(color, Offset(w * cell, d * rowH), Size(side, side), CornerRadius(1.5.dp.toPx()))
        if (date == picked) drawRoundRect(c.neutral.ink, Offset(w * cell, d * rowH), Size(side, side), CornerRadius(1.5.dp.toPx()), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
      }
    }
  }
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text(
      picked?.let { "${it.format(dayFormat)}: ${s.daily[it] ?: 0} reviews" } ?: "Tap a day to see its reviews",
      style = MaterialTheme.typography.bodySmall, color = c.neutral.ink2, modifier = Modifier.weight(1f),
    )
    Text("Less", style = MaterialTheme.typography.labelSmall, color = c.neutral.ink2, modifier = Modifier.padding(end = 4.dp))
    steps.forEach { Box(Modifier.padding(horizontal = 1.dp).size(9.dp).background(it, RoundedCornerShape(2.dp))) }
    Text("More", style = MaterialTheme.typography.labelSmall, color = c.neutral.ink2, modifier = Modifier.padding(start = 4.dp))
  }
}

/** One thin bar per day, rounded at the data end, anchored to the baseline. Tap for the count. */
@Composable
private fun Forecast(days: List<Int>, today: LocalDate) {
  val c = Yuki.colors
  val max = (days.maxOrNull() ?: 0).coerceAtLeast(1)
  var picked by remember { mutableStateOf(0) }
  Canvas(
    Modifier
      .fillMaxWidth()
      .height(110.dp)
      .semantics { contentDescription = "Forecast: ${days.firstOrNull() ?: 0} reviews today, ${days.take(7).sum()} this week, ${days.sum()} in 30 days." }
      .pointerInput(days) {
        detectTapGestures { p -> picked = (p.x / (size.width / days.size.toFloat())).toInt().coerceIn(0, days.size - 1) }
      },
  ) {
    val slot = size.width / days.size
    val bar = slot - 2.dp.toPx()
    val base = size.height - 1.dp.toPx()
    drawLine(c.neutral.outline, Offset(0f, base), Offset(size.width, base), 1.dp.toPx())
    days.forEachIndexed { i, n ->
      if (n == 0) return@forEachIndexed
      val h = (base * n / max).coerceAtLeast(3.dp.toPx())
      val color = if (i == picked) c.line.deep else c.line.field
      drawRoundRect(color, Offset(i * slot, base - h), Size(bar, h + 2.dp.toPx()), CornerRadius(2.dp.toPx()))
    }
  }
  val label = when (picked) { 0 -> "Today"; 1 -> "Tomorrow"; else -> today.plusDays(picked.toLong()).format(dayFormat) }
  Text("$label: ${days.getOrElse(picked) { 0 }} reviews · this week ${days.take(7).sum()}", style = MaterialTheme.typography.bodySmall, color = c.neutral.ink2)
}

/** Per-level mastery bars, e.g. "HSK 2: 412/772 mastered" (stability ≥ 21 days). */
@Composable
private fun Mastery(line: LineSummary) {
  val c = Yuki.colors
  val color = Lines.of(line.lang).field
  Row(verticalAlignment = Alignment.CenterVertically) {
    LineBullet(line.lang.badge, color, size = 24.dp)
    Spacer(Modifier.width(8.dp))
    Text("${line.lang.englishName} · mastered", style = MaterialTheme.typography.titleSmall, color = c.neutral.ink)
  }
  line.levels.forEach { lvl ->
    Column(Modifier.semantics(mergeDescendants = true) { contentDescription = "${lvl.label}: ${lvl.mastered} of ${lvl.total} mastered, ${lvl.inReview} in review" }) {
      Row {
        Text(lvl.label, style = MaterialTheme.typography.bodyMedium, color = c.neutral.ink, modifier = Modifier.weight(1f))
        Text("${lvl.mastered}/${lvl.total}", style = MaterialTheme.typography.bodyMedium, color = c.neutral.ink2)
      }
      Box(Modifier.fillMaxWidth().padding(top = 4.dp).height(8.dp).background(c.neutral.surface2, CircleShape)) {
        val review = if (lvl.total == 0) 0f else lvl.inReview.toFloat() / lvl.total
        if (review > 0f) Box(Modifier.fillMaxWidth(review).height(8.dp).background(color.copy(alpha = 0.3f), CircleShape))
        if (lvl.masteredFraction > 0f) Box(Modifier.fillMaxWidth(lvl.masteredFraction).height(8.dp).background(color, CircleShape))
      }
    }
  }
  Text("Solid: mastered (remembered for 3+ weeks). Light: in review.", style = MaterialTheme.typography.bodySmall, color = c.neutral.ink2)
}
