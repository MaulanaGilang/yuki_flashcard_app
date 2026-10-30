package app.yuki

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import app.yuki.core.designsystem.theme.Yuki
import app.yuki.core.designsystem.theme.YukiTheme
import app.yuki.core.model.Lang
import app.yuki.feature.library.AlphabetScreen
import app.yuki.feature.library.LibraryScreen
import app.yuki.feature.library.LibraryViewModel
import app.yuki.feature.library.WordDetailScreen
import app.yuki.feature.library.WordDetailViewModel
import app.yuki.feature.study.StudyScreen
import app.yuki.feature.study.StudyViewModel
import app.yuki.feature.today.TodayScreen
import app.yuki.feature.today.TodayViewModel
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.yuki.feature.study.CheckpointScreen
import app.yuki.feature.study.CheckpointViewModel
import app.yuki.feature.today.OnboardingScreen
import app.yuki.feature.today.OnboardingViewModel
import app.yuki.feature.today.StatsScreen
import app.yuki.feature.today.StatsViewModel
import app.yuki.reminder.Reminders
import kotlinx.serialization.Serializable

@Serializable data object TodayKey : NavKey
@Serializable data object LibraryKey : NavKey
@Serializable data object SettingsKey : NavKey
@Serializable data object StatsKey : NavKey
@Serializable data class CheckpointKey(val lang: String, val level: Int, val label: String) : NavKey
/** [next]: languages still queued by "Study all" (PRD S11), run after this one. */
@Serializable data class StudyKey(val lang: String, val next: List<String> = emptyList()) : NavKey
@Serializable data class WordKey(val wordId: String) : NavKey
@Serializable data class AlphabetKey(val lang: String) : NavKey

private val topLevel = listOf<Pair<NavKey, String>>(TodayKey to "Today", LibraryKey to "Library", StatsKey to "Stats", SettingsKey to "Settings")
private val topLevelIcons = mapOf<NavKey, ImageVector>(
  TodayKey to Icons.Filled.Home, LibraryKey to Icons.AutoMirrored.Filled.List, StatsKey to Icons.Filled.DateRange, SettingsKey to Icons.Filled.Settings,
)

@Composable
fun YukiNavigation(graph: AppGraph, openWord: String? = null, onWordOpened: () -> Unit = {}) {
  val context = LocalContext.current
  // First run goes through onboarding (PRD S10); anyone with study history is already set up.
  var onboarded by remember { mutableStateOf<Boolean?>(null) }
  LaunchedEffect(Unit) {
    val done = graph.settings.onboarded() || graph.progress.hasHistory()
    if (done) graph.settings.setOnboarded()
    onboarded = done
  }
  val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
  when (onboarded) {
    null -> return
    false -> {
      val vm = viewModel { OnboardingViewModel(graph.packs, graph.study, graph.settings) }
      OnboardingScreen(vm, onFinished = { time ->
        Reminders.schedule(context, time)
        if (time != null && Build.VERSION.SDK_INT >= 33 && !Reminders.canNotify(context)) {
          notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        onboarded = true
      })
      return
    }
    true -> Unit
  }
  val backStack = rememberNavBackStack(TodayKey)
  // Tapping the home-screen widget opens that word on top of Today.
  LaunchedEffect(openWord) {
    if (openWord != null) {
      backStack.add(WordKey(openWord))
      onWordOpened()
    }
  }
  val current = backStack.lastOrNull()
  val showBar = current == TodayKey || current == LibraryKey || current == StatsKey || current == SettingsKey

  Scaffold(
    bottomBar = {
      if (showBar) {
        NavigationBar(containerColor = Yuki.colors.neutral.surface) {
          topLevel.forEach { (key, label) ->
            NavigationBarItem(
              selected = current == key,
              onClick = {
                if (current != key) {
                  backStack.clear()
                  backStack.add(TodayKey)
                  if (key != TodayKey) backStack.add(key)
                }
              },
              icon = { Icon(topLevelIcons.getValue(key), contentDescription = null) },
              label = { Text(label) },
              colors = NavigationBarItemDefaults.colors(indicatorColor = Yuki.colors.artTint),
            )
          }
        }
      }
    },
  ) { padding ->
    NavDisplay(
      backStack = backStack,
      onBack = { backStack.removeLastOrNull() },
      entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), rememberViewModelStoreNavEntryDecorator()),
      entryProvider = entryProvider {
        entry<TodayKey> {
          val vm = viewModel { TodayViewModel(graph.progress) }
          TodayScreen(
            vm, padding,
            onStart = { lang -> backStack.add(StudyKey(lang.code)) },
            onStudyAll = { langs -> if (langs.isNotEmpty()) backStack.add(StudyKey(langs.first().code, langs.drop(1).map { it.code })) },
            onCheckpoint = { lang, level, label -> backStack.add(CheckpointKey(lang.code, level, label)) },
          )
        }
        entry<StatsKey> {
          val vm = viewModel { StatsViewModel(graph.stats, graph.progress) }
          StatsScreen(vm, padding)
        }
        entry<CheckpointKey> { key ->
          val lang = Lang.fromCode(key.lang)
          YukiTheme(lang) {
            val vm = viewModel { CheckpointViewModel(lang, key.level, graph.study) }
            CheckpointScreen(vm, key.label, onClose = { backStack.removeLastOrNull() })
          }
        }
        entry<LibraryKey> {
          val vm = viewModel { LibraryViewModel(graph.progress, graph.packs) }
          LibraryScreen(vm, padding, onOpenWord = { backStack.add(WordKey(it)) }, onOpenGuide = { backStack.add(AlphabetKey(it.code)) })
        }
        entry<SettingsKey> {
          Box(Modifier.fillMaxSize().padding(padding)) { SettingsScreen(graph) }
        }
        entry<StudyKey> { key ->
          val lang = Lang.fromCode(key.lang)
          YukiTheme(lang) {
            val vm = viewModel { StudyViewModel(lang, graph.study, graph.packs, graph.audio, graph.clock) }
            StudyScreen(
              vm,
              onClose = { backStack.removeLastOrNull() },
              onOpenWord = { backStack.add(WordKey(it)) },
              nextLang = key.next.firstOrNull()?.let(Lang::fromCode),
              onFinished = {
                backStack.removeLastOrNull()
                key.next.firstOrNull()?.let { backStack.add(StudyKey(it, key.next.drop(1))) }
              },
            )
          }
        }
        entry<AlphabetKey> { key ->
          val lang = Lang.fromCode(key.lang)
          YukiTheme(lang) { AlphabetScreen(lang, onBack = { backStack.removeLastOrNull() }) }
        }
        entry<WordKey> { key ->
          val lang = Lang.fromCode(key.wordId.substringBefore(':'))
          YukiTheme(lang) {
            val vm = viewModel { WordDetailViewModel(key.wordId, graph.study, graph.progress, graph.audio) }
            WordDetailScreen(vm, onBack = { backStack.removeLastOrNull() })
          }
        }
      },
    )
  }
}
