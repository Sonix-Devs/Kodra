# Kodra Android shell — Stage 3 scaffold

Single-Activity WebView + foreground service that owns the local VS Code server.

## Architecture (see `PORTING_NOTES.md` § Architecture decisions)

- **UI:** `MainActivity.kt` — full-screen `WebView`, edge-to-edge (`WindowCompat.setDecorFitsSystemWindows(false)`), `WindowInsetsControllerCompat` transient-bars-by-swipe, immersive-mode toggle in Settings, cutout/`displayCutout` handling, IME resize (`adjustResize`) + `WindowInsetsCompat` listener, long-press → browser context menu, hardware keyboard dispatch via `onKeyDown` → `dispatchKeyEvent`.
- **Server lifecycle:** `KodraService.kt` — `Service.START_STICKY`, owns `ServerProcess` (child `node` + REH), partial `WakeLock`, low-importance foreground `Notification` with **Stop Kodra** action, `onTaskRemoved` ≠ stop (user must press Stop or swipe-away+OS kill), reconnection via `LocalBroadcast`/`Messenger` port+token handoff.
- **Bootstrap:** `BootstrapInstaller.kt` — extracts `assets/bootstrap.zip` (`$PREFIX`) + `assets/reh-web.tar.gz` (REH) on first launch, `chmod +x` for `node`/`bash`/`rg`/`git`, creates `$HOME`, `~/storage` symlink → SAF bridge, writes `bash.bashrc`/`gitconfig`.
- **Origin & security:** `WebViewManager.kt` — `WebViewClient.shouldOverrideUrlLoading` allowlist `http://127.0.0.1:<port>/*` only; external `https:` → `Intent.ACTION_VIEW`; `addJavascriptInterface` **never** — narrow `WebMessagePort` bridge (`KodraBridge.kt`) for `openFile`/`openFolder`/`clipboard`/`auth` only, token never logged (redact `?tkn=` from Logcat, see `ServerProcess`).
- **File access:** `FilePickerBridge.kt` — `ActivityResultContracts.OpenDocument`/`OpenDocumentTree` for Open File/Folder (SAF), `DocumentsContract.isDocumentUri` → `ContentResolver` → real POSIX path only when `isFileSystemBacked` (Storage Access Framework column `_data` or `File`); content-only URIs return `ERR_CONTENT_URI` with explanation (no data loss). Shared projects default `/storage/emulated/0/Kodra` via `MANAGE_EXTERNAL_STORAGE` (targetSdk 28) + `Intent.ACTION_OPEN_DOCUMENT_TREE` fallback.
- **Token:** `TokenManager.kt` — `SecureRandom` 32-byte base64url, per-launch, written to `filesDir/token` (`MODE_PRIVATE`, `600`), passed via `--connection-token-file`; server startup line `Web UI available at ...?tkn=...` redacted before `Log.d`.

## Project layout

```
kodra/android/
  gradle/wrapper/gradle-wrapper.properties  # Gradle 8.7, AGP 8.5.2, Kotlin 1.9.24
  settings.gradle.kts
  build.gradle.kts            # project
  app/build.gradle.kts        # applicationId dev.unalivin.kodra, targetSdk 28
  app/src/main/AndroidManifest.xml
  app/src/main/java/dev/unalivin/kodra/*.kt
  app/src/main/res/...
  local.properties.example
```

## Build (outside sandbox)

```sh
export ANDROID_SDK_ROOT=$HOME/Android/Sdk
export ANDROID_NDK_ROOT=$ANDROID_SDK_ROOT/ndk/26.1.10909125
cd kodra/android
./gradlew :app:assembleDebug          # → app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease        # requires kodra/android/app/release.keystore (ignored)
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -s Kodra:V KodraService:V
# Verify:
# - WebView loads http://127.0.0.1:<port>/?tkn=<token> (no token in logcat)
# - Notification shows, Stop action kills service+node
# - Reopen Activity reconnects without server restart
# - SAF Open File returns real path for filesystem-backed provider
```

Sandbox has **no** Android SDK/NDK, Java, or `adb` — this is a scaffold; CI `kodra-android.yml` will run `assembleDebug` + `lint` on `ubuntu-latest` with `actions/setup-java` + `android-actions/setup-android`.

## Not in this commit

- No `bootstrap.zip` assets (Stage 2 NDK build + `build/gulpfile.reh.ts` tar)
- No PTY/Git/auth bridges (Stage 4)
- No extension bundling (Stage 5)
- No touch/keyboard exhaustive tests (Stage 6)

See `PORTING_NOTES.md` § *Electron-only surface map* for feature-to-bridge mapping.
