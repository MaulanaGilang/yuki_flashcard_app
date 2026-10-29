package app.yuki

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import app.yuki.core.designsystem.theme.YukiTheme
import app.yuki.widget.WordWidget

class MainActivity : ComponentActivity() {
  /** Word to open, set when launched from the home-screen widget. */
  private val pendingWord = mutableStateOf<String?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    pendingWord.value = intent?.getStringExtra(WordWidget.EXTRA_WORD_ID)
    val graph = (application as YukiApp).graph
    setContent {
      LaunchedEffect(Unit) { graph.packs.ensureInstalled() }
      YukiTheme { YukiNavigation(graph, openWord = pendingWord.value, onWordOpened = { pendingWord.value = null }) }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    intent.getStringExtra(WordWidget.EXTRA_WORD_ID)?.let { pendingWord.value = it }
  }

  override fun onStop() {
    super.onStop()
    // Keep the widget in step with today's progress whenever the app goes to the background.
    WordWidget.refreshAll(this)
    app.yuki.widget.StreakWidget.refreshAll(this)
  }
}
