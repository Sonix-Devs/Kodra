# Kodra Android shell

Single-Activity WebView + foreground service that owns the local VS Code server.
Implements the architecture described in `PORTING_NOTES.md` § Architecture decisions 3-8.

## Architecture

- **UI:** `MainActivity.kt` — full-screen `WebView`, edge-to-edge (`WindowCompat.setDecorFitsSystemWindows(false)`), `WindowInsetsControllerCompat` transient-bars-by-swipe, immersive-mode toggle in Settings (`SettingsActivity`), cutout/`displayCutout` handling, IME resize (`adjustResize`) + `WindowInsetsCompat` listener, long-press → browser context menu, hardware keyboard dispatch via `onKeyDown` → `dispatchKeyEvent`.
- **Server lifecycle:** `KodraService.kt` — `Service.START_STICKY`, owns `ServerProcess` (child `node` + REH), partial `WakeLock`, low-importance foreground `Notification` with **Stop Kodra** action. Reopening the Activity reconnects the WebView to that server; recovery after OS process death restarts the service with a new token.
- **Bootstrap:** `BootstrapInstaller.kt` — extracts `assets/bootstrap.zip` (`$PREFIX`) + `assets/reh-web.tar.gz` (REH) on first launch, `chmod +x` for `node`/`bash`/`rg`/`git`, creates `$HOME`, `~/storage` symlink → SAF bridge, writes `bash.bashrc`/`gitconfig`.
- **Origin & security:** `WebViewClient.shouldOverrideUrlLoading` allowlist `http://127.0.0.1:<port>/*` only; external `https:` → `Intent.ACTION_VIEW`; no `addJavascriptInterface` — narrow `WebMessagePort` bridge (`KodraBridge.kt` + `WorkbenchBridge.kt`) for `openFile`/`openFolder`/`clipboard`/`auth` only, token never logged (redact `?tkn=` from Logcat, see `ServerProcess`).
- **File access:** `FilePickerBridge.kt` + `PermissionHandler.kt` — `ActivityResultContracts.OpenDocument`/`OpenDocumentTree` for Open File/Folder (SAF), `DocumentsContract.isDocumentUri` → `ContentResolver` → real POSIX path only when `isFileSystemBacked`; content-only URIs return `ERR_CONTENT_URI` with explanation (no data loss). Shared projects default `/storage/emulated/0/Kodra` via `MANAGE_EXTERNAL_STORAGE` (targetSdk 28) + `Intent.ACTION_OPEN_DOCUMENT_TREE` fallback.
- **Token:** `TokenManager.kt` — `SecureRandom` 32-byte base64url, per-launch, written to `filesDir/token` (`MODE_PRIVATE`, `600`), passed via `--connection-token-file`; server startup line `Web UI available at ...?tkn=...` redacted before `Log.d`.
- **Input:** `InsetsBridge.kt` (edge-to-edge, cutout, IME), `ExtraKeysView.kt` (sticky Ctrl/Alt, Esc/Tab/arrows), `ClipboardBridge.kt` (plain-text only).
- **Auth:** `GithubAuth.kt` (`AndroidKeyStore` AES/GCM) + `DeviceAuthFlow.kt` (polling `slow_down`/`expired_token`) + `GitBridge.kt` (credential helper).

## Project layout

```
kodra/android/
  gradle/wrapper/gradle-wrapper.properties  # Gradle 8.7, AGP 8.5.2, Kotlin 1.9.24
  settings.gradle.kts
  build.gradle.kts            # project
  app/build.gradle.kts        # applicationId dev.unalivin.kodra, targetSdk 28, compileSdk 34
  app/src/main/AndroidManifest.xml
  app/src/main/java/dev/unalivin/kodra/*.kt
  app/src/main/res/...        # adaptive icon, theme, strings, backup rules
  app/src/main/assets/README.md  # populated by bionicBuild.ts + gulp at build time
  local.properties.example
```

## Build (outside sandbox)

```sh
export ANDROID_SDK_ROOT=$HOME/Android/Sdk
export ANDROID_NDK_ROOT=$ANDROID_SDK_ROOT/ndk/26.1.10909125
cd kodra/android
./gradlew :app:assembleDebug          # → app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease        # requires release.keystore (ignored in git)
./gradlew :app:lintDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -s Kodra:V KodraService:V
# Verify:
# - WebView loads http://127.0.0.1:<port>/?tkn=<token> (no token in logcat)
# - Notification shows, Stop action kills service+node
# - Reopen Activity reconnects without server restart
# - SAF Open File returns real path for filesystem-backed provider
# - Extra keys row sends Esc/Tab/Ctrl combos to terminal
```

Sandbox has **no** Android SDK/NDK, Java, or `adb` — CI `kodra-android.yml` runs `assembleDebug` + `lint` on `ubuntu-latest` with `actions/setup-java` + `android-actions/setup-android`.

## Build integration

1. NDK host builds bionic Node + 5 native addons (`node-pty`, `parcel/watcher`, etc.) → `bootstrap.zip` (see `kodra/TOOLCHAIN.md` + `build/kodra/bionicBuild.ts`).
2. REH build `pnpm run gulp vscode-reh-web-linux-arm64-min` → `reh-web.tar.gz` (filtered for Android).
3. Both archives are copied to `kodra/android/app/src/main/assets/` before `assembleDebug` (see `.github/workflows/kodra-release.yml`).

See `PORTING_NOTES.md` § *Electron-only surface map* for feature-to-bridge mapping and § Acceptance baseline for verified behaviors.
