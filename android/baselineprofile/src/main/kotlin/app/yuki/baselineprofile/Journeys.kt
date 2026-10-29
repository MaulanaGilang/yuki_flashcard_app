package app.yuki.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

/** Set by the baselineprofile plugin; falls back to the app's own id when run by hand. */
val targetPackage: String = InstrumentationRegistry.getArguments().getString("targetAppId") ?: "app.yuki"

private const val TIMEOUT = 10_000L

/** Pre-grants notifications so the system prompt after onboarding doesn't cover Today. Call before launching. */
fun MacrobenchmarkScope.grantNotifications() {
  device.executeShellCommand("pm grant $packageName android.permission.POST_NOTIFICATIONS")
}

/**
 * Taps through first-run onboarding if it is showing. The defaults pick Mandarin (the first pack),
 * so pressing "Let's go", "Next" ×3 and "Start learning" leaves a Mandarin-only plan.
 */
fun MacrobenchmarkScope.passOnboardingIfShown() {
  // Either the onboarding welcome or the Today tab shows up first.
  device.wait(Until.hasObject(By.text("Let's go").enabled(true)), TIMEOUT)
  device.findObject(By.text("Let's go").enabled(true))?.click() ?: return
  repeat(3) {
    device.wait(Until.findObject(By.text("Next").enabled(true)), TIMEOUT)?.click()
  }
  device.wait(Until.findObject(By.text("Start learning").enabled(true)), TIMEOUT)?.click()
  device.wait(Until.hasObject(By.text("Stats")), TIMEOUT)
}

/** Opens the Mandarin study session from Today, flips the first card, then leaves the session. */
fun MacrobenchmarkScope.studyOneCard() {
  // Today settles in after launch (streak banner, route strip), so the button can move under a tap; retry.
  val card = (1..3).firstNotNullOfOrNull {
    device.wait(Until.hasObject(By.textStartsWith("Start ·").enabled(true)), TIMEOUT)
    device.waitForIdle()
    device.findObject(By.textStartsWith("Start ·").enabled(true))?.click()
    device.wait(Until.findObject(By.descContains("Double-tap to flip")), TIMEOUT / 2)
  } ?: run {
    device.takeScreenshot(java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.externalMediaDirs.first(), "no-card.png"))
    error("Study card did not appear")
  }
  card.click()
  device.wait(Until.hasObject(By.descContains("flip back")), TIMEOUT)
  device.waitForIdle()
  device.pressBack()
  device.wait(Until.hasObject(By.textStartsWith("Start ·")), TIMEOUT)
}

fun MacrobenchmarkScope.openStats() {
  device.findObject(By.text("Stats"))?.click() ?: error("Stats tab missing")
  device.wait(Until.hasObject(By.textContains("reviews on")), TIMEOUT)
  device.waitForIdle()
}
