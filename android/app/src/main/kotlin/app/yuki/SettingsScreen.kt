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

  // File work runs in the app scope, so leaving Settings never cancels a backup or restore half-way.
  val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
    if (uri != null) graph.appScope.launch {
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

    OnlineBackup(graph, onPreview = { preview = it })

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
        "Fonts: Onest, Unbounded, LXGW WenKai GB, Klee One, Noto Naskh Arabic (SIL OFL). Each content pack lists its own word lists, dictionaries, sentences, recordings and pictures.",
        style = MaterialTheme.typography.bodySmall, color = c.neutral.ink2,
      )
      var showCredits by remember { mutableStateOf(false) }
      TextButton(onClick = { showCredits = true }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("Credits & licences") }
      if (showCredits) CreditsSheet(graph, onDismiss = { showCredits = false })
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
          graph.appScope.launch {
            message = runCatching { graph.backup.restore(chosen) }.fold(
              { "Progress restored. Install your language packs again if this is a new phone." },
              { "Restore failed: ${it.message}" },
            )
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
    if (lang.hasCharacterReading || lang.needsTransliteration) {
      ToggleRow(
        when (lang) {
          Lang.ZH -> "Show pinyin on the front"
          Lang.JA -> "Show kana and romaji on the front"
          else -> "Show Latin letters on the front"
        },
        "Easier, but you practise reading less",
        cfg.showReadingOnFront,
      ) { save(cfg.copy(showReadingOnFront = it)) }
    }
    var shown by remember(lang) { mutableStateOf(true) }
    LaunchedEffect(lang) { shown = graph.settings.activeLangs()?.contains(lang) ?: true }
    ToggleRow("Show on Today", "Study this language and see it on the Today screen", shown) { on ->
      shown = on
      scope.launch {
        val all = graph.packs.packs.value.map { it.lang }.toSet()
        val active = graph.settings.activeLangs() ?: all
        graph.settings.setActiveLangs(if (on) active + lang else (active - lang).ifEmpty { setOf(lang) })
      }
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

/**
 * Online backup (ADR-7 amended): sign in with email and password once; progress then uploads
 * whenever you leave the app and are online. Restore shows the same confirm dialog as a file.
 */
@Composable
private fun OnlineBackup(graph: AppGraph, onPreview: (BackupPreview) -> Unit) {
  val c = Yuki.colors
  val cloud = graph.cloud
  val state by cloud.state.collectAsStateWithLifecycle()
  var email by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var note by remember { mutableStateOf<String?>(null) }
  fun run(block: suspend () -> String) {
    busy = true
    note = null
    graph.appScope.launch {
      note = runCatching { block() }.getOrElse { it.message ?: "Something went wrong." }
      busy = false
    }
  }
  Column(Modifier.fillMaxWidth().background(c.neutral.surface, RoundedCornerShape(20.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    SectionLabel("Online backup")
    val signedInEmail = state.email
    if (signedInEmail == null) {
      Text(
        "Sign in to keep a copy of your progress online. Studying stays offline; Yuki uploads when you leave the app and you're online.",
        style = MaterialTheme.typography.bodyMedium, color = c.neutral.ink2,
      )
      androidx.compose.material3.OutlinedTextField(
        value = email, onValueChange = { email = it }, label = { Text("Email") }, singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email),
        modifier = Modifier.fillMaxWidth(),
      )
      androidx.compose.material3.OutlinedTextField(
        value = password, onValueChange = { password = it }, label = { Text("Password (6+ characters)") }, singleLine = true,
        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
      )
      val ready = !busy && email.contains('@') && password.length >= 6
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(enabled = ready, onClick = {
          run { cloud.signIn(email, password); password = ""; cloud.scheduleUpload(force = true); "Signed in. Your progress will upload when you're online." }
        }, shape = CircleShape) { Text("Sign in") }
        OutlinedButton(enabled = ready, onClick = {
          run {
            when (cloud.signUp(email, password)) {
              app.yuki.cloud.CloudBackup.SignUpResult.SignedIn -> { password = ""; cloud.scheduleUpload(force = true); "Account created and signed in." }
              app.yuki.cloud.CloudBackup.SignUpResult.ConfirmEmail ->
                "Account created. Open the email from Supabase and tap the link (the page it opens may not load; that's fine), then tap Sign in here."
            }
          }
        }, shape = CircleShape) { Text("Create account") }
      }
    } else {
      Text("Signed in as $signedInEmail", style = MaterialTheme.typography.bodyLarge, color = c.neutral.ink)
      Text(
        state.lastUpload?.let {
          val t = it.atZone(java.time.ZoneId.systemDefault())
          "Last uploaded: ${t.toLocalDate()} ${t.toLocalTime().withNano(0).withSecond(0)}"
        } ?: "Not uploaded yet",
        style = MaterialTheme.typography.bodySmall, color = c.neutral.ink2,
      )
      ToggleRow("Upload only on Wi-Fi", "Otherwise mobile data is used too (a few MB each time)", state.wifiOnly) { cloud.setWifiOnly(it) }
      androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(enabled = !busy, onClick = {
          run { val m = cloud.upload(force = true); "Uploaded: ${m?.cards ?: 0} cards, ${m?.reviews ?: 0} reviews." }
        }, shape = CircleShape) { Text("Back up now") }
        OutlinedButton(enabled = !busy, onClick = {
          run { onPreview(cloud.download()); "Online backup found. Confirm to restore it." }
        }, shape = CircleShape) { Text("Restore from online") }
        TextButton(enabled = !busy, onClick = { cloud.signOut(); note = "Signed out. The online copy stays." }) { Text("Sign out") }
      }
    }
    if (busy) androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
    note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.neutral.ink) }
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
    if (uri != null) graph.appScope.launch {
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
    // App scope: a big pack takes a while to check, and leaving Settings must not cut it off.
    if (uri != null) graph.appScope.launch {
      busy = true
      note = "Checking the pack…"
      note = try {
        val m = context.contentResolver.openInputStream(uri)!!.use { graph.packs.import(it) }
        // A language added after onboarding should show on Today straight away.
        graph.settings.activeLangs()?.let { active -> graph.settings.setActiveLangs(active + app.yuki.core.model.Lang.fromCode(m.lang)) }
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
private data class PackCredits(val title: String, val sources: List<app.yuki.core.data.pack.PackSource>, val assets: List<Triple<String, String, Int>>)

/** Attribution for every installed pack: upstream datasets, then where pictures and recordings come from. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CreditsSheet(graph: AppGraph, onDismiss: () -> Unit) {
  val c = Yuki.colors
  var credits by remember { mutableStateOf<List<PackCredits>?>(null) }
  LaunchedEffect(Unit) {
    graph.packs.awaitReady()
    credits = graph.packs.packs.value.sortedBy { it.lang.ordinal }.map { p ->
      val m = graph.packs.manifest(p)
      PackCredits(m?.title ?: p.entity.id, m?.sources.orEmpty(), runCatching { p.reader.assetCredits() }.getOrDefault(emptyList()))
    }
  }
  androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = c.neutral.surface) {
    Column(
      Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text("Credits & licences", style = MaterialTheme.typography.titleLarge, color = c.neutral.ink)
      Text(
        "Yuki is a personal, non-commercial app. Content is used under the licences below; share-alike content keeps its licence.",
        style = MaterialTheme.typography.bodySmall, color = c.neutral.ink2,
      )
      credits?.forEach { pack ->
        SectionLabel(pack.title)
        pack.sources.forEach { s ->
          Text("${s.name} · ${s.license}", style = MaterialTheme.typography.bodyMedium, color = c.neutral.ink)
          s.url?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.neutral.ink2) }
        }
        if (pack.assets.isNotEmpty()) {
          Text("Pictures and recordings", style = MaterialTheme.typography.labelLarge, color = c.neutral.ink2)
          pack.assets.forEach { (source, license, n) ->
            Text("$source · $license · $n", style = MaterialTheme.typography.bodySmall, color = c.neutral.ink)
          }
        }
        if (pack.sources.isEmpty() && pack.assets.isEmpty()) {
          Text("Built-in sample pack.", style = MaterialTheme.typography.bodySmall, color = c.neutral.ink2)
        }
      }
    }
  }
}