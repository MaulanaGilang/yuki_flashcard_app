<img src="design/logo/yuki-logo-512.png" width="96" alt="Yuki" />

# Yuki

A personal, fully offline Android flashcard app: Mandarin by HSK (1–6), then Spanish, Russian, English, German, French and Arabic by CEFR (up to C1), scheduled with FSRS-6.

- Product requirements: [docs/PRD.md](docs/PRD.md)
- Design system: [docs/DESIGN-SYSTEM.md](docs/DESIGN-SYSTEM.md) · visual preview: [docs/design-preview.html](docs/design-preview.html)
- Architecture: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)

## Layout

| Folder | What |
|---|---|
| `android/` | The app (Kotlin, Jetpack Compose). Modules: `core:model`, `core:scheduler` (FSRS-6 + session engine, pure Kotlin), `core:data` (Room progress DB, pack reader, audio, backup), `core:designsystem`, `feature:today`, `feature:study`, `feature:library`, `app`, `baselineprofile` (Baseline Profile generator and startup benchmark). |
| `pipeline/` | Python content pipeline that builds language packs. `tools/` holds the FSRS test-vector generator, the dev-pack builder and the font subsetter. |
| `packs/` | Built packs (git-ignored). |

## Build and run (Windows, PowerShell)

Needs JDK 21 (Temurin) and the Android SDK (platform 37, build-tools 36+).

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
cd android
.\gradlew.bat :app:assembleDebug        # APK: app\build\outputs\apk\debug\app-debug.apk
.\gradlew.bat :core:scheduler:test      # FSRS port vs py-fsrs reference + session engine
.\gradlew.bat :core:data:connectedDebugAndroidTest   # backup round-trip (needs a device/emulator)
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

**Signed release APK** (the one to put on the phone): `.\gradlew.bat :app:assembleRelease` writes `app\build\outputs\apk\release\app-release.apk`. It's signed with the local keystore `android/keystore/yuki-release.jks`, whose passwords are in `android/keystore.properties`. Both files are git-ignored. **Back them up**: Android only installs updates signed with the same key. Without `keystore.properties` the release build comes out unsigned.

**Baseline Profile and startup benchmark.** `android/baselineprofile` is a Macrobenchmark module (`androidx.baselineprofile` 1.5.0, works with AGP 9.0.1 / Gradle 9.1). Its generator cold-starts the app, taps through onboarding, opens a Mandarin session, flips a card and opens Stats. The profile it records is committed at `app/src/release/generated/baselineProfiles/`, and `assembleRelease` packs it into the APK (`assets/dexopt/baseline.prof`), where `profileinstaller` applies it on install. Regenerate it after big UI changes, with an emulator or the phone connected:

```powershell
.\gradlew.bat :app:generateReleaseBaselineProfile                 # ~25 min on the emulator
.\gradlew.bat :baselineprofile:connectedBenchmarkReleaseAndroidTest  # cold start, 10 runs each
```

Cold start to the Today tab (`timeToInitialDisplayMs`, R8-minified `benchmarkRelease`, 10 runs each, emulator `yuki_api36`, API 36, x86_64, 2 GB RAM, 2026-10-29):

| Compilation | Median | Min | Max |
|---|---|---|---|
| None (no profile, interpreted + JIT) | 2,624 ms | 2,125 ms | 4,459 ms |
| Baseline Profile (`Partial`, required) | 1,994 ms | 1,571 ms | 2,745 ms |

That's 24% faster at the median. The emulator was heavily loaded during the run, so absolute numbers are higher than a quiet run (about 1.3–1.4 s by hand). Compare the two rows, not the absolute values. The OPPO Reno4 will differ again. The benchmark suppresses the `EMULATOR` error; remove that argument in `baselineprofile/build.gradle.kts` before measuring on a phone.

**CI:** `.github/workflows/android.yml` runs the unit tests and builds a debug APK on every push to `main` that touches `android/`. The APK is attached to the run.

## Content

The APK currently bundles small **dev packs** (`android/app/src/main/assets/packs/{zh,es,ru,de,fr,ar}-dev`, about 120 words in total) so the app runs end to end. Rebuild them with:

```powershell
pipeline\.venv\Scripts\python.exe pipeline\tools\build_dev_pack.py
```

The real Mandarin HSK 1–6 pack comes from the pipeline (`packs/zh-hsk-1-6-v1`) and replaces `zh-dev`. Word ids use the same scheme, so progress carries over.

To install a real pack without rebuilding the APK, copy its `.kpack` (from `packs/`) to the phone, then use **Settings › Content packs › Install a pack…**. Yuki checks every file against the manifest's SHA-256 and refuses unsafe zip paths. The pack then replaces the built-in one for that language.
