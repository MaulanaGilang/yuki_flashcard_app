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
import kotlinx.serialization.Serializable

@Serializable data object TodayKey : NavKey
@Serializable data object LibraryKey : NavKey
@Serializable data object SettingsKey : NavKey
@Serializable data class StudyKey(val lang: String) : NavKey
@Serializable data class WordKey(val wordId: String) : NavKey
@Serializable data class AlphabetKey(val lang: String) : NavKey

private val topLevel = listOf<Pair<NavKey, String>>(TodayKey to "Today", LibraryKey to "Library", SettingsKey to "Settings")
private val topLevelIcons = mapOf<NavKey, ImageVector>(TodayKey to Icons.Filled.Home, LibraryKey to Icons.AutoMirrored.Filled.List, SettingsKey to Icons.Filled.Settings)

@Composable
fun YukiNavigation(graph: AppGraph, openWord: String? = null, onWordOpened: () -> Unit = {}) {
  val backStack = rememberNavBackStack(TodayKey)
  // Tapping the home-screen widget opens that word on top of Today.
  LaunchedEffect(openWord) {
    if (openWord != null) {
      backStack.add(WordKey(openWord))
      onWordOpened()
    }
  }
  val current = backStack.lastOrNull()
  val showBar = current == TodayKey || current == LibraryKey || current == SettingsKey

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
          TodayScreen(vm, padding, onStart = { lang -> backStack.add(StudyKey(lang.code)) })
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
            StudyScreen(vm, onClose = { backStack.removeLastOrNull() }, onOpenWord = { backStack.add(WordKey(it)) })
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
