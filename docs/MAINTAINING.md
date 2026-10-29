# Running Yuki yourself (no Claude)

Everything here works with free tools on the Windows laptop. Commands are PowerShell, run from the repo folder `D:\Archive\Karir\Flashcard-App`.

## One-time setup (already done on this laptop)

- **JDK 21 (Temurin)** at `C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot`. The `java` on PATH is an old broken one, so always set `JAVA_HOME` first:
  ```powershell
  $env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
  ```
- **Android SDK** in `%LOCALAPPDATA%\Android\Sdk` (adb is in `platform-tools`).
- **Python venv** for the content pipeline: `pipeline\.venv` (Python 3.12).
- **Cloudflare credentials** for FLUX pictures in `pipeline\.env` (`CF_ACCOUNT_ID`, `CF_API_TOKEN`). Never share or commit this file.

## The things you must never lose

| File | Why | Where to keep a copy |
|---|---|---|
| `android\keystore\yuki-release.jks` and `android\keystore.properties` | Without them you can't update the app on your phone; you'd have to uninstall it and lose progress | Google Drive + a USB stick |
| Your `.yuki-backup` files | Your study progress | The weekly automatic backup folder (Settings) plus an occasional manual save |
| `pipeline\.env` | Picture generation | Anywhere private |

## Build and install the app

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
cd android
.\gradlew.bat test                    # unit tests: should say BUILD SUCCESSFUL
.\gradlew.bat :app:assembleRelease    # signed APK (takes about 10-15 min on this laptop)
```

The APK is `android\app\build\outputs\apk\release\app-release.apk`. To install it, either:
- copy it to the phone and tap it (allow "install unknown apps"), or
- plug the phone in with USB debugging on, then run `adb install -r app\build\outputs\apk\release\app-release.apk`.

`-r` keeps your progress. Only an APK signed with the same key can update the app, which is why the keystore matters.

**If a build fails:** read the first line that starts with `e:` (the Kotlin error) or `What went wrong`. Most failures after an Android Studio or SDK update are fixed by `.\gradlew.bat --stop` and building again.

## Content packs

Each language is one pack, built by the pipeline from free sources (`pipeline\config\sources.md`).

**Build or refresh a pack.** The stages are listed in `pipeline\README.md` ("Running it yourself"). In short:
```powershell
$py = "pipeline\.venv\Scripts\python.exe"
& $py -X utf8 -m yuki_pipeline fetch --lang es
& $py -X utf8 -m yuki_pipeline normalize --lang es
& $py -X utf8 -m yuki_pipeline enrich --lang es
& $py -X utf8 -m yuki_pipeline media --lang es
& $py -X utf8 -m yuki_pipeline images --lang es
& $py -X utf8 -m yuki_pipeline evaluate --lang es
& $py -X utf8 -m yuki_pipeline package --lang es
```
*(The exact flags are whatever `pipeline\README.md` says once the multi-language engine lands; that file wins.)*

The pack lands in `packs\<id>\`, with a `.kpack` file next to it.

**Install a pack on the phone.** Copy the `.kpack` to the phone (USB, Google Drive or Telegram to yourself), then in Yuki go to **Settings › Content packs › Install a pack…** and pick the file. Your progress for that language is kept.

**Check a pack before installing it.** Open `pipeline\work\<pack>\report.html` in a browser. It's a spot-check sheet with sample words, audio and pictures.

## Pictures: the daily job

Cloudflare's free tier makes about 170 pictures a day. `pipeline\tools\daily.ps1` makes today's batch, then re-packages. To run it every morning automatically, register it once with Windows Task Scheduler (the exact command is in `pipeline\README.md`), roughly:
```powershell
schtasks /Create /SC DAILY /ST 07:30 /TN "Yuki daily images" /TR "powershell -NoProfile -ExecutionPolicy Bypass -File D:\Archive\Karir\Flashcard-App\pipeline\tools\daily.ps1"
```
It needs the laptop on and online at that time; if it misses a day, nothing breaks. Every week or two, install the refreshed `.kpack`s on the phone to get the new pictures.

## Backups and restoring

- **Automatic:** Settings › Backup & restore › "Back up every week" saves to the folder you picked and keeps the last 4.
- **Manual:** Settings › Save backup.
- **Restore on a new phone:** install the APK and the packs, then Settings › Restore… and pick a `.yuki-backup`. It shows what's inside before replacing anything.

## Phone quirks (OPPO Reno4 / ColorOS)

If reminders stop or the widget freezes, go to Settings › Battery › Yuki and allow background activity (and auto-launch if it's offered). After a ColorOS update, check this again.

## Where things are

| Folder | What |
|---|---|
| `android\` | the app (Kotlin, Jetpack Compose). Modules: `core\model` (data types, reading guides), `core\scheduler` (FSRS-6), `core\data` (database, packs, backup), `core\designsystem` (look and feel), `feature\*` (screens), `app` (navigation, settings, widgets, reminders) |
| `pipeline\` | content builder (Python): `config\<lang>.yaml` recipes, `yuki_pipeline\` stages, `tools\` helpers |
| `packs\` | built packs (git-ignored; big) |
| `docs\` | PRD, design system, architecture, this guide, the 30-day plan |
| `design\` | logo and mascot source images |

## Making small changes without Claude

- **Fix a wrong meaning or sentence:** add an override in `pipeline\config\<lang>_overrides.yaml` (see the zh file for the format), rebuild that pack, install it.
- **Change daily limits or the reminder time:** in the app's Settings; no code needed.
- **Text or colour changes in the app:** strings are inline in the screen files under `android\feature\` and `android\app\`. Colours are only in `android\core\designsystem\...\theme\Tokens.kt`. Rebuild the release APK and install it with `adb install -r`.
