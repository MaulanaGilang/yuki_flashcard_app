package app.yuki.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates `app/src/release/generated/baselineProfiles/baseline-prof.txt`:
 * `./gradlew :app:generateReleaseBaselineProfile` with the emulator or phone connected.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {
  @get:Rule
  val rule = BaselineProfileRule()

  @Test
  // Fewer iterations than the default 15: the 2 GB emulator stalls on long runs.
  fun generate() = rule.collect(packageName = targetPackage, maxIterations = 6, stableIterations = 2, includeInStartupProfile = true) {
    pressHome()
    grantNotifications()
    startActivityAndWait()
    passOnboardingIfShown()
    studyOneCard()
    openStats()
  }
}
