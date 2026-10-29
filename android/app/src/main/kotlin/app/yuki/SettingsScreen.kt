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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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

    ReminderSettings(graph)

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
      AutoBackupSettings(graph)
    }

    PackImport(graph)

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
      TextButton(onClick = { save(cfg.copy(newPerDay = (cfg.newPerDay - 5).coerceAtLeast(0))) }, modifier = Modifier.semantics { contentDescription = "Fewer new cards" }) { Text("−") }
      Text("${cfg.newPerDay}", style = MaterialTheme.typography.titleMedium, color = c.neutral.ink)
      TextButton(onClick = { save(cfg.copy(newPerDay = (cfg.newPerDay + 5).coerceAtMost(50))) }, modifier = Modifier.semantics { contentDescription = "More new cards" }) { Text("+") }
    }
    if (lang == Lang.ZH || lang.needsTransliteration) {
      ToggleRow(
        if (lang == Lang.ZH) "Show pinyin on the front" else "Show Latin letters on the front",
        "Easier, but you practise reading less",
        cfg.showReadingOnFront,
      ) { save(cfg.copy(showReadingOnFront = it)) }
    }
    ToggleRow("Listening cards", "Hear the word, recall its meaning", cfg.listening) { save(cfg.copy(listening = it)) }
    ToggleRow("Production cards", "See the meaning, recall the ${lang.englishName} word", cfg.production) { save(cfg.copy(production = it)) }
  }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
  val c = Yuki.colors
  // The whole row is one switch for TalkBack: title and subtitle read as its label.
  Row(
    Modifier
      .fillMaxWidth()
      .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
      .padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(Modifier.weight(1f)) {
      Text(title, style = MaterialTheme.typography.bodyLarge, color = c.neutral.ink)
      Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.neutral.ink2)
    }
    Switch(checked = checked, onCheckedChange = null)
  }
}

/** Daily reminder (PRD S13). Asks for the notification permission only when switched on. */
@Composable
private fun ReminderSettings(graph: AppGraph) {
  val c = Yuki.colors
  val context = androidx.compose.ui.platform.LocalContext.current
  val scope = rememberCoroutineScope()
  var time by remember { mutableStateOf<java.time.LocalTime?>(null) }
  var loaded by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) { time = graph.settings.reminderTime(); loaded = true }
  val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
  fun set(t: java.time.LocalTime?) {
    time = t
    scope.launch { graph.settings.setReminderTime(t) }
    app.yuki.reminder.Reminders.schedule(context, t)
    if (t != null && android.os.Build.VERSION.SDK_INT >= 33 && !app.yuki.reminder.Reminders.canNotify(context)) {
      permission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
  }
  if (!loaded) return
  Column(Modifier.fillMaxWidth().background(c.neutral.surface, RoundedCornerShape(20.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    SectionLabel("Daily reminder")
    ToggleRow("Remind me", "Only on days with cards waiting and no study yet", time != null) { on -> set(if (on) java.time.LocalTime.of(19, 0) else null) }
    time?.let { current ->
      androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(7 to 30, 12 to 0, 19 to 0, 21 to 0).map { (h, m) -> java.time.LocalTime.of(h, m) }.forEach { t ->
          androidx.compose.material3.FilterChip(selected = current == t, onClick = { set(t) }, label = { Text(t.toString()) })
        }
      }
    }
  }
}

/** Automatic weekly backup into a folder picked once (ARCHITECTURE §2.9). */
@Composable
private fun AutoBackupSettings(graph: AppGraph) {
  val c = Yuki.colors
  val context = androidx.compose.ui.platform.LocalContext.current
  val scope = rememberCoroutineScope()
  var folder by remember { mutableStateOf<String?>(null) }
  var last by remember { mutableStateOf<java.time.Instant?>(null) }
  var note by remember { mutableStateOf<String?>(null) }
  LaunchedEffect(Unit) { folder = graph.settings.autoBackupFolder(); last = graph.settings.lastAutoBackup() }
  val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
    if (uri != null) scope.launch {
      runCatching {
        context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        graph.settings.setAutoBackupFolder(uri.toString())
        app.yuki.backup.AutoBackup.runOnce(context, uri)
      }.fold(
        { cards ->
          folder = uri.toString(); last = graph.settings.lastAutoBackup()
          app.yuki.backup.AutoBackup.schedule(context, true)
          note = "First automatic backup saved ($cards cards). Next one in a week."
        },
        { note = "Couldn't use that folder: ${it.message}" },
      )
    }
  }
  ToggleRow("Back up every week", folder?.let { "To ${android.net.Uri.parse(it).lastPathSegment?.substringAfter(':') ?: "your folder"}, keeps the last 4" } ?: "Pick a folder once, e.g. Google Drive or Downloads", folder != null) { on ->
    if (on) pick.launch(null) else scope.launch {
      graph.settings.setAutoBackupFolder(null)
      app.yuki.backup.AutoBackup.schedule(context, false)
      folder = null
      note = null
    }
  }
  last?.let {
    val local = it.atZone(java.time.ZoneId.systemDefault())
    Text("Last automatic backup: ${local.toLocalDate()} ${local.toLocalTime().withNano(0).withSecond(0)}", style = MaterialTheme.typography.bodySmall, color = c.neutral.ink2)
  }
  note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.neutral.ink) }
}

/** Installs a content pack from a `.kpack` file (ARCHITECTURE §2.4). Progress is kept. */
@Composable
private fun PackImport(graph: AppGraph) {
  val c = Yuki.colors
  val context = androidx.compose.ui.platform.LocalContext.current
  val scope = rememberCoroutineScope()
  var busy by remember { mutableStateOf(false) }
  var note by remember { mutableStateOf<String?>(null) }
  val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) scope.launch {
      busy = true
      note = "Checking the pack…"
      note = try {
        val m = context.contentResolver.openInputStream(uri)!!.use { graph.packs.import(it) }
        "Installed ${m.title} (version ${m.version}). Your progress is kept."
      } catch (e: app.yuki.core.data.pack.PackImportException) {
        when (val err = e.error) {
          app.yuki.core.data.pack.ImportError.NotAPack -> "That file isn't a Yuki pack (.kpack)."
          app.yuki.core.data.pack.ImportError.UnsafeArchive -> "That file looks unsafe, so it wasn't installed."
          is app.yuki.core.data.pack.ImportError.Damaged -> "The pack is damaged (${err.file}). Copy it again and retry."
          is app.yuki.core.data.pack.ImportError.UnknownLanguage -> "This pack is for a language Yuki doesn't know yet (${err.code})."
        }
      } catch (e: Exception) {
        "Couldn't install the pack: ${e.message}"
      }
      busy = false
    }
  }
  Column(Modifier.fillMaxWidth().background(c.neutral.surface, RoundedCornerShape(20.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    SectionLabel("Content packs")
    graph.packs.packs.collectAsStateWithLifecycle().value.sortedBy { it.lang.ordinal }.forEach { p ->
      Text("${p.lang.englishName}: ${p.entity.id} v${p.entity.version}" + if (p.entity.assetDir != null) " (built in)" else "", style = MaterialTheme.typography.bodyMedium, color = c.neutral.ink)
    }
    Text("Copy a .kpack file to the phone, then install it here. It replaces that language's pack and keeps your progress.", style = MaterialTheme.typography.bodySmall, color = c.neutral.ink2)
    OutlinedButton(onClick = { pick.launch(arrayOf("*/*")) }, enabled = !busy, shape = CircleShape) { Text("Install a pack…") }
    note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = c.neutral.ink) }
  }
}