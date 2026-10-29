package app.yuki

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.yuki.core.data.backup.BackupError
import app.yuki.core.data.backup.BackupException
import app.yuki.core.data.backup.BackupPreview
import app.yuki.core.designsystem.component.LineBullet
import app.yuki.core.designsystem.component.SectionLabel
import app.yuki.core.designsystem.theme.Lines
import app.yuki.core.designsystem.theme.Yuki
import app.yuki.core.model.DeckConfig
import app.yuki.core.model.Lang
import kotlinx.coroutines.launch

/** Study limits per line, and backup / restore of progress (PRD S15). */
@Composable
fun SettingsScreen(graph: AppGraph) {
  val c = Yuki.colors
  val scope = rememberCoroutineScope()
  val packs by graph.packs.packs.collectAsStateWithLifecycle()
  var message by remember { mutableStateOf<String?>(null) }
  var preview by remember { mutableStateOf<BackupPreview?>(null) }

  val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
    if (uri != null) scope.launch {
      message = runCatching { graph.backup.export(uri) }
        .fold({ "Backup saved: ${it.cards} cards, ${it.reviews} reviews." }, { "Backup failed: ${it.message}" })
    }
  }
  val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) scope.launch {
      try {
        preview = graph.backup.inspect(uri)
      } catch (e: BackupException) {
        message = when (val err = e.error) {
          BackupError.NotABackup -> "That file isn't a Yuki backup."
          BackupError.Corrupted -> "That backup is damaged and can't be restored."
          is BackupError.NewerVersion -> "This backup comes from a newer version of Yuki. Update the app to restore it."
        }
      } catch (e: Exception) {
        message = "Couldn't read that file: ${e.message}"
      }
    }
  }

  Column(
    Modifier.fillMaxSize().background(c.neutral.ground).verticalScroll(rememberScrollState()).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Spacer(Modifier.padding(top = 8.dp))
    Text("Settings", style = MaterialTheme.typography.headlineLarge, color = c.neutral.ink)

    packs.sortedBy { it.lang.ordinal }.forEach { pack -> DeckSettings(graph, pack.lang) }

    val context = androidx.compose.ui.platform.LocalContext.current
    Column(Modifier.fillMaxWidth().background(c.neutral.surface, RoundedCornerShape(20.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      SectionLabel("Home-screen widget")
      Text(
        "Streak widget: Yuki and your streak, with a nudge if you haven't studied yet. Word widget: one word you're learning each day.",
        style = MaterialTheme.typography.bodyMedium, color = c.neutral.ink2,
      )
      val manager = android.appwidget.AppWidgetManager.getInstance(context)
      if (manager.isRequestPinAppWidgetSupported) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Button(onClick = {
            manager.requestPinAppWidget(android.content.ComponentName(context, app.yuki.widget.StreakWidgetReceiver::class.java), null, null)
          }, shape = CircleShape) { Text("Add streak widget") }
          OutlinedButton(onClick = {
            manager.requestPinAppWidget(android.content.ComponentName(context, app.yuki.widget.WordWidgetReceiver::class.java), null, null)
          }, shape = CircleShape) { Text("Add word widget") }
        }
      } else {
        Text("Long-press your home screen, choose Widgets, then Yuki word.", style = MaterialTheme.typography.bodyMedium, color = c.neutral.ink)
      }
      Text(
        "On OPPO / ColorOS: if the widget stops changing, open Settings › Battery › Yuki and allow background activity.",
        style = MaterialTheme.typography.bodySmall, color = c.neutral.ink2,
      )
    }

    Column(Modifier.fillMaxWidth().background(c.neutral.surface, RoundedCornerShape(20.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      SectionLabel("Backup & restore")
      Text(
        "Your progress lives only on this phone. Save a backup file to Downloads or Google Drive, and restore it on any phone.",
        style = MaterialTheme.typography.bodyMedium, color = c.neutral.ink2,
      )
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { exportLauncher.launch(graph.backup.suggestedFileName()) }, shape = CircleShape) { Text("Save backup") }
        OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*")) }, shape = CircleShape) { Text("Restore…") }
      }
      message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.neutral.ink) }
    }

    Column(Modifier.fillMaxWidth().background(c.neutral.surface, RoundedCornerShape(20.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      SectionLabel("About")
      Text("Yuki ${BuildConfig.VERSION_NAME} · works fully offline", style = MaterialTheme.typography.bodyMedium, color = c.neutral.ink)
      Text(
        "Word lists: HSK 3.0. Dictionary: CC-CEDICT (CC BY-SA 4.0). Sentences: Tatoeba (CC BY 2.0 FR). Stroke data: Make Me a Hanzi (Arphic Public License). Fonts: Onest, Unbounded, LXGW WenKai GB (SIL OFL).",
        style = MaterialTheme.typography.bodySmall, color = c.neutral.ink2,
      )
    }
  }

  preview?.let { p ->
    AlertDialog(
      onDismissRequest = { preview = null },
      title = { Text("Restore this backup?") },
      text = {
        Text(
          "It holds ${p.manifest.cards} cards and ${p.manifest.reviews} reviews" +
            (p.manifest.lastReviewAt?.let { ", last studied ${it.take(10)}" } ?: "") +
            ". It will replace all progress on this phone.",
        )
      },
      confirmButton = {
        TextButton(onClick = {
          val chosen = p
          preview = null
          scope.launch {
            message = runCatching { graph.backup.restore(chosen) }.fold({ "Progress restored." }, { "Restore failed: ${it.message}" })
          }
        }) { Text("Replace progress") }
      },
      dismissButton = { TextButton(onClick = { preview = null }) { Text("Cancel") } },
    )
  }
}

@Composable
private fun DeckSettings(graph: AppGraph, lang: Lang) {
  val c = Yuki.colors
  val scope = rememberCoroutineScope()
  var config by remember(lang) { mutableStateOf<DeckConfig?>(null) }
  LaunchedEffect(lang) { config = graph.study.deckConfig(lang) }
  val cfg = config ?: return
  fun save(next: DeckConfig) {
    config = next
    scope.launch { graph.study.saveDeckConfig(next) }
  }
  Column(Modifier.fillMaxWidth().background(c.neutral.surface, RoundedCornerShape(20.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      LineBullet(lang.badge, Lines.of(lang).field, size = 24.dp)
      Spacer(Modifier.width(8.dp))
      Text(lang.englishName, style = MaterialTheme.typography.titleMedium, color = c.neutral.ink)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text("New cards per day", style = MaterialTheme.typography.bodyLarge, color = c.neutral.ink, modifier = Modifier.weight(1f))
      TextButton(onClick = { save(cfg.copy(newPerDay = (cfg.newPerDay - 5).coerceAtLeast(0))) }) { Text("−") }
      Text("${cfg.newPerDay}", style = MaterialTheme.typography.titleMedium, color = c.neutral.ink)
      TextButton(onClick = { save(cfg.copy(newPerDay = (cfg.newPerDay + 5).coerceAtMost(50))) }) { Text("+") }
    }
    if (lang == Lang.ZH || lang.needsTransliteration) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (lang == Lang.ZH) "Show pinyin on the front" else "Show Latin letters on the front", style = MaterialTheme.typography.bodyLarge, color = c.neutral.ink, modifier = Modifier.weight(1f))
        Switch(checked = cfg.showReadingOnFront, onCheckedChange = { save(cfg.copy(showReadingOnFront = it)) })
      }
    }
  }
}
