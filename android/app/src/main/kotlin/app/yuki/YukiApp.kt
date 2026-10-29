package app.yuki

import android.app.Application

class YukiApp : Application() {
  val graph: AppGraph by lazy { AppGraph(this) }
}
