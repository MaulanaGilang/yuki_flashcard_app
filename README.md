<img src="design/logo/yuki-logo-512.png" width="96" alt="Yuki" />

# Yuki

A personal, fully offline Android flashcard app: Mandarin by HSK (1–6), then Spanish, Russian, English, German, French and Arabic by CEFR (up to C1), scheduled with FSRS-6.

- Product requirements: [docs/PRD.md](docs/PRD.md)
- Design system: [docs/DESIGN-SYSTEM.md](docs/DESIGN-SYSTEM.md) · visual preview: [docs/design-preview.html](docs/design-preview.html)
- Architecture: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)

## Layout

| Folder | What |
|---|---|
| `android/` | The app (Kotlin, Jetpack Compose). Modules: `core:model`, `core:scheduler` (FSRS-6 + session engine, pure Kotlin), `core:data` (Room progress DB, pack reader, audio, backup), `core:designsystem`, `feature:today`, `feature:study`, `feature:library`, `app`. |
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

## Content

The APK currently bundles small **dev packs** (`android/app/src/main/assets/packs/{zh,es,ru,de,fr,ar}-dev`, about 120 words in total) so the app runs end to end. Rebuild them with:

```powershell
pipeline\.venv\Scripts\python.exe pipeline\tools\build_dev_pack.py
```

The real Mandarin HSK 1–6 pack comes from the pipeline (`packs/zh-hsk-1-6-v1`) and replaces `zh-dev`. Word ids use the same scheme, so progress carries over.
