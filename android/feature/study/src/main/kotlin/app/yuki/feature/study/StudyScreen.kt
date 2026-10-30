package app.yuki.feature.study

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yuki.core.designsystem.component.ArtWindow
import app.yuki.core.designsystem.component.AudioButton
import app.yuki.core.designsystem.component.CardDivider
import app.yuki.core.designsystem.component.CardHeader
import app.yuki.core.designsystem.component.CollectibleCard
import app.yuki.core.designsystem.component.GrammarChip
import app.yuki.core.designsystem.component.Headword
import app.yuki.core.designsystem.component.LineBullet
import app.yuki.core.designsystem.component.RatingBar
import app.yuki.core.designsystem.component.StrokeCanvas
import app.yuki.core.designsystem.component.StrokeGeometry
import app.yuki.core.designsystem.component.HowToWrite
import app.yuki.core.designsystem.component.rememberReduceMotion
import app.yuki.core.designsystem.component.toneText
import app.yuki.core.designsystem.component.HowToReadPanel
import app.yuki.core.designsystem.component.ScriptText
import app.yuki.core.designsystem.component.KanaReading
import app.yuki.core.designsystem.component.TransliterationLine
import app.yuki.core.designsystem.theme.scriptFamily
import app.yuki.core.designsystem.theme.Unbounded
import app.yuki.core.designsystem.theme.Yuki
import app.yuki.core.designsystem.theme.YukiType
import app.yuki.core.model.Hanzi
import app.yuki.core.model.Mood
import app.yuki.core.designsystem.component.YukiMascot
import app.yuki.core.model.CardType
import app.yuki.core.model.Lang
import app.yuki.core.model.Rating
import app.yuki.core.model.WordDetail

@Composable
fun StudyScreen(
  viewModel: StudyViewModel,
  onClose: () -> Unit,
  onOpenWord: (String) -> Unit,
  /** "Study all" (PRD S11): the next language in the queue, shown on the Done button. */
  nextLang: Lang? = null,
  /** Session over (Done / nothing due); defaults to closing. */
  onFinished: () -> Unit = onClose,
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val view = LocalView.current
  LaunchedEffect(viewModel) {
    viewModel.effect.collect { e ->
      if (e is StudyEffect.Rated) {
        val confirm = e.rating != Rating.AGAIN
        val constant = when {
          Build.VERSION.SDK_INT >= 30 && confirm -> HapticFeedbackConstants.CONFIRM
          Build.VERSION.SDK_INT >= 30 -> HapticFeedbackConstants.REJECT
          else -> HapticFeedbackConstants.CLOCK_TICK
        }
        view.performHapticFeedback(constant)
      }
    }
  }
  // The study screens are drenched in the line colour: use light status/navigation bar icons.
  DisposableEffect(view) {
    val activity = generateSequence(view.context) { (it as? android.content.ContextWrapper)?.baseContext }
      .filterIsInstance<android.app.Activity>().firstOrNull()
    val controller = activity?.let { androidx.core.view.WindowCompat.getInsetsController(it.window, view) }
    val previous = controller?.isAppearanceLightStatusBars
    controller?.isAppearanceLightStatusBars = false
    onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
  }
  val c = Yuki.colors
  Box(Modifier.fillMaxSize().background(if (state is StudyUiState.Finished) c.line.deep else c.field)) {
    when (val s = state) {
      StudyUiState.Loading -> Unit
      is StudyUiState.Empty -> EmptySession(s.lang, nextLang, onFinished)
      is StudyUiState.Studying -> StudyingContent(s.ui, viewModel, onClose)
      is StudyUiState.Finished -> SummaryContent(s.summary, nextLang, onFinished, onReviewMissed = viewModel::startExtraRound)
    }
  }
}

@Composable
private fun StudyingContent(ui: CardUi, vm: StudyViewModel, onClose: () -> Unit) {
  val tilt = rememberTilt()
  val reduceMotion = rememberReduceMotion()
  Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
    StudyTopBar(ui, onClose, onUndo = vm::undo)
    Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
      AnimatedContent(
        targetState = ui.card.id to ui.position,
        transitionSpec = {
          if (reduceMotion) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
          else (slideInVertically(tween(300)) { it / 6 } + fadeIn(tween(300))) togetherWith fadeOut(tween(150))
        },
        label = "next-card",
      ) { _ ->
        CollectibleCard(
          flipped = ui.flipped,
          onFlip = vm::flip,
          foil = ui.foil,
          tilt = { tilt.value },
          accessibilityLabel = when {
            ui.flipped -> "${ui.detail.word.headword}, ${ui.detail.gloss}. Double-tap to flip back"
            ui.direction == CardType.LISTENING -> "Listening card: say what you hear means. Double-tap to flip"
            ui.direction == CardType.PRODUCTION -> "Say it in ${ui.card.lang.englishName}: ${ui.detail.gloss}. Double-tap to flip"
            else -> "${ui.detail.word.headword}, ${ui.detail.word.levelLabel}. Double-tap to flip"
          },
          front = {
            when (ui.direction) {
              CardType.LISTENING -> ListeningFront(ui, onPlay = vm::playWord)
              CardType.PRODUCTION -> ProductionFront(ui)
              else -> CardFront(ui, onPlay = vm::playWord)
            }
          },
          back = { CardBack(ui, onPlayWord = vm::playWord, onPlaySentence = vm::playSentence) },
        )
      }
    }
    Surface(color = Yuki.colors.neutral.surface, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
      Column(Modifier.fillMaxWidth().padding(16.dp).windowInsetsPadding(WindowInsets.navigationBars)) {
        RatingBar(ui.intervals, enabled = ui.flipped, onRate = vm::rate)
        Spacer(Modifier.height(12.dp))
        SessionTrack(ui.position, ui.total)
      }
    }
  }
}

@Composable
private fun StudyTopBar(ui: CardUi, onClose: () -> Unit, onUndo: () -> Unit) {
  val lang = ui.card.lang
  Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
    IconButton(onClick = onClose, modifier = Modifier.semantics { contentDescription = "Close session" }) {
      Box(Modifier.size(32.dp).background(Color.White.copy(alpha = 0.16f), CircleShape), contentAlignment = Alignment.Center) {
        Text("✕", color = Color.White, fontSize = 14.sp)
      }
    }
    Spacer(Modifier.width(4.dp))
    LineBullet(ui.detail.word.level.toString(), Color.White, size = 24.dp, onColor = Yuki.colors.line.field)
    Spacer(Modifier.width(8.dp))
    val title = if (ui.extraRound) "Review missed · ${lang.englishName}" else "${ui.detail.word.levelLabel} · ${lang.englishName}"
    Text(title.uppercase(), style = YukiType.station, color = Color.White, modifier = Modifier.weight(1f))
    if (ui.canUndo) TextButton(onClick = onUndo) { Text("Undo", color = Color.White) }
    Text("${ui.position}/${ui.total}", color = Color.White, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 8.dp))
  }
}

@Composable
private fun SessionTrack(position: Int, total: Int) {
  val c = Yuki.colors
  val fraction = if (total == 0) 0f else (position - 1).toFloat() / total
  Row(verticalAlignment = Alignment.CenterVertically) {
    Text("$position", style = YukiType.station, color = c.neutral.ink2)
    Box(Modifier.weight(1f).padding(horizontal = 10.dp).height(4.dp).background(c.neutral.outline, CircleShape)) {
      Box(Modifier.fillMaxWidth(fraction.coerceIn(0.02f, 1f)).height(4.dp).background(c.line.field, CircleShape))
    }
    Text("$total", style = YukiType.station, color = c.neutral.ink2)
  }
}

@Composable
private fun ColumnScope.CardFront(ui: CardUi, onPlay: () -> Unit) {
  val word = ui.detail.word
  val characters = word.lang.hasCharacterReading
  CardHeader(word.level.toString(), "${word.levelLabel} · ${word.pos}", ui.foil)
  ArtWindow(ui.art, Modifier.padding(top = 10.dp).weight(0.9f))
  Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
    Headword(word.headword, word.lang)
    when {
      word.lang.needsTransliteration -> if (ui.showReadingOnFront) TransliterationLine(word.headword, word.lang)
      !characters || ui.showReadingOnFront -> ReadingLine(ui.detail)
    }
    word.gender?.let { if (!characters) Text(it, style = MaterialTheme.typography.bodyMedium, color = Yuki.colors.neutral.ink2) }
  }
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    AudioButton(onPlay, enabled = ui.detail.wordAudio != null, contentDescription = "Play pronunciation")
    Spacer(Modifier.weight(1f))
    Text("NO. %04d".format(word.freqRank), style = YukiType.station, color = Yuki.colors.neutral.ink2)
  }
}

/** Listening card (PRD S6): only the sound; the word and its meaning wait on the back. */
@Composable
private fun ColumnScope.ListeningFront(ui: CardUi, onPlay: () -> Unit) {
  val c = Yuki.colors
  CardHeader(ui.detail.word.level.toString(), "${ui.detail.word.levelLabel} · listening", ui.foil)
  Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
    Box(
      Modifier
        .size(112.dp)
        .background(c.line.field, CircleShape)
        .clickable(onClickLabel = "Play the word again", onClick = onPlay),
      contentAlignment = Alignment.Center,
    ) { Text("🔊", fontSize = 44.sp) }
    Spacer(Modifier.height(20.dp))
    Text("What does it mean?", style = MaterialTheme.typography.titleMedium, color = c.neutral.ink)
    Text("Tap to hear it again", style = MaterialTheme.typography.bodySmall, color = c.neutral.ink2)
  }
}

/** Production card (PRD S6): the meaning and picture; you recall the word, then flip to check. */
@Composable
private fun ColumnScope.ProductionFront(ui: CardUi) {
  val c = Yuki.colors
  val word = ui.detail.word
  CardHeader(word.level.toString(), "${word.levelLabel} · say it", ui.foil)
  ArtWindow(ui.art, Modifier.padding(top = 10.dp).weight(0.9f))
  Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
    Text(
      ui.detail.senses.firstOrNull()?.gloss ?: ui.detail.gloss,
      style = MaterialTheme.typography.headlineSmall.copy(fontFamily = app.yuki.core.designsystem.theme.Onest, fontWeight = FontWeight.Bold),
      color = c.neutral.ink,
      textAlign = TextAlign.Center,
    )
    if (word.pos.isNotBlank()) Text(word.pos, style = MaterialTheme.typography.bodyMedium, color = c.neutral.ink2)
    Spacer(Modifier.height(10.dp))
    Text(
      if (word.lang.hasCharacterReading) "Say it, then write it on paper" else "Say it in ${word.lang.englishName}",
      style = MaterialTheme.typography.bodySmall, color = c.neutral.ink2,
    )
  }
}

@Composable
private fun ReadingLine(detail: WordDetail) {
  val word = detail.word
  if (word.lang == Lang.ZH && word.syllables.isNotEmpty()) {
    Text(toneText(word.syllables, word.tones), style = YukiType.reading, textAlign = TextAlign.Center)
  } else if (word.lang == Lang.JA) {
    KanaReading(word.headword, word.reading)
  } else {
    Text(word.reading, style = YukiType.reading, color = Yuki.colors.neutral.ink2, textAlign = TextAlign.Center)
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnScope.CardBack(ui: CardUi, onPlayWord: () -> Unit, onPlaySentence: (Int) -> Unit) {
  val detail = ui.detail
  val word = detail.word
  val characters = word.lang.hasCharacterReading
  val c = Yuki.colors
  var strokeSheet by remember { mutableStateOf<Hanzi?>(null) }
  var readSheet by remember { mutableStateOf(false) }
  val guide = remember(word.lang) { app.yuki.core.model.script.ScriptGuide.of(word.lang) }
  // Japanese is read from its kana reading; kanji have no letter-by-letter sound.
  val readable = if (word.lang == Lang.JA) word.reading.ifBlank { word.headword } else word.headword
  val canExplainReading = guide != null && (word.lang.needsTransliteration || word.lang == Lang.JA || guide.hints(readable).isNotEmpty())
  CardHeader(word.level.toString(), word.levelLabel, ui.foil)
  Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
      Text(
        word.headword,
        style = when {
          characters -> YukiType.sentenceFor(word.lang).copy(fontSize = 34.sp, lineHeight = 42.sp)
          word.lang.rtl -> MaterialTheme.typography.headlineSmall.copy(fontFamily = scriptFamily(word.lang), fontSize = 30.sp, lineHeight = 52.sp)
          else -> MaterialTheme.typography.headlineSmall.copy(fontFamily = app.yuki.core.designsystem.theme.Onest, fontWeight = FontWeight.Bold)
        },
        color = c.neutral.ink,
      )
      Spacer(Modifier.width(10.dp))
      Box(Modifier.weight(1f)) {
        if (word.lang.needsTransliteration) TransliterationLine(word.headword, word.lang) else ReadingLine(detail)
      }
      AudioButton(onPlayWord, enabled = detail.wordAudio != null, contentDescription = "Play pronunciation", small = true)
    }
    if (word.lang.needsTransliteration && word.reading.isNotBlank()) {
      Text(word.reading, style = MaterialTheme.typography.bodySmall, color = c.neutral.ink2)
    }
    if (canExplainReading) {
      TextButton(onClick = { readSheet = true }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        Text("How to read this word", style = MaterialTheme.typography.labelLarge, color = c.line.field)
      }
    }
    CardDivider()
    detail.senses.groupBy { it.pos }.forEach { (pos, senses) ->
      Text(
        buildString {
          if (pos.isNotBlank()) append("$pos  ")
          append(senses.joinToString("; ") { it.gloss })
        },
        style = MaterialTheme.typography.bodyLarge,
        color = c.neutral.ink,
      )
    }
    val chips = word.chips
    if (chips.isNotEmpty()) {
      FlowRow(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { chips.forEach { GrammarChip(it) } }
    }
    detail.sentences.firstOrNull()?.let { s ->
      CardDivider()
      Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
          ScriptText(s.text, word.lang)
          val reading = s.reading ?: if (word.lang.needsTransliteration) guide?.transliterate(s.text) else null
          reading?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.neutral.ink2) }
          Text(s.translation, style = MaterialTheme.typography.bodyMedium, color = c.neutral.ink2)
        }
        if (s.audioPath != null) AudioButton({ onPlaySentence(0) }, enabled = true, contentDescription = "Play example sentence", small = true)
      }
    }
    if (characters && detail.characters.isNotEmpty()) {
      CardDivider()
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        detail.characters.forEach { h ->
          val g = remember(h.ch) { StrokeGeometry(h) }
          Box(
            Modifier
              .size(58.dp)
              .clickable(onClickLabel = "Show stroke order for ${h.ch}") { strokeSheet = h },
          ) {
            StrokeCanvas(g, g.count.toFloat(), Modifier.fillMaxWidth())
          }
        }
        Spacer(Modifier.weight(1f))
        Text("Tap for\nstroke order", style = MaterialTheme.typography.bodySmall, color = c.neutral.ink2, textAlign = TextAlign.End)
      }
    }
  }
  if (readSheet && guide != null) {
    ModalBottomSheet(onDismissRequest = { readSheet = false }, containerColor = c.neutral.surface) {
      Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(guide.title, style = MaterialTheme.typography.titleLarge, color = c.neutral.ink)
        HowToReadPanel(readable, word.lang)
      }
    }
  }
  strokeSheet?.let { h ->
    var drawing by remember { mutableStateOf(false) }
    ModalBottomSheet(
      onDismissRequest = { strokeSheet = null },
      containerColor = c.neutral.surface,
      sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
      sheetGesturesEnabled = !drawing,
    ) {
      Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState(), enabled = !drawing)) {
        HowToWrite(detail.characters, onPracticingChange = { drawing = it })
      }
    }
  }
}

@Composable
private fun EmptySession(lang: Lang, nextLang: Lang?, onClose: () -> Unit) {
  Column(
    Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
    verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    YukiMascot(Mood.SLEEPY, size = 180.dp)
    Spacer(Modifier.height(12.dp))
    Text("All caught up", style = MaterialTheme.typography.headlineMedium, color = Color.White, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Text("Nothing is due in ${lang.englishName} right now, and today's new cards are done.", color = Color.White.copy(alpha = 0.9f), textAlign = TextAlign.Center)
    Spacer(Modifier.height(24.dp))
    Button(onClick = onClose, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Yuki.colors.line.field)) { Text(nextLang?.let { "Next: ${it.englishName}" } ?: "Back to Today") }
  }
}

@Composable
private fun SummaryContent(summary: SessionSummary, nextLang: Lang?, onClose: () -> Unit, onReviewMissed: () -> Unit) {
  Column(
    Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Spacer(Modifier.weight(1f))
    YukiMascot(Mood.CELEBRATE, size = 168.dp, modifier = Modifier.align(Alignment.CenterHorizontally))
    Text(if (summary.accuracyPercent >= 85) "Amazing work!" else "Every review counts. Well done!", style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.align(Alignment.CenterHorizontally))
    Text("ARRIVED", style = YukiType.station.copy(letterSpacing = 1.5.sp), color = Color.White.copy(alpha = 0.75f))
    Text(summary.lang.englishName, fontFamily = Unbounded, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, color = Color.White)
    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.2f)))
    BoardRow("Cards", "${summary.cards}", "Time", "${summary.minutes}m")
    BoardRow("New", "${summary.newLearned}", "Accuracy", "${summary.accuracyPercent}%")
    BoardRow("New foils", "+${summary.newFoils}", "", "")
    if (summary.missed.isNotEmpty()) {
      Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.2f)))
      Text("Missed: " + summary.missed.joinToString(" · "), color = Color.White, style = if (summary.lang.hasCharacterReading) YukiType.sentenceFor(summary.lang) else MaterialTheme.typography.bodyLarge)
    }
    if (summary.reviewedMissed) {
      Text("Missed words reviewed. They come back on their normal schedule.", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
    }
    Spacer(Modifier.weight(1f))
    if (summary.missed.isNotEmpty()) {
      androidx.compose.material3.OutlinedButton(
        onClick = onReviewMissed,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = CircleShape,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color.White),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
      ) { Text(if (summary.missed.size == 1) "Review it again now" else "Review these ${summary.missed.size} now", style = MaterialTheme.typography.titleMedium) }
    }
    Button(
      onClick = onClose,
      modifier = Modifier.fillMaxWidth().height(52.dp),
      shape = CircleShape,
      colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Yuki.colors.line.deep),
    ) { Text(nextLang?.let { "Next: ${it.englishName}" } ?: "Done", style = MaterialTheme.typography.titleMedium) }
  }
}

@Composable
private fun BoardRow(k1: String, v1: String, k2: String, v2: String) {
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text(k1, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.weight(1f))
    Text(v1, fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White, modifier = Modifier.weight(1f))
    Text(k2, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.weight(1f))
    Text(v2, fontFamily = Unbounded, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color.White, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
  }
}

/**
 * Phone tilt for the card shine, from the gravity sensor, relative to how the phone was held
 * when the card appeared. Values in [-1, 1]. Read only inside graphics layers.
 */
@Composable
private fun rememberTilt(): androidx.compose.runtime.State<Offset> {
  val context = LocalContext.current
  val reduceMotion = rememberReduceMotion()
  val state = remember { mutableStateOf(Offset.Zero) }
  DisposableEffect(reduceMotion) {
    if (reduceMotion) return@DisposableEffect onDispose { }
    val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val sensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    var base: Offset? = null
    var smooth = Offset.Zero
    val listener = object : SensorEventListener {
      override fun onSensorChanged(e: SensorEvent) {
        val raw = Offset(-e.values[0] / 9.81f, e.values[1] / 9.81f)
        val b = base ?: raw.also { base = it }
        val target = Offset(((raw.x - b.x) * 2.5f).coerceIn(-1f, 1f), ((raw.y - b.y) * 2.5f).coerceIn(-1f, 1f))
        smooth = smooth + (target - smooth) * 0.15f
        state.value = smooth
      }

      override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }
    if (sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
    onDispose { sm.unregisterListener(listener) }
  }
  return state
}
